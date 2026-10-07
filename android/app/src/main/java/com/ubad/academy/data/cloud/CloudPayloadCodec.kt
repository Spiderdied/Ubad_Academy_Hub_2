package com.ubad.academy.data.cloud

import androidx.room.withTransaction
import com.ubad.academy.core.Web
import com.ubad.academy.data.backup.BackupCodec
import com.ubad.academy.data.backup.WebNormalizer
import com.ubad.academy.data.local.db.IslamEntity
import com.ubad.academy.data.local.db.NoteEntity
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.assembleCourses
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.db.toEntity
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.IslamRepository
import com.ubad.academy.domain.model.AppLanguage
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and applies the cloud payload — the structured half of cloud sync.
 *
 * **Compatibility is the whole point of this file.** The payload is the same
 * object the web app's `cloudPayload()` produces and `hydrate()` consumes, so a
 * document written by either client is readable by the other and the Firebase
 * UID stays the single identity across web and Android.
 *
 * Serialization therefore reuses [BackupCodec.ExportJson] rather than a fresh
 * `Json` instance, which guarantees byte-identical encoding to the backup/web
 * codec (defaults written, explicit nulls, unknown keys ignored).
 *
 * Binaries are **not** here: they travel through Backblaze B2 and are described
 * by `cloudState.files`. Notes therefore carry only metadata plus the web's
 * `hasImages` / `hasAudio` flags, and applying a payload *preserves* whatever
 * local attachment files already exist for a note id.
 */
@Singleton
class CloudPayloadCodec @Inject constructor(
    private val db: UbadDatabase,
    private val settings: SettingsStore,
    private val islam: IslamRepository,
) {
    private val json = BackupCodec.ExportJson

    // ───────────────────────────── push ─────────────────────────────

    /** The web `cloudPayload()`. */
    suspend fun build(): JsonObject = withContext(Dispatchers.IO) {
        val s = settings.current()
        val focus = settings.currentFocus()
        val courses = db.courses()
        val courseList = assembleCourses(courses.courses(), courses.units(), courses.contents())

        val attachmentsByNote = db.notes().attachments().groupBy { it.noteId }
        val notes = db.notes().notes().map { entity ->
            entity.toDomain(attachmentsByNote[entity.id].orEmpty())
        }

        buildJsonObject {
            put("version", JsonPrimitive(com.ubad.academy.core.cloud.CloudConfig.SYNC_VERSION))
            put(
                "user",
                buildJsonObject { put("name", JsonPrimitive(s.name)) },
            )
            put(
                "settings",
                buildJsonObject {
                    put("lang", JsonPrimitive(s.language.tag))
                    put("sound", JsonPrimitive(s.sound))
                    put("theme", JsonPrimitive(s.theme.key))
                },
            )
            put("courses", encode(courseList))
            put("events", encode(db.planner().events().map { it.toDomain() }))
            put("tasks", encode(db.planner().tasks().map { it.toDomain() }))
            put("schedule", encode(db.planner().schedule().map { it.toDomain() }))
            put("decks", encode(db.study().decks().map { it.toDomain() }))
            put("quizzes", encode(db.study().quizzes().map { it.toDomain() }))
            put("forms", encode(db.study().links(LinkKind.FORMS.key).map { it.toDomain() }))
            put("summaries", encode(db.study().links(LinkKind.SUMMARIES.key).map { it.toDomain() }))
            put(
                "focus",
                buildJsonObject {
                    put("day", JsonPrimitive(focus.day))
                    put("done", JsonPrimitive(focus.done))
                    put("focusMins", JsonPrimitive(focus.focusMins))
                    put("breakMins", JsonPrimitive(focus.breakMins))
                },
            )
            put("islam", json.encodeToJsonElement(IslamState.serializer(), islam.state.first()))
            put("notes", buildJsonArray { notes.forEach { add(noteMeta(it)) } })
        }
    }

    /**
     * The web's note metadata for the cloud payload. Explicitly matches
     * `cloudNotes()`: binaries are represented by flags only.
     */
    private fun noteMeta(n: com.ubad.academy.domain.model.Note): JsonElement = buildJsonObject {
        put("id", JsonPrimitive(n.id))
        put("title", JsonPrimitive(n.title))
        put("body", JsonPrimitive(n.body))
        put("tags", buildJsonArray { n.tags.forEach { add(JsonPrimitive(it)) } })
        put("createdAt", JsonPrimitive(n.createdAt))
        put("updatedAt", JsonPrimitive(n.updatedAt))
        put("pin", JsonPrimitive(n.pin))
        put("hasImages", JsonPrimitive(n.images.isNotEmpty()))
        put("hasAudio", JsonPrimitive(n.audio.isNotEmpty()))
    }

    private inline fun <reified T> encode(list: List<T>): JsonElement =
        json.encodeToJsonElement(ListSerializer(serializer<T>()), list)

    /** Web `hasMeaningfulLocalData()` — decides whether the first sync must ask. */
    suspend fun hasMeaningfulLocalData(): Boolean = withContext(Dispatchers.IO) {
        val s = settings.current()
        if (s.name != com.ubad.academy.domain.model.UserSettings.DEFAULT_NAME) return@withContext true
        db.courses().courses().isNotEmpty() ||
            db.notes().notes().isNotEmpty() ||
            db.planner().events().isNotEmpty() ||
            db.planner().tasks().isNotEmpty() ||
            db.planner().schedule().isNotEmpty() ||
            db.study().decks().isNotEmpty() ||
            db.study().quizzes().isNotEmpty() ||
            db.study().links(LinkKind.FORMS.key).isNotEmpty() ||
            db.study().links(LinkKind.SUMMARIES.key).isNotEmpty() ||
            islam.state.first().fasts.isNotEmpty()
    }

    // ───────────────────────────── pull ─────────────────────────────

    /**
     * Applies a cloud payload: `hydrate()` for every structured section, then
     * `mergeCloudNotes()` for notes (which keeps local attachment files).
     */
    suspend fun apply(payload: JsonObject) = withContext(Dispatchers.IO) {
        if (payload.isEmpty()) return@withContext
        val now = System.currentTimeMillis()

        // user + settings + focus
        (payload["user"] as? JsonObject)?.let { u ->
            settings.setName(WebNormalizer.str(u["name"], 40, settings.current().name))
        }
        (payload["settings"] as? JsonObject)?.let { st ->
            (st["lang"] as? JsonPrimitive)?.takeIf { it.isString && (it.content == "ar" || it.content == "en") }
                ?.let { settings.setLanguage(AppLanguage.from(it.content)) }
            (st["sound"] as? JsonPrimitive)?.takeIf { !it.isString }?.let { p ->
                p.content.toBooleanStrictOrNull()?.let { settings.setSound(it) }
            }
            (st["theme"] as? JsonPrimitive)?.takeIf { it.isString && ThemeId.isValid(it.content) }
                ?.let { settings.setTheme(ThemeId.from(it.content)) }
        }
        (payload["focus"] as? JsonObject)?.let { settings.setFocus(WebNormalizer.focus(it)) }

        (payload["courses"] as? JsonArray)?.let { arr ->
            replaceCourses(arr.mapNotNull(WebNormalizer::course))
        }
        (payload["events"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).map(WebNormalizer::event)
            db.withTransaction {
                db.planner().clearEvents()
                db.planner().insertEvents(list.mapIndexed { i, e -> e.toEntity(i) })
            }
        }
        (payload["tasks"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).map(WebNormalizer::task)
            db.withTransaction {
                db.planner().clearTasks()
                db.planner().insertTasks(list.mapIndexed { i, t -> t.toEntity(i) })
            }
        }
        (payload["schedule"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.schedule(arr)
            db.withTransaction {
                db.planner().clearSchedule()
                db.planner().insertSchedule(list.mapIndexed { i, s -> s.toEntity(i) })
            }
        }
        (payload["decks"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).map(WebNormalizer::deck)
            db.withTransaction {
                db.study().clearDecks()
                db.study().insertDecks(list.mapIndexed { i, d -> d.toEntity(i) })
            }
        }
        (payload["quizzes"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).map(WebNormalizer::quiz)
            db.withTransaction {
                db.study().clearQuizzes()
                db.study().insertQuizzes(list.mapIndexed { i, z -> z.toEntity(i) })
            }
        }
        (payload["forms"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).mapNotNull { WebNormalizer.link(it, "Google Form") }
            db.withTransaction {
                db.study().clearLinks(LinkKind.FORMS.key)
                db.study().insertLinks(list.mapIndexed { i, l -> l.toEntity(LinkKind.FORMS, i) })
            }
        }
        (payload["summaries"] as? JsonArray)?.let { arr ->
            val list = WebNormalizer.arr(arr).mapNotNull { WebNormalizer.link(it, "Summary") }
            db.withTransaction {
                db.study().clearLinks(LinkKind.SUMMARIES.key)
                db.study().insertLinks(list.mapIndexed { i, l -> l.toEntity(LinkKind.SUMMARIES, i) })
            }
        }
        (payload["islam"] as? JsonObject)?.let { s ->
            db.islam().put(IslamEntity(json = json.encodeToString(IslamState.serializer(), WebNormalizer.islam(s))))
        }
        (payload["notes"] as? JsonArray)?.let { arr ->
            mergeNotes(arr.mapNotNull { it as? JsonObject }, now)
        }
    }

    /**
     * Course replacement that **preserves local course assets**.
     *
     * `BackupCodec.restoreCourses` intentionally clears the asset table and the
     * course-asset directory, because a backup carries its own copies. A cloud
     * payload does not — the binaries live in B2 and arrive separately — so
     * clearing here would delete the user's files.
     */
    private suspend fun replaceCourses(courses: List<Course>) {
        val dao = db.courses()
        val seenUnits = HashSet<String>()
        val seenContents = HashSet<String>()
        val normalised = courses.map { c ->
            c.copy(
                units = c.units.map { u ->
                    val unitId = if (seenUnits.add(u.id)) u.id else Web.uid().also { seenUnits += it }
                    u.copy(
                        id = unitId,
                        contents = u.contents.map { x ->
                            if (seenContents.add(x.id)) x else x.copy(id = Web.uid().also { seenContents += it })
                        },
                    )
                },
            )
        }
        db.withTransaction {
            dao.clearCourses()
            normalised.forEachIndexed { ci, c ->
                dao.upsertCourse(c.toEntity(ci))
                dao.upsertUnits(c.units.mapIndexed { ui, u -> u.toEntity(c.id, ui) })
                dao.upsertContents(c.units.flatMap { u -> u.contents.mapIndexed { xi, x -> x.toEntity(u.id, xi) } })
            }
        }
    }

    /**
     * Web `mergeCloudNotes()`: cloud metadata wins, **local attachment rows are
     * kept** so a download is never required just to see an existing note.
     *
     * Attachment rows are captured before `clearNotes()` because the
     * `note_attachments` foreign key cascades on delete.
     */
    private suspend fun mergeNotes(cloudNotes: List<JsonObject>, now: Long) {
        val kept = db.notes().attachments().groupBy { it.noteId }
        val seen = HashSet<String>()
        val built = cloudNotes.map { n ->
            var id = WebNormalizer.str(n["id"], 40).ifEmpty { Web.uid() }
            if (!seen.add(id)) id = Web.uid().also { seen += it }
            val entity = NoteEntity(
                id = id,
                title = WebNormalizer.str(n["title"], 120),
                body = WebNormalizer.str(n["body"], 20_000),
                tagsJson = json.encodeToString(
                    ListSerializer(String.serializer()),
                    WebNormalizer.arr(n["tags"]).map { WebNormalizer.str(it, 24) }.take(8),
                ),
                pin = WebNormalizer.truthy(n["pin"]),
                createdAt = WebNormalizer.long(n["createdAt"], 0, MAX_TS, now),
                updatedAt = WebNormalizer.long(n["updatedAt"], 0, MAX_TS, now),
            )
            entity to kept[id].orEmpty()
        }
        db.withTransaction {
            db.notes().clearNotes()
            built.forEach { (entity, attachments) -> db.notes().replaceNote(entity, attachments) }
        }
    }

    private companion object {
        const val MAX_TS = 1_000_000_000_000_000L
    }
}
