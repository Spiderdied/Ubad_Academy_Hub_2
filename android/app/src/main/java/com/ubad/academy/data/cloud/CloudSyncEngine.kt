package com.ubad.academy.data.cloud

import com.ubad.academy.core.Web
import com.ubad.academy.core.cloud.CloudConfig
import com.ubad.academy.core.cloud.CloudPaths
import com.ubad.academy.data.backup.BackupCodec
import com.ubad.academy.data.local.db.CourseAssetEntity
import com.ubad.academy.data.local.db.NoteAttachmentEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.CloudPrefsStore
import com.ubad.academy.di.AppScope
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the Settings UI shows for cloud sync. */
data class CloudSyncStatus(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val fileCount: Int = 0,
    val lastSyncAt: Long = 0L,
    val error: String? = null,
    /** Both sides have data and the user has not answered the first-sync prompt yet. */
    val needsFirstChoice: Boolean = false,
    /** Cloud state awaiting an answer to that prompt. */
    val pendingRemote: CloudState? = null,
)

/**
 * Cloud sync, ported from the web's `cloudSync` block in `app.js`.
 *
 * Rules carried over unchanged, because they are what keeps user data safe:
 *
 *  - **Local is never destroyed by a failed cloud operation.** An upload that
 *    fails aborts the push before anything is written to Firestore; a download
 *    that fails leaves the local file untouched. Nothing here deletes local data.
 *  - **Last-write-wins by `clientUpdatedAt`**, and pushes are only applied to the
 *    local side when the remote is strictly newer.
 *  - **The first sync with data on both sides asks** (Upload / Restore / Merge)
 *    and remembers the answer per account; nothing is overwritten silently.
 *  - **The full 900 KB structured payload plus a file manifest** is written in
 *    one `setCloudState` call, matching the web document shape.
 *
 * Two deliberate, documented deviations (both strictly less destructive):
 *
 *  1. The web re-applies the remembered choice on *every* launch, which for
 *     "restore" re-downloads every file each time. Android skips that when
 *     `clientUpdatedAt` matches the value it last applied, so a normal launch
 *     does no work while an actual remote change still syncs.
 *  2. A restore does not delete local course-asset files that the cloud has no
 *     record of. The web's restore clears them first; leaving them is safer and
 *     the next push re-registers them.
 */
@Singleton
class CloudSyncEngine @Inject constructor(
    private val auth: CloudAuthRepository,
    private val codec: CloudPayloadCodec,
    private val firestore: FirestoreCloudClient,
    private val cloudFiles: CloudFileApi,
    private val fileStore: FileStore,
    private val db: UbadDatabase,
    private val prefs: CloudPrefsStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(CloudSyncStatus())
    val status: StateFlow<CloudSyncStatus> = _status.asStateFlow()

    private val pushLock = Mutex()
    private var watchJob: Job? = null
    private var debounceJob: Job? = null

    /** Web `cloudSync.fileSig` — avoids re-uploading an unchanged file. */
    private val fileSig = mutableMapOf<String, String>()
    private var remoteFiles: List<CloudFileEntry> = emptyList()
    private var applying = false

    // ───────────────────────── lifecycle ─────────────────────────

    /** Web `startCloudSync(user)` — called on sign-in and on app start while signed in. */
    suspend fun onSignedIn(user: CloudUser) {
        _status.value = CloudSyncStatus(signedIn = true, busy = true)
        runCatching {
            firestore.saveProfile(
                CloudProfile(user.uid, user.displayName, user.email, user.photoUrl),
            )
        }
        val remote = try {
            firestore.getCloudState(user.uid)
        } catch (e: Throwable) {
            _status.value = _status.value.copy(busy = false, error = friendly(e))
            startWatch(user.uid)
            return
        }
        remoteFiles = remote?.files.orEmpty()
        prefs.setRemoteFilesJson(encodeEntries(remoteFiles))

        val appData = remote?.appData
        if (appData == null || appData.isEmpty()) {
            // Nothing in the cloud yet: publish what is on this device.
            pushNow(user.uid, manual = false)
        } else {
            val remoteAt = remote.clientUpdatedAt
            val changed = remoteAt != prefs.appliedAt()
            when (FirstSyncChoice.from(prefs.choice(user.uid))) {
                null -> {
                    if (!codec.hasMeaningfulLocalData()) {
                        applyRemote(user.uid, remote)
                        prefs.setChoice(user.uid, FirstSyncChoice.RESTORE.wire)
                    } else {
                        _status.value = _status.value.copy(busy = false, needsFirstChoice = true, pendingRemote = remote)
                        startWatch(user.uid)
                        return
                    }
                }
                FirstSyncChoice.UPLOAD -> if (changed) pushNow(user.uid, manual = false)
                FirstSyncChoice.RESTORE -> if (changed) applyRemote(user.uid, remote)
                FirstSyncChoice.MERGE -> if (changed) mergeWithRemote(user.uid, remote)
                FirstSyncChoice.CANCEL -> Unit
            }
        }
        _status.value = _status.value.copy(busy = false, fileCount = remoteFiles.size, lastSyncAt = prefs.appliedAt())
        startWatch(user.uid)
    }

    /** Web `stopCloudSync()`. */
    fun stop() {
        watchJob?.cancel(); watchJob = null
        debounceJob?.cancel(); debounceJob = null
        remoteFiles = emptyList()
        fileSig.clear()
        _status.value = CloudSyncStatus()
    }

    /** Live remote updates, like the web's `watchCloudState` subscription. */
    private fun startWatch(uid: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            runCatching {
                firestore.watchCloudState(uid).collect { remote ->
                    if (remote == null || applying) return@collect
                    val remoteAt = remote.clientUpdatedAt
                    if (remote.appData == null || remoteAt <= prefs.localUpdatedAt()) return@collect
                    applying = true
                    try {
                        applyRemote(uid, remote)
                    } finally {
                        applying = false
                    }
                }
            }
        }
    }

    /**
     * Web `scheduleCloudSync(delay)` — debounced push after a local change.
     * Called from the app's save paths; it never blocks the caller.
     */
    fun requestSync(delayMs: Long = 1_200) {
        val user = auth.user.value ?: return
        if (applying) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            pushNow(user.uid, manual = false)
        }
    }

    // ───────────────────────── user actions ─────────────────────────

    /** Settings → "Sync now" / first-sync answer "Upload". */
    suspend fun uploadDeviceToCloud(): Result<Unit> = runCatching {
        val user = requireUser()
        prefs.setLocalUpdatedAt(System.currentTimeMillis())
        pushNow(user.uid, manual = true)
        prefs.setChoice(user.uid, FirstSyncChoice.UPLOAD.wire)
        _status.value = _status.value.copy(needsFirstChoice = false, pendingRemote = null)
    }

    /** Settings → "Download from cloud" / first-sync answer "Restore". */
    suspend fun downloadCloudToDevice(): Result<Unit> = runCatching {
        val user = requireUser()
        val remote = firestore.getCloudState(user.uid)
            ?: throw CloudException("There is nothing in the cloud yet.", "cloud/no-remote-state")
        if (remote.appData == null) throw CloudException("There is nothing in the cloud yet.", "cloud/no-remote-state")
        applyRemote(user.uid, remote)
        prefs.setChoice(user.uid, FirstSyncChoice.RESTORE.wire)
        _status.value = _status.value.copy(needsFirstChoice = false, pendingRemote = null)
    }

    /** First-sync answer "Merge". */
    suspend fun mergeWithCloud(): Result<Unit> = runCatching {
        val user = requireUser()
        val remote = firestore.getCloudState(user.uid)
            ?: throw CloudException("There is nothing in the cloud yet.", "cloud/no-remote-state")
        mergeWithRemote(user.uid, remote)
        prefs.setChoice(user.uid, FirstSyncChoice.MERGE.wire)
        _status.value = _status.value.copy(needsFirstChoice = false, pendingRemote = null)
    }

    fun cancelFirstChoice() {
        val uid = auth.user.value?.uid ?: return
        scope.launch { prefs.setChoice(uid, FirstSyncChoice.CANCEL.wire) }
        _status.value = _status.value.copy(needsFirstChoice = false, pendingRemote = null)
    }

    /** Web `wipeAll` clears cloud state too, when signed in. */
    suspend fun clearCloud() {
        val user = auth.user.value ?: return
        runCatching {
            val remote = firestore.getCloudState(user.uid)
            deleteRemoteFiles(user.uid, remote?.files.orEmpty())
            firestore.deleteCloudState(user.uid)
        }
        prefs.setAppliedAt(0L)
        remoteFiles = emptyList()
        fileSig.clear()
    }

    // ───────────────────────── push / pull ─────────────────────────

    private suspend fun pushNow(uid: String, manual: Boolean) {
        if (applying) return
        pushLock.withLock {
            _status.value = _status.value.copy(busy = true, error = null, signedIn = true)
            try {
                val payload = codec.build()
                val bytes = payload.toString().toByteArray(Charsets.UTF_8).size
                if (bytes > CloudConfig.MAX_CLOUD_STATE_BYTES) {
                    throw CloudException(
                        "Cloud data is too large for one Firestore document (${bytes / 1024} KB).",
                        "cloud/state-too-large",
                    )
                }
                val manifest = buildManifest(uid)
                val uploaded = uploadFiles(uid, manifest)

                // Delete cloud objects the local store no longer has (web parity).
                val currentKeys = uploaded.map { it.entry.key }.toSet()
                val stale = remoteFiles.filter { it.key.isNotEmpty() && it.key !in currentKeys }
                if (stale.isNotEmpty()) deleteRemoteFiles(uid, stale)

                val now = System.currentTimeMillis()
                val syncId = now.toString(36) + "-" + Web.uid().take(6)
                prefs.setLocalUpdatedAt(now)
                firestore.setCloudState(uid, payload, uploaded.map { it.entry }, syncId, now)

                remoteFiles = uploaded.map { it.entry }
                prefs.setSyncId(syncId)
                prefs.setRemoteFilesJson(encodeEntries(remoteFiles))
                prefs.setAppliedAt(now)
                _status.value = _status.value.copy(busy = false, fileCount = remoteFiles.size, lastSyncAt = now, error = null)
            } catch (e: Throwable) {
                // Local data is untouched on failure — by design.
                _status.value = _status.value.copy(busy = false, error = friendly(e))
                if (manual) throw e
            }
        }
    }

    private suspend fun applyRemote(uid: String, remote: CloudState) {
        _status.value = _status.value.copy(busy = true, error = null)
        applying = true
        try {
            remote.appData?.let { codec.apply(it) }
            downloadFiles(uid, remote.files)
            prefs.setAppliedAt(remote.clientUpdatedAt)
            prefs.setLocalUpdatedAt(remote.clientUpdatedAt)
            prefs.setRemoteFilesJson(encodeEntries(remote.files))
            remoteFiles = remote.files
            fileSig.clear()
            _status.value = _status.value.copy(busy = false, fileCount = remote.files.size, lastSyncAt = System.currentTimeMillis())
        } catch (e: Throwable) {
            _status.value = _status.value.copy(busy = false, error = friendly(e))
        } finally {
            applying = false
        }
    }

    /** Web `mergeCloudAndLocal` + the following upload of the merged result. */
    private suspend fun mergeWithRemote(uid: String, remote: CloudState) {
        _status.value = _status.value.copy(busy = true, error = null)
        applying = true
        val merged = mergePayloads(codec.build(), remote.appData ?: JsonObject(emptyMap()))
        try {
            codec.apply(merged)
            downloadFiles(uid, remote.files)
        } finally {
            applying = false
        }
        val manifest = buildManifest(uid)
        val uploaded = uploadFiles(uid, manifest)
        val now = System.currentTimeMillis()
        firestore.setCloudState(uid, merged, uploaded.map { it.entry }, "merge-" + Web.uid(), now)
        prefs.setAppliedAt(now)
        prefs.setLocalUpdatedAt(now)
        remoteFiles = uploaded.map { it.entry }
        prefs.setRemoteFilesJson(encodeEntries(remoteFiles))
        fileSig.clear()
        _status.value = _status.value.copy(busy = false, fileCount = remoteFiles.size, lastSyncAt = now)
    }

    // ───────────────────────── files ─────────────────────────

    private data class ManifestEntry(val entry: CloudFileEntry, val file: File)

    /** Web `buildCloudFileManifest()`. */
    private suspend fun buildManifest(uid: String): List<ManifestEntry> = withContext(Dispatchers.IO) {
        val out = mutableListOf<ManifestEntry>()

        for (a in db.notes().attachments()) {
            val f = fileStore.noteFile(a.fileId)
            if (!f.exists() || f.length() == 0L) continue
            val isImage = a.kind == IMAGE
            val index = if (isImage) a.position else a.position - AUDIO_OFFSET
            if (index < 0) continue
            val key = if (isImage) {
                CloudPaths.noteImage(a.noteId, index, a.name)
            } else {
                CloudPaths.noteAudio(a.noteId, index, a.name)
            }
            out += ManifestEntry(
                CloudFileEntry(
                    kind = if (isImage) CloudPaths.KIND_NOTE_IMAGE else CloudPaths.KIND_NOTE_AUDIO,
                    key = key, noteId = a.noteId, index = index, name = a.name,
                    mimeType = a.mime, size = f.length(),
                ),
                f,
            )
        }

        // Every stored asset, not only referenced ones: the cloud is a mirror of
        // the local file store (web parity), so nothing silently disappears.
        for (asset in db.courses().assets()) {
            val f = fileStore.courseFile(asset.id)
            if (!f.exists() || f.length() == 0L) continue
            out += ManifestEntry(
                CloudFileEntry(
                    kind = CloudPaths.KIND_COURSE_ASSET,
                    key = CloudPaths.courseAsset(asset.id),
                    assetId = asset.id, name = "", mimeType = asset.mime, size = f.length(),
                ),
                f,
            )
        }

        for (theme in ThemeId.entries) {
            val f = fileStore.backgroundFile(theme)
            if (!f.exists() || f.length() == 0L) continue
            out += ManifestEntry(
                CloudFileEntry(
                    kind = CloudPaths.KIND_BACKGROUND,
                    key = CloudPaths.background(theme.key),
                    theme = theme.key, name = theme.key,
                    mimeType = BackupCodec.sniffImageMime(f), size = f.length(),
                ),
                f,
            )
        }
        out
    }

    /**
     * Uploads changed files only. A failure aborts the whole push *before*
     * Firestore is written, and never touches local files.
     */
    private suspend fun uploadFiles(uid: String, manifest: List<ManifestEntry>): List<ManifestEntry> {
        val token = auth.idToken() ?: throw CloudException("Sign in again to sync.", "auth/no-token")
        val done = mutableListOf<ManifestEntry>()
        val failed = mutableListOf<String>()
        for (m in manifest) {
            val sig = "${m.file.length()}:${m.entry.mimeType}:${m.file.lastModified()}"
            if (fileSig[m.entry.key] == sig) {
                done += m
                continue
            }
            try {
                cloudFiles.upload(uid, m.entry.key, m.file, m.entry.mimeType, token)
                fileSig[m.entry.key] = sig
                done += m
            } catch (t: Throwable) {
                failed += m.entry.key
            }
        }
        if (failed.isNotEmpty()) {
            throw CloudException(
                "Could not upload ${failed.size} file(s) to cloud storage. Nothing was removed locally.",
                "cloud/file-upload-failed",
            )
        }
        return done
    }

    private suspend fun downloadFiles(uid: String, entries: List<CloudFileEntry>) {
        if (entries.isEmpty()) return
        val token = auth.idToken() ?: return
        for (e in entries) {
            try {
                when (e.kind) {
                    CloudPaths.KIND_NOTE_IMAGE, CloudPaths.KIND_NOTE_AUDIO -> downloadNoteAttachment(uid, e, token)
                    CloudPaths.KIND_COURSE_ASSET -> {
                        val id = e.assetId ?: continue
                        val target = fileStore.courseFile(id)
                        val size = cloudFiles.download(uid, e.key, target, token)
                        db.courses().upsertAsset(CourseAssetEntity(id, e.mimeType, size))
                    }
                    CloudPaths.KIND_BACKGROUND -> {
                        val theme = ThemeId.from(e.theme ?: e.name)
                        cloudFiles.download(uid, e.key, fileStore.backgroundFile(theme), token)
                    }
                }
            } catch (t: Throwable) {
                // Leave the local copy alone; the next sync retries.
                continue
            }
        }
    }

    /**
     * Restores one note attachment. Android attachment positions encode audio as
     * `1000 + index` (see `Note.attachmentEntities()`), so the cloud index is
     * mapped back to a position rather than reused directly.
     */
    private suspend fun downloadNoteAttachment(uid: String, e: CloudFileEntry, token: String) {
        val noteId = e.noteId ?: return
        val index = e.index ?: return
        val kind = if (e.kind == CloudPaths.KIND_NOTE_IMAGE) IMAGE else AUDIO
        val note = db.notes().note(noteId) ?: return
        val existing = db.notes().attachmentsOf(noteId).filter { it.kind == kind }.sortedBy { it.position }
        val current = existing.getOrNull(index)
        val fileId = current?.fileId ?: Web.uid()
        cloudFiles.download(uid, e.key, fileStore.noteFile(fileId), token)
        if (current == null) {
            val position = if (kind == IMAGE) index else AUDIO_OFFSET + index
            db.notes().replaceNote(
                note,
                db.notes().attachmentsOf(noteId) +
                    NoteAttachmentEntity(fileId, noteId, kind, e.name, e.mimeType, position),
            )
        }
    }

    private suspend fun deleteRemoteFiles(uid: String, entries: List<CloudFileEntry>) {
        val token = auth.idToken() ?: return
        for (e in entries) {
            if (e.key.isEmpty()) continue
            runCatching { cloudFiles.delete(uid, e.key, token) }
        }
    }

    // ───────────────────────── merge ─────────────────────────

    /**
     * Web `mergeCloudAndLocal`: per-section union by id, newest
     * `updatedAt ?: createdAt` wins, and local user/settings take precedence.
     */
    private fun mergePayloads(local: JsonObject, remote: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>(remote)
        out["user"] = mergeObjects(remote["user"], local["user"])
        out["settings"] = mergeObjects(remote["settings"], local["settings"])
        out["focus"] = mergeObjects(remote["focus"], local["focus"])
        for (key in MERGE_KEYS) {
            out[key] = mergeRecords(local[key], remote[key])
        }
        return JsonObject(out)
    }

    /** `{...remote, ...local}` for the small scalar sections. */
    private fun mergeObjects(remote: JsonElement?, local: JsonElement?): JsonElement {
        val r = remote as? JsonObject
        val l = local as? JsonObject
        return when {
            r == null && l == null -> JsonObject(emptyMap())
            r == null -> l!!
            l == null -> r
            else -> JsonObject(LinkedHashMap<String, JsonElement>(r).apply { putAll(l) })
        }
    }

    private fun mergeRecords(local: JsonElement?, remote: JsonElement?): JsonArray {
        val byId = LinkedHashMap<String, JsonElement>()
        fun idOf(e: JsonElement): String? = runCatching { e.jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
        fun stamp(e: JsonElement): Long {
            val o = runCatching { e.jsonObject }.getOrNull() ?: return 0L
            return (o["updatedAt"] ?: o["createdAt"])?.jsonPrimitive?.longOrNull ?: 0L
        }
        (remote as? JsonArray)?.forEach { e -> idOf(e)?.let { byId[it] = e } }
        (local as? JsonArray)?.forEach { e ->
            val id = idOf(e) ?: return@forEach
            val existing = byId[id]
            if (existing == null || stamp(e) >= stamp(existing)) byId[id] = e
        }
        return JsonArray(byId.values.toList())
    }

    // ───────────────────────── helpers ─────────────────────────

    private fun requireUser(): CloudUser =
        auth.user.value ?: throw CloudException("Sign in with Google first.", "auth/not-signed-in")

    private fun encodeEntries(list: List<CloudFileEntry>): String =
        BackupCodec.ExportJson.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CloudFileEntry.serializer()),
            list,
        )

    private fun friendly(e: Throwable): String = when (e) {
        is CloudException -> e.message ?: "Cloud sync failed."
        else -> e.message?.take(200) ?: "Cloud sync failed."
    }

    private companion object {
        const val IMAGE = "image"
        const val AUDIO = "audio"
        /** `Note.attachmentEntities()` stores audio positions as `1000 + index`. */
        const val AUDIO_OFFSET = 1000
        val MERGE_KEYS = listOf(
            "courses", "events", "tasks", "decks", "quizzes", "schedule",
            "forms", "summaries", "notes",
        )
    }
}
