package com.ubad.academy.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ubad.academy.core.Web
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.assembleCourses
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.data.repository.IslamRepository
import com.ubad.academy.data.repository.PlannerRepository
import com.ubad.academy.data.repository.StudyRepository
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.QuizQuestion
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

/**
 * ANDROID → WEB: every section of an Android export must satisfy exactly what the web
 * app's `importBackup()` / `inferBackupSections()` / `restoreBackup()` (app.js) read, so
 * the web restores the same meaning. Data created natively on Android is included.
 *
 * The contract per field mirrors the web restore code (normStr limits, clampNum ranges,
 * `startsWith('data:')`, the quiz validity filter, safeHttpUrl, …). Where the web runs a
 * normalizer, the exported value must be a fixed point of the Kotlin port of that
 * normalizer (which [BackupCodecTest] checks against web-shaped input).
 */
@RunWith(RobolectricTestRunner::class)
class BackupWebCompatTest {
    private lateinit var db: UbadDatabase
    private lateinit var files: FileStore
    private lateinit var settings: SettingsStore
    private lateinit var codec: BackupCodec

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, UbadDatabase::class.java).allowMainThreadQueries().build()
        files = FileStore(ctx)
        settings = SettingsStore(ctx)
        codec = BackupCodec(db, files, settings)
    }

    @After fun tearDown() { db.close(); files.clearAll() }

    /** Web data + things created with the native screens' repositories. */
    private suspend fun seed() {
        val parsed = codec.parse(BackupFixtures.webBackup.byteInputStream())
        codec.restore(parsed, parsed.available.toSet())
        val study = StudyRepository(db)
        val deckId = study.createDeck("  بطاقات أندرويد  ")
        study.addCard(study.deck(deckId).first()!!, "وجه", "ظهر")
        study.saveQuiz(Quiz(Web.uid(), "اختبار", System.currentTimeMillis(), listOf(
            QuizQuestion("سؤال؟", listOf("أ", "ب", "", ""), 1),
            QuizQuestion("", listOf("x", "y"), 0), // invalid → dropped on save like the web filter
        )))
        study.saveLink(LinkKind.FORMS, null, "نموذج", "forms.gle/xyz")
        PlannerRepository(db).saveSchedule(null, "مذاكرة", listOf(5, 6), "07:05", "08:10")
        val islam = IslamRepository(db)
        islam.togglePrayer("isha"); islam.toggleRawatib("duha"); islam.toggleFastToday(); islam.tasbihSave(null, "سبحان الله وبحمده")
        repeat(3) { islam.tasbihTap() }
        settings.setName("مستخدم أندرويد")
    }

    private suspend fun export(sections: Set<BackupSection> = BackupSection.entries.toSet()): JsonObject {
        val out = ByteArrayOutputStream()
        codec.export(out, sections)
        return Json.parseToJsonElement(out.toString(Charsets.UTF_8.name())).jsonObject
    }

    // ── helpers mirroring app.js semantics ──
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.s(k: String) = this[k].str()
    private fun assertStr(o: JsonObject, k: String, max: Int, allowEmpty: Boolean = true) {
        val v = o.s(k); assertNotNull("$k must be a string in $o", v)
        assertTrue("$k longer than $max", v!!.length <= max)
        if (!allowEmpty) assertTrue("$k empty", v.isNotEmpty())
    }
    /** clampNum(v,0,1e15,…) keeps the value only for a finite integer-ish number in range. */
    private fun assertTime(o: JsonObject, k: String) {
        val p = o[k] as? JsonPrimitive; assertNotNull("$k missing", p)
        assertFalse("$k is a string", p!!.isString)
        assertTrue("$k must be a plain integer, was ${p.content}", Regex("^\\d{1,16}$").matches(p.content))
    }
    private fun assertBool(o: JsonObject, k: String) = assertNotNull("$k must be boolean", (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull)
    private fun assertDataUrl(o: JsonObject, mimeKey: String? = null) {
        val d = o.s("data"); assertNotNull(d)
        assertTrue("data must start with data:", d!!.startsWith("data:"))
        if (mimeKey != null) assertTrue(d.startsWith("data:${o.s(mimeKey)};base64,"))
    }

    @Test fun `envelope and sections match inferBackupSections`() = runTest {
        seed()
        val root = export()
        assertEquals("ubad-academy-hub", root.s("app"))
        assertEquals(2, (root["version"] as JsonPrimitive).content.toInt())
        val secs = root["sections"]!!.jsonObject
        BackupSection.entries.forEach { assertEquals(true, (secs[it.id] as JsonPrimitive).booleanOrNull) }
        val d = root["data"]!!.jsonObject
        // Keys the web uses to infer each section must be present (truthy) in a full export.
        listOf("user", "courses", "notes", "events", "decks", "quizzes", "schedule", "islam", "summaries", "forms", "backgrounds").forEach {
            assertTrue("data.$it missing", it in d)
        }
    }

    @Test fun `partial export only offers the chosen sections to the web`() = runTest {
        seed()
        val root = export(setOf(BackupSection.NOTES, BackupSection.ISLAM))
        val secs = root["sections"]!!.jsonObject
        assertEquals(setOf("notes", "islam"), secs.filterValues { (it as JsonPrimitive).booleanOrNull == true }.keys)
        val d = root["data"]!!.jsonObject
        // With `sections` present the web never uses legacy inference, so extra keys can't leak sections.
        assertEquals(setOf("notes", "islam"), d.keys)
    }

    @Test fun `every section satisfies the web restore contract`() = runTest {
        seed()
        val d = export()["data"]!!.jsonObject

        // user — state.user.name=normStr(d.user.name,40,…)
        assertStr(d["user"]!!.jsonObject, "name", 40, allowEmpty = false)
        assertEquals("مستخدم أندرويد", d["user"]!!.jsonObject.s("name"))

        // courses — normCourse is a fixed point, assets referenced by contents are exported as data URLs.
        val courses = d["courses"]!!.jsonArray
        val dbCourses = assembleCourses(db.courses().courses(), db.courses().units(), db.courses().contents())
        assertEquals(dbCourses, courses.map { WebNormalizer.course(it)!! })
        val assets = d["courseAssets"]!!.jsonArray.map { it.jsonObject }
        assets.forEach { a -> assertStr(a, "id", 80, allowEmpty = false); assertDataUrl(a, "type") }
        val referenced = dbCourses.flatMap { c -> c.contents.flatMap { it.allAssetIds } }.toSet()
        assertEquals(referenced, assets.map { it.s("id")!! }.toSet())

        // notes — ids ≤40, title ≤120, body ≤20000, ≤8 tags ≤24, pin boolean, times, attachments as data URLs.
        val notes = d["notes"]!!.jsonArray.map { it.jsonObject }
        assertTrue(notes.isNotEmpty())
        notes.forEach { n ->
            assertStr(n, "id", 40, allowEmpty = false); assertStr(n, "title", 120); assertStr(n, "body", 20000)
            val tags = n["tags"]!!.jsonArray; assertTrue(tags.size <= 8); tags.forEach { assertTrue(it.str()!!.length <= 24) }
            assertBool(n, "pin"); assertTime(n, "createdAt"); assertTime(n, "updatedAt")
            (n["images"]!!.jsonArray + n["audio"]!!.jsonArray).map { it.jsonObject }.forEach { a -> assertStr(a, "name", 80); assertDataUrl(a) }
        }
        assertEquals("board.png", notes.first { it.s("id") == "n1" }["images"]!!.jsonArray[0].jsonObject.s("name"))

        // events — date must match YYYY-MM-DD and time HH:MM or '' (else the web replaces them).
        d["events"]!!.jsonArray.map { it.jsonObject }.forEach { e ->
            assertStr(e, "id", 40, allowEmpty = false); assertStr(e, "title", 120, allowEmpty = false); assertStr(e, "desc", 500)
            assertTrue(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(e.s("date")!!))
            assertTrue(e.s("time")!!.let { it.isEmpty() || Regex("^\\d{2}:\\d{2}$").matches(it) })
            assertTime(e, "createdAt")
        }

        // study — decks/cards limits, quiz validity filter keeps every question, schedule normalizer fixed point.
        val decks = d["decks"]!!.jsonArray.map { it.jsonObject }
        assertEquals(db.study().decks().map { it.toDomain() }, decks.map { WebNormalizer.deck(it) })
        assertTrue(decks.any { it.s("title") == "بطاقات أندرويد" && it["cards"]!!.jsonArray.size == 1 })
        val quizzes = d["quizzes"]!!.jsonArray
        assertEquals(db.study().quizzes().map { it.toDomain() }, quizzes.map { WebNormalizer.quiz(it) })
        quizzes.flatMap { it.jsonObject["questions"]!!.jsonArray }.map { it.jsonObject }.forEach { q ->
            val opts = q["options"]!!.jsonArray.map { it.str()!! }
            val correct = (q["correct"] as JsonPrimitive).content.toInt()
            assertTrue(q.s("q")!!.isNotEmpty() && opts.size <= 4 && opts.count { it.isNotEmpty() } >= 2 && correct in 0..3 && opts[correct].isNotEmpty())
        }
        assertEquals(db.planner().schedule().map { it.toDomain() }, WebNormalizer.schedule(d["schedule"]))

        // islam — normIslam fixed point (prayers, sunnah, fasting, tasbih + custom dhikr, history).
        val isl = Json.decodeFromString(IslamState.serializer(), db.islam().get()!!)
        val round = WebNormalizer.islam(d["islam"])
        assertEquals(isl, round)
        assertTrue((round.prayers["isha"] ?: 0) != 0); assertTrue((round.rawatib["duha"] ?: 0) != 0)
        assertTrue(Web.today() in round.fasts)
        assertTrue(round.tasbih.adhkar.any { !it.builtin && it.text == "سبحان الله وبحمده" })

        // summaries/forms — url must survive safeHttpUrl unchanged; web keeps pinned/lastOpened.
        for (k in listOf("summaries", "forms")) {
            d[k]!!.jsonArray.map { it.jsonObject }.forEach { l ->
                assertStr(l, "id", 40, allowEmpty = false); assertStr(l, "title", 120, allowEmpty = false)
                assertEquals(l.s("url"), Web.safeHttpUrl(l.s("url")))
                assertTime(l, "createdAt"); assertTime(l, "lastOpened"); assertBool(l, "pinned")
            }
        }
        assertTrue(d["forms"]!!.jsonArray.any { it.jsonObject.s("url") == "https://forms.gle/xyz" })

        // backgrounds — only known themes, each {type,data:data:<type>;base64,…}.
        val bgs = d["backgrounds"]!!.jsonObject
        assertEquals(setOf("sage"), bgs.keys)
        bgs.values.map { it.jsonObject }.forEach { b -> assertTrue(b.s("type")!!.startsWith("image/")); assertDataUrl(b, "type") }
        assertTrue(bgs.keys.all { ThemeId.isValid(it) })
    }

    @Test fun `numbers are plain JSON numbers the web can compare`() = runTest {
        seed()
        val d = export()["data"]!!.jsonObject
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> e.values.forEach(::walk)
                is JsonArray -> e.forEach(::walk)
                is JsonPrimitive -> if (!e.isString && e.content != "true" && e.content != "false" && e.content != "null") {
                    assertFalse("exponent/NaN in ${e.content}", e.content.contains('E') || e.content.contains("NaN") || e.content.contains("Infinity"))
                }
            }
        }
        walk(d)
    }
}
