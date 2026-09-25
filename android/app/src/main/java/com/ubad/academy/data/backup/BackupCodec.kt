package com.ubad.academy.data.backup

import androidx.room.withTransaction
import com.ubad.academy.core.Web
import com.ubad.academy.data.backup.WebNormalizer.get
import com.ubad.academy.data.local.db.CourseAssetEntity
import com.ubad.academy.data.local.db.IslamEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.assembleCourses
import com.ubad.academy.data.local.db.attachmentEntities
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.Note
import com.ubad.academy.domain.model.NoteAttachment
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.domain.model.Task
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the web app's backup format v2 **exactly**
 * (see docs/ANDROID_MIGRATION_PLAN.md §Backup), streaming binary data so
 * large PDFs/videos never have to fit in memory.
 *
 * Additive, web-compatible extensions written by Android (ignored by the web restore):
 *  - `data.tasks` (with the calendar section), `data.focus` (with study),
 *  - `data.settings` {lang, sound, theme} (with user).
 */
@Singleton
class BackupCodec @Inject constructor(
    private val db: UbadDatabase,
    private val files: FileStore,
    private val settings: SettingsStore,
) {
    // ───────────────────────────── import ─────────────────────────────

    /** Parses and stages a backup. Throws [BackupException]. Nothing is modified yet. */
    suspend fun parse(input: InputStream): ParsedBackup = withContext(Dispatchers.IO) {
        val staged = mutableListOf<java.io.File>()
        try {
            parseInternal(input, staged).also {
                if (it.available.isEmpty()) { it.discard(); throw BackupException(BackupError.NothingToRestore) }
            }
        } catch (e: BackupException) {
            staged.forEach { it.delete() }; throw e
        } catch (e: IOException) {
            staged.forEach { it.delete() }; throw BackupException(BackupError.Invalid)
        } catch (e: kotlinx.coroutines.CancellationException) {
            staged.forEach { it.delete() }; throw e
        } catch (e: RuntimeException) {
            staged.forEach { it.delete() }; throw BackupException(BackupError.Invalid)
        }
    }

    private fun parseInternal(input: InputStream, staged: MutableList<java.io.File>): ParsedBackup {
        val r = StreamJsonReader(InputStreamReader(input, Charsets.UTF_8))
        if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) throw BackupException(BackupError.Invalid)
        var app: String? = null
        var hasSections = false
        var dataOk = false
        val data = LinkedHashMap<String, JsonElement>()
        val present = LinkedHashSet<String>()
        var assets: List<StagedAsset> = emptyList()
        var notes: List<StagedNote>? = null
        var backgrounds: Map<String, StagedFile>? = null

        fun stage(): StagedFile? {
            val tmp = files.newTempFile("bk").also { staged += it }
            val out = BufferedOutputStream(tmp.outputStream(), FileStore.BUFFER)
            val sink = DataUrlSink(out)
            out.use {
                r.readStringChunks(sink::accept)
                sink.finish()
            }
            return if (sink.valid) StagedFile(tmp, sink.mime, sink.bytes) else { tmp.delete(); null }
        }

        /** Reads an object, streaming the `data` key; returns (other fields, staged data). */
        fun readBinaryHolder(): Pair<Map<String, JsonElement>, StagedFile?> {
            val fields = LinkedHashMap<String, JsonElement>()
            var file: StagedFile? = null
            r.beginObject()
            while (r.hasNext()) {
                val n = r.nextName()
                if (n == "data" && r.peek() == StreamJsonReader.Kind.STRING) { file?.file?.delete(); file = stage() }
                else fields[n] = r.readElement()
            }
            r.endObject()
            return fields to file
        }

        fun readAttachments(): List<StagedAttachment> {
            if (r.peek() != StreamJsonReader.Kind.BEGIN_ARRAY) { r.skipValue(); return emptyList() }
            val list = mutableListOf<StagedAttachment>()
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) { r.skipValue(); continue }
                val (f, file) = readBinaryHolder()
                if (file != null) list += StagedAttachment(WebNormalizer.str(f["name"], 80, "file"), file)
            }
            r.endArray()
            return list
        }

        fun readData() {
            r.beginObject()
            while (r.hasNext()) {
                when (val key = r.nextName()) {
                    "courseAssets" -> {
                        if (r.peek() != StreamJsonReader.Kind.BEGIN_ARRAY) { data[key] = r.readElement(); continue }
                        val list = mutableListOf<StagedAsset>()
                        r.beginArray()
                        while (r.hasNext()) {
                            if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) { r.skipValue(); continue }
                            val (f, file) = readBinaryHolder()
                            val id = WebNormalizer.str(f["id"], 80)
                            if (file != null && WebNormalizer.truthy(f["id"]) && id.isNotEmpty()) list += StagedAsset(id, file)
                            else file?.file?.delete()
                        }
                        r.endArray()
                        assets = list
                        present += key
                    }
                    "notes" -> {
                        if (r.peek() != StreamJsonReader.Kind.BEGIN_ARRAY) {
                            val el = r.readElement(); data[key] = el
                            notes = if (WebNormalizer.truthy(el)) emptyList() else null
                            if (notes != null) present += key
                            continue
                        }
                        val list = mutableListOf<StagedNote>()
                        r.beginArray()
                        while (r.hasNext()) {
                            if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) { r.skipValue(); list += StagedNote(emptyMap(), emptyList(), emptyList()); continue }
                            val fields = LinkedHashMap<String, JsonElement>()
                            var images = emptyList<StagedAttachment>()
                            var audio = emptyList<StagedAttachment>()
                            r.beginObject()
                            while (r.hasNext()) {
                                when (val n = r.nextName()) {
                                    "images" -> images = readAttachments()
                                    "audio" -> audio = readAttachments()
                                    else -> fields[n] = r.readElement()
                                }
                            }
                            r.endObject()
                            list += StagedNote(fields, images, audio)
                        }
                        r.endArray()
                        notes = list
                        present += key
                    }
                    "backgrounds" -> {
                        if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) {
                            val el = r.readElement(); data[key] = el
                            if (WebNormalizer.truthy(el)) { present += key; backgrounds = emptyMap() }
                            continue
                        }
                        val map = LinkedHashMap<String, StagedFile>()
                        r.beginObject()
                        while (r.hasNext()) {
                            val theme = r.nextName()
                            if (r.peek() != StreamJsonReader.Kind.BEGIN_OBJECT) { r.skipValue(); continue }
                            val (_, file) = readBinaryHolder()
                            if (file != null) map.put(theme, file)?.file?.delete()
                        }
                        r.endObject()
                        backgrounds = map
                        present += key
                    }
                    else -> {
                        val el = r.readElement()
                        data[key] = el
                        if (WebNormalizer.truthy(el)) present += key else present -= key
                    }
                }
            }
            r.endObject()
        }

        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "app" -> app = if (r.peek() == StreamJsonReader.Kind.STRING) r.nextString(64) else { r.skipValue(); null }
                "sections" -> hasSections = WebNormalizer.truthy(r.readElement())
                "data" -> when (r.peek()) {
                    StreamJsonReader.Kind.BEGIN_OBJECT -> { dataOk = true; readData() }
                    StreamJsonReader.Kind.BEGIN_ARRAY -> { dataOk = true; r.skipValue() }
                    else -> r.skipValue()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        if (app != APP_ID || !dataOk) throw BackupException(BackupError.Invalid)
        return ParsedBackup(data, hasSections, assets, notes, backgrounds, present)
    }

    /** `restoreBackup(parsed, ids)` */
    suspend fun restore(parsed: ParsedBackup, selected: Set<BackupSection>) = withContext(Dispatchers.IO) {
        val d = parsed.data
        val p = parsed.presentKeys
        try {
            if (BackupSection.USER in selected && "user" in p) {
                val cur = settings.current()
                settings.setName(WebNormalizer.str(d["user"]["name"], 40, cur.name))
                // Android extension (absent in web backups): UI preferences.
                d["settings"]?.let { s ->
                    (s["lang"] as? JsonPrimitive)?.takeIf { it.isString && (it.content == "ar" || it.content == "en") }
                        ?.let { settings.setLanguage(AppLanguage.from(it.content)) }
                    (s["sound"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull?.let { settings.setSound(it) }
                    (s["theme"] as? JsonPrimitive)?.takeIf { it.isString && ThemeId.isValid(it.content) }
                        ?.let { settings.setTheme(ThemeId.from(it.content)) }
                }
            }
            if (BackupSection.COURSES in selected && "courses" in p) restoreCourses(parsed)
            if (BackupSection.NOTES in selected && parsed.notes != null) restoreNotes(parsed.notes)
            if (BackupSection.CALENDAR in selected) {
                if ("events" in p) replaceEvents(dedupe(WebNormalizer.arr(d["events"]).map(WebNormalizer::event), { it.id }) { e, id -> e.copy(id = id) })
                if ("tasks" in p) replaceTasks(dedupe(WebNormalizer.arr(d["tasks"]).map(WebNormalizer::task), { it.id }) { t, id -> t.copy(id = id) })
            }
            if (BackupSection.STUDY in selected) {
                if ("schedule" in p) replaceSchedule(dedupe(WebNormalizer.schedule(d["schedule"]), { it.id }) { s, id -> s.copy(id = id) })
                if ("decks" in p) replaceDecks(dedupe(WebNormalizer.arr(d["decks"]).map(WebNormalizer::deck), { it.id }) { k, id -> k.copy(id = id) })
                if ("quizzes" in p) replaceQuizzes(dedupe(WebNormalizer.arr(d["quizzes"]).map(WebNormalizer::quiz), { it.id }) { z, id -> z.copy(id = id) })
                if ("focus" in p) {
                    val f = WebNormalizer.focus(d["focus"])
                    settings.setFocus(f)
                }
            }
            if (BackupSection.ISLAM in selected && "islam" in p) putIslam(WebNormalizer.islam(d["islam"]))
            if (BackupSection.SUMMARIES in selected && "summaries" in p) {
                replaceLinks(LinkKind.SUMMARIES, dedupe(WebNormalizer.arr(d["summaries"]).mapNotNull { WebNormalizer.link(it, "Summary") }, { it.id }) { l, id -> l.copy(id = id) })
            }
            if (BackupSection.FORMS in selected && "forms" in p) {
                replaceLinks(LinkKind.FORMS, dedupe(WebNormalizer.arr(d["forms"]).mapNotNull { WebNormalizer.link(it, "Google Form") }, { it.id }) { l, id -> l.copy(id = id) })
            }
            if (BackupSection.BACKGROUND in selected) {
                ThemeId.entries.forEach { files.backgroundFile(it).delete() }
                parsed.backgrounds.orEmpty().forEach { (key, f) ->
                    if (ThemeId.isValid(key)) files.moveInto(f.file, files.backgroundFile(ThemeId.from(key)))
                }
            }
        } finally {
            parsed.discard()
        }
    }

    private suspend fun restoreCourses(parsed: ParsedBackup) {
        val seenU = HashSet<String>()
        val seenC = HashSet<String>()
        val courses = dedupe(WebNormalizer.arr(parsed.data["courses"]).mapNotNull(WebNormalizer::course), { it.id }) { c, id -> c.copy(id = id) }
            .map { c ->
                c.copy(units = c.units.map { u ->
                    val uid = if (seenU.add(u.id)) u.id else Web.uid().also { seenU += it }
                    u.copy(id = uid, contents = u.contents.map { x ->
                        if (seenC.add(x.id)) x else x.copy(id = Web.uid().also { seenC += it })
                    })
                })
            }
        val dao = db.courses()
        db.withTransaction {
            dao.clearAssets()
            dao.clearCourses()
            courses.forEachIndexed { ci, c ->
                dao.upsertCourse(c.toEntity(ci))
                dao.upsertUnits(c.units.mapIndexed { ui, u -> u.toEntity(c.id, ui) })
                dao.upsertContents(c.units.flatMap { u -> u.contents.mapIndexed { xi, x -> x.toEntity(u.id, xi) } })
            }
        }
        files.clearDir(files.courseDir)
        for (a in parsed.courseAssets) {
            if (!a.staged.file.exists()) continue
            files.moveInto(a.staged.file, files.courseFile(a.id))
            dao.upsertAsset(CourseAssetEntity(a.id, a.staged.mime, a.staged.size))
        }
    }

    private suspend fun restoreNotes(staged: List<StagedNote>) {
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        val built = staged.map { n ->
            val f = n.fields
            var id = WebNormalizer.str(f["id"], 40).ifEmpty { Web.uid() }
            if (!seen.add(id)) id = Web.uid().also { seen += it }
            Triple(
                Note(
                    id = id,
                    title = WebNormalizer.str(f["title"], 120),
                    body = WebNormalizer.str(f["body"], 20_000),
                    tags = WebNormalizer.arr(f["tags"]).map { WebNormalizer.str(it, 24) }.take(8),
                    createdAt = WebNormalizer.long(f["createdAt"], 0, MAX_TS, now),
                    updatedAt = WebNormalizer.long(f["updatedAt"], 0, MAX_TS, now),
                    pin = WebNormalizer.truthy(f["pin"]),
                ),
                n.images, n.audio,
            )
        }
        db.withTransaction { db.notes().clearNotes() }
        files.clearDir(files.noteDir)
        for ((note, images, audio) in built) {
            fun place(list: List<StagedAttachment>) = list.map { a ->
                val fileId = Web.uid()
                files.moveInto(a.staged.file, files.noteFile(fileId))
                NoteAttachment(fileId, a.name, a.staged.mime)
            }
            val full = note.copy(images = place(images), audio = place(audio))
            db.notes().replaceNote(full.toEntity(), full.attachmentEntities())
        }
    }

    private suspend fun replaceEvents(l: List<CalendarEvent>) = db.withTransaction {
        db.planner().clearEvents(); db.planner().insertEvents(l.mapIndexed { i, e -> e.toEntity(i) })
    }
    private suspend fun replaceTasks(l: List<Task>) = db.withTransaction {
        db.planner().clearTasks(); db.planner().insertTasks(l.mapIndexed { i, e -> e.toEntity(i) })
    }
    private suspend fun replaceSchedule(l: List<ScheduleEntry>) = db.withTransaction {
        db.planner().clearSchedule(); db.planner().insertSchedule(l.mapIndexed { i, e -> e.toEntity(i) })
    }
    private suspend fun replaceDecks(l: List<Deck>) = db.withTransaction {
        db.study().clearDecks(); db.study().insertDecks(l.mapIndexed { i, e -> e.toEntity(i) })
    }
    private suspend fun replaceQuizzes(l: List<Quiz>) = db.withTransaction {
        db.study().clearQuizzes(); db.study().insertQuizzes(l.mapIndexed { i, e -> e.toEntity(i) })
    }
    private suspend fun replaceLinks(kind: LinkKind, l: List<SavedLink>) = db.withTransaction {
        db.study().clearLinks(kind.key); db.study().insertLinks(l.mapIndexed { i, e -> e.toEntity(kind, i) })
    }
    private suspend fun putIslam(s: IslamState) = db.islam().put(IslamEntity(json = ExportJson.encodeToString(IslamState.serializer(), s)))

    /** Keeps the first occurrence of each id; later duplicates get a fresh uid (Room PKs must be unique). */
    private fun <T> dedupe(list: List<T>, id: (T) -> String, withId: (T, String) -> T): List<T> {
        val seen = HashSet<String>()
        return list.map { x -> if (seen.add(id(x))) x else withId(x, Web.uid().also { seen += it }) }
    }

    // ───────────────────────────── export ─────────────────────────────

    /** `exportBackup(ids)` — writes the backup JSON to [out] (not closed). */
    suspend fun export(out: OutputStream, selected: Set<BackupSection>) = withContext(Dispatchers.IO) {
        val w = BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), FileStore.BUFFER)
        w.write("{\"app\":\"$APP_ID\",\"version\":2,\"exportedAt\":")
        w.jsonString(ISO.format(Instant.now()))
        w.write(",\"sections\":{")
        w.write(BackupSection.entries.joinToString(",") { "\"${it.id}\":${it in selected}" })
        w.write("},\"data\":{")
        var first = true
        fun key(k: String) { if (!first) w.write(","); first = false; w.jsonString(k); w.write(":") }

        if (BackupSection.USER in selected) {
            val s = settings.current()
            key("user"); w.write(ExportJson.encodeToString(JsonElement.serializer(), buildJsonObject { put("name", s.name) }))
            key("settings"); w.write(ExportJson.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("lang", s.language.tag); put("sound", s.sound); put("theme", s.theme.key)
            }))
        }
        if (BackupSection.COURSES in selected) {
            val dao = db.courses()
            val courses = assembleCourses(dao.courses(), dao.units(), dao.contents())
            key("courses"); w.write(ExportJson.encodeToString(ListSerializer(Course.serializer()), courses))
            val needed = courses.flatMap { c -> c.contents.flatMap { it.allAssetIds } }.toSet()
            key("courseAssets"); w.write("[")
            var firstA = true
            for (a in dao.assets()) {
                val f = files.courseFile(a.id)
                if (a.id !in needed || !f.exists()) continue
                if (!firstA) w.write(","); firstA = false
                w.write("{\"id\":"); w.jsonString(a.id)
                w.write(",\"name\":\"\",\"type\":"); w.jsonString(a.mime)
                w.write(",\"data\":\""); DataUrlWriter.write(w, a.mime, f); w.write("\"}")
            }
            w.write("]")
        }
        if (BackupSection.NOTES in selected) {
            key("notes"); w.write("[")
            val att = db.notes().attachments().groupBy { it.noteId }
            db.notes().notes().forEachIndexed { i, e ->
                val n = e.toDomain(att[e.id].orEmpty())
                if (i > 0) w.write(",")
                w.write("{\"id\":"); w.jsonString(n.id)
                w.write(",\"title\":"); w.jsonString(n.title)
                w.write(",\"body\":"); w.jsonString(n.body)
                w.write(",\"tags\":"); w.write(ExportJson.encodeToString(ListSerializer(String.serializer()), n.tags))
                w.write(",\"pin\":${n.pin},\"createdAt\":${n.createdAt},\"updatedAt\":${n.updatedAt}")
                w.write(",\"images\":"); writeAttachments(w, n.images)
                w.write(",\"audio\":"); writeAttachments(w, n.audio)
                w.write("}")
            }
            w.write("]")
        }
        if (BackupSection.CALENDAR in selected) {
            key("events"); w.write(ExportJson.encodeToString(ListSerializer(CalendarEvent.serializer()), db.planner().events().map { it.toDomain() }))
            key("tasks"); w.write(ExportJson.encodeToString(ListSerializer(Task.serializer()), db.planner().tasks().map { it.toDomain() }))
        }
        if (BackupSection.STUDY in selected) {
            key("decks"); w.write(ExportJson.encodeToString(ListSerializer(Deck.serializer()), db.study().decks().map { it.toDomain() }))
            key("quizzes"); w.write(ExportJson.encodeToString(ListSerializer(Quiz.serializer()), db.study().quizzes().map { it.toDomain() }))
            key("schedule"); w.write(ExportJson.encodeToString(ListSerializer(ScheduleEntry.serializer()), db.planner().schedule().map { it.toDomain() }))
            val f = settings.currentFocus()
            key("focus"); w.write(ExportJson.encodeToString(JsonElement.serializer(), buildJsonObject {
                put("day", f.day); put("done", f.done); put("focusMins", f.focusMins); put("breakMins", f.breakMins)
            }))
        }
        if (BackupSection.ISLAM in selected) {
            val isl = db.islam().get()?.let { runCatching { ExportJson.decodeFromString(IslamState.serializer(), it) }.getOrNull() }
                ?: WebNormalizer.defaultIslam()
            key("islam"); w.write(ExportJson.encodeToString(IslamState.serializer(), isl))
        }
        if (BackupSection.SUMMARIES in selected) {
            key("summaries"); w.write(ExportJson.encodeToString(ListSerializer(SavedLink.serializer()), db.study().links(LinkKind.SUMMARIES.key).map { it.toDomain() }))
        }
        if (BackupSection.FORMS in selected) {
            key("forms"); w.write(ExportJson.encodeToString(ListSerializer(SavedLink.serializer()), db.study().links(LinkKind.FORMS.key).map { it.toDomain() }))
        }
        if (BackupSection.BACKGROUND in selected) {
            key("backgrounds"); w.write("{")
            var firstB = true
            for (t in ThemeId.entries) {
                val f = files.backgroundFile(t)
                if (!f.exists()) continue
                val mime = sniffImageMime(f)
                if (!firstB) w.write(","); firstB = false
                w.jsonString(t.key); w.write(":{\"type\":"); w.jsonString(mime)
                w.write(",\"data\":\""); DataUrlWriter.write(w, mime, f); w.write("\"}")
            }
            w.write("}")
        }
        w.write("}}")
        w.flush()
    }

    private fun writeAttachments(w: Writer, list: List<NoteAttachment>) {
        w.write("[")
        var firstX = true
        for (a in list) {
            val f = files.noteFile(a.fileId)
            if (!f.exists()) continue
            if (!firstX) w.write(","); firstX = false
            w.write("{\"name\":"); w.jsonString(a.name)
            w.write(",\"type\":"); w.jsonString(a.mime)
            w.write(",\"data\":\""); DataUrlWriter.write(w, a.mime, f); w.write("\"}")
        }
        w.write("]")
    }

    private fun Writer.jsonString(s: String) = write(ExportJson.encodeToString(String.serializer(), s))

    companion object {
        const val APP_ID = "ubad-academy-hub"
        private const val MAX_TS = 1_000_000_000_000_000L
        val ExportJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = true }
        /** JS `Date.prototype.toISOString()` → 2026-09-25T10:15:30.123Z */
        private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        fun fileName(today: String = Web.today()) = "ubad-backup-$today.json"

        /** Backgrounds are stored as raw bytes; recover the image type for `type`/data URL. */
        fun sniffImageMime(f: java.io.File): String {
            val h = ByteArray(12)
            val n = f.inputStream().use { it.read(h) }
            if (n < 4) return "image/*"
            return when {
                h[0] == 0xFF.toByte() && h[1] == 0xD8.toByte() -> "image/jpeg"
                h[0] == 0x89.toByte() && h[1] == 'P'.code.toByte() -> "image/png"
                h[0] == 'G'.code.toByte() && h[1] == 'I'.code.toByte() -> "image/gif"
                n >= 12 && h[8] == 'W'.code.toByte() && h[9] == 'E'.code.toByte() -> "image/webp"
                h[0] == '<'.code.toByte() -> "image/svg+xml"
                else -> "image/*"
            }
        }
    }
}
