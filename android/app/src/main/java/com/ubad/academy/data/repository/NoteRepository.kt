package com.ubad.academy.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.ubad.academy.core.Web
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.attachmentEntities
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.domain.model.Note
import com.ubad.academy.domain.model.NoteAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val db: UbadDatabase,
    private val files: FileStore,
    private val resolver: ContentResolver,
) {
    private val dao get() = db.notes()

    /** Pinned first, then most recently updated (web sort). */
    val notes: Flow<List<Note>> = combine(dao.observeNotes(), dao.observeAttachments()) { n, a ->
        val byNote = a.groupBy { it.noteId }
        n.map { it.toDomain(byNote[it.id].orEmpty()) }
    }

    suspend fun note(id: String): Note? = dao.note(id)?.toDomain(dao.attachmentsOf(id))

    fun file(a: NoteAttachment): File = files.noteFile(a.fileId)

    sealed interface AttachResult {
        data class Ok(val attachment: NoteAttachment) : AttachResult
        data object TooBig : AttachResult
        data object BadType : AttachResult
        data object Failed : AttachResult
    }

    /**
     * Copies a picked file into private storage (web `addFiles`): max 5 MB, image/* or audio/*.
     * The file is "pending" until [save]; call [discardPending] if the editor is discarded.
     */
    suspend fun importAttachment(uri: Uri, kind: Kind): AttachResult = withContext(Dispatchers.IO) {
        val mime = resolver.getType(uri).orEmpty()
        if (!mime.startsWith(kind.mimePrefix)) return@withContext AttachResult.BadType
        val (name, size) = queryNameSize(uri)
        if (size != null && size > MAX_ATTACHMENT) return@withContext AttachResult.TooBig
        val fileId = Web.uid()
        val target = files.noteFile(fileId)
        val written = runCatching {
            resolver.openInputStream(uri)?.use { files.writeAtomically(target, LimitedInputStream(it, MAX_ATTACHMENT + 1)) }
        }.getOrNull() ?: return@withContext AttachResult.Failed
        if (written > MAX_ATTACHMENT) { target.delete(); return@withContext AttachResult.TooBig }
        AttachResult.Ok(NoteAttachment(fileId, (name ?: kind.defaultName).take(80), mime))
    }

    private fun queryNameSize(uri: Uri): Pair<String?, Long?> = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null to null
            val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
            val s = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
            n to s
        }
    }.getOrNull() ?: (null to null)

    /** `saveNote(rec)` — also removes files of attachments the user deleted. */
    suspend fun save(note: Note) = withContext(Dispatchers.IO) {
        val old = dao.attachmentsOf(note.id).map { it.fileId }.toSet()
        val clean = note.copy(
            title = note.title.trim().take(120),
            body = note.body.take(20_000),
            tags = note.tags.map { it.trim().take(24) }.filter { it.isNotEmpty() }.take(8),
            updatedAt = System.currentTimeMillis(),
        )
        dao.replaceNote(clean.toEntity(), clean.attachmentEntities())
        val kept = (clean.images + clean.audio).map { it.fileId }.toSet()
        (old - kept).forEach { files.noteFile(it).delete() }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val att = dao.attachmentsOf(id)
        dao.deleteNote(id)
        att.forEach { files.noteFile(it.fileId).delete() }
    }

    /** Deletes files that were imported in the editor but never saved. */
    suspend fun discardPending(fileIds: Collection<String>) = withContext(Dispatchers.IO) {
        val referenced = dao.attachments().map { it.fileId }.toSet()
        fileIds.filter { it !in referenced }.forEach { files.noteFile(it).delete() }
    }

    enum class Kind(val mimePrefix: String, val defaultName: String) { IMAGE("image/", "image"), AUDIO("audio/", "audio") }

    companion object {
        const val MAX_ATTACHMENT = 5L * 1024 * 1024

        /** Web: `value.split(',').map(trim).filter(Boolean).slice(0,8)` */
        fun parseTags(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(8)
    }
}

/** Stops reading after [limit] bytes so an oversized/unknown-size stream can't fill the disk. */
class LimitedInputStream(private val input: java.io.InputStream, private var remaining: Long) : java.io.InputStream() {
    override fun read(): Int = if (remaining <= 0) -1 else input.read().also { if (it >= 0) remaining-- }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }
    override fun close() = input.close()
}
