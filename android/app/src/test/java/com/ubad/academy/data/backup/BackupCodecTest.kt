package com.ubad.academy.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ubad.academy.data.local.db.UbadDatabase
import com.ubad.academy.data.local.db.assembleCourses
import com.ubad.academy.data.local.db.toDomain
import com.ubad.academy.data.local.files.FileStore
import com.ubad.academy.data.local.prefs.SettingsStore
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.ThemeId
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {
    private lateinit var db: UbadDatabase
    private lateinit var files: FileStore
    private lateinit var settings: SettingsStore
    private lateinit var codec: BackupCodec

    private val pdfBytes = ByteArray(70_000) { (it * 31 % 251).toByte() }
    private val pngBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    /** Shaped exactly like app.js exportBackup() output (web-created backup). */
    private val webBackup by lazy {
        """
        {"app":"ubad-academy-hub","version":2,"exportedAt":"2026-09-01T10:00:00.000Z",
         "sections":{"user":true,"courses":true,"notes":true,"calendar":true,"study":true,"islam":true,"summaries":true,"forms":true,"background":true},
         "data":{
          "user":{"name":"عبدالله"},
          "courses":[{"id":"c1","name":"فيزياء","code":"PHY101","instructor":"د. أحمد","credits":3.5,"semester":"الخريف","createdAt":1700000000000,
            "units":[{"id":"u1","title":"الوحدة الأولى","contents":[
              {"id":"x1","type":"pdf","title":"محاضرة","text":"","assetId":"a1","assets":[],"name":"lec.pdf","mime":"application/pdf","source":"","url":"","done":true,"createdAt":1700000000001},
              {"id":"x2","type":"video","title":"شرح","url":"https://youtu.be/dQw4w9WgXcQ","done":false,"createdAt":1700000000002},
              {"id":"x3","type":"image","title":"صور","assets":[{"id":"a2","name":"p.png","mime":"image/png"}],"createdAt":1700000000003},
              {"id":"x4","type":"text","title":"ملخص","text":"سطر\nسطر","createdAt":1700000000004}]},
             {"id":"u2","title":"Legacy","lessons":[{"id":"l1","title":"Old lesson","done":true}]}]}],
          "courseAssets":[{"id":"a1","name":"","type":"application/pdf","data":"data:application/pdf;base64,${b64(pdfBytes)}"},
                          {"id":"a2","name":"","type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"},
                          {"id":"bad","name":"","type":"x","data":"blob:nope"}],
          "notes":[{"id":"n1","title":"ملاحظة","body":"نص","tags":["فيزياء","exam"],"pin":true,"createdAt":1700000001000,"updatedAt":1700000002000,
                    "images":[{"name":"board.png","type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"}],"audio":[]},
                   {"id":"n2","title":"b","body":"","tags":[],"pin":false,"createdAt":1,"updatedAt":2,"images":[],"audio":[]}],
          "events":[{"id":"e1","title":"امتحان","desc":"","date":"2026-10-01","time":"09:30","createdAt":5},
                    {"id":"e2","title":"bad date","date":"tomorrow","time":"9:30","createdAt":6}],
          "decks":[{"id":"d1","title":"Deck","createdAt":7,"cards":[{"id":"k1","front":"F","back":"B"}]}],
          "quizzes":[{"id":"q1","title":"Quiz","createdAt":8,"questions":[
             {"q":"2+2?","options":["3","4","",""],"correct":1},
             {"q":"invalid","options":["only one"],"correct":0}]}],
          "schedule":[{"id":"s1","title":"Study","days":[6,0,"1",9],"start":"18:00","end":"19:30","doneDates":{"2026-09-01":true,"x":true},"createdAt":9}],
          "islam":{"day":"2026-09-01","prayers":{"fajr":2,"zuhr":1,"asr":5},"rawatib":{"witr":1},
                   "tasbih":{"mode":"my1","count":12,"total":99,"target":100,"adhkar":[{"id":"sub","text":"x","builtin":true},{"id":"my1","text":"لا إله إلا الله","builtin":false}]},
                   "fasts":["2026-08-31","nope"],"hist":{"2026-09-01":4}},
          "summaries":[{"id":"m1","title":"Sum","url":"docs.google.com/document/d/1","createdAt":10,"lastOpened":0,"pinned":true},
                       {"id":"m2","title":"js","url":"javascript:alert(1)","createdAt":11}],
          "forms":[{"id":"f1","title":"Form","url":"https://forms.gle/abc","createdAt":12,"lastOpened":13,"pinned":false}],
          "backgrounds":{"sage":{"type":"image/png","data":"data:image/png;base64,${b64(pngBytes)}"},"unknown":{"type":"image/png","data":"data:image/png;base64,AA=="}}
         }}
        """.trimIndent()
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, UbadDatabase::class.java).allowMainThreadQueries().build()
        files = FileStore(ctx)
        settings = SettingsStore(ctx)
        codec = BackupCodec(db, files, settings)
    }

    @After fun tearDown() { db.close(); files.clearAll() }

    private suspend fun restoreAll(json: String) {
        val parsed = codec.parse(json.byteInputStream())
        codec.restore(parsed, parsed.available.toSet())
    }

    @Test fun `web backup restores with web normalization semantics`() = runTest {
        val parsed = codec.parse(webBackup.byteInputStream())
        assertEquals(BackupSection.entries.toList(), parsed.available)
        codec.restore(parsed, parsed.available.toSet())

        assertEquals("عبدالله", settings.current().name)

        val courses = assembleCourses(db.courses().courses(), db.courses().units(), db.courses().contents())
        val c = courses.single()
        assertEquals(3.5, c.credits, 0.0)
        val x = c.units[0].contents
        assertEquals(listOf("pdf", "video", "image", "text"), x.map { it.type })
        assertEquals("a1", x[0].assetId)
        assertTrue(x[0].done)
        assertEquals("youtube", x[1].source)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", x[1].url)
        assertEquals(listOf("a2"), x[2].assets.map { it.id })
        assertEquals("", x[2].assetId)
        assertEquals("سطر\nسطر", x[3].text)
        // legacy lessons → text contents
        assertEquals("Old lesson", c.units[1].contents.single().title)
        assertTrue(c.units[1].contents.single().done)

        assertArrayEquals(pdfBytes, files.courseFile("a1").readBytes())
        assertArrayEquals(pngBytes, files.courseFile("a2").readBytes())
        assertEquals("application/pdf", db.courses().asset("a1")!!.mime)
        assertEquals(null, db.courses().asset("bad"))

        val notes = db.notes().notes()
        assertEquals(listOf("n1", "n2"), notes.map { it.id }) // pinned first
        val n1 = notes[0].toDomain(db.notes().attachmentsOf("n1"))
        assertEquals(listOf("فيزياء", "exam"), n1.tags)
        assertEquals("board.png", n1.images.single().name)
        assertArrayEquals(pngBytes, files.noteFile(n1.images.single().fileId).readBytes())

        val events = db.planner().events().map { it.toDomain() }
        assertEquals("09:30", events[0].time)
        assertEquals("", events[1].time)          // invalid HH:MM dropped
        assertEquals(10, events[1].date.length)   // invalid date → today

        val quiz = db.study().quizzes().single().toDomain()
        assertEquals(1, quiz.questions.size)       // invalid question filtered
        val sched = db.planner().schedule().single().toDomain()
        assertEquals(listOf(6, 0, 1), sched.days)  // "1" coerced, 9 dropped
        assertEquals(mapOf("2026-09-01" to true), sched.doneDates)

        val isl = Json.decodeFromString(IslamState.serializer(), db.islam().get()!!)
        assertEquals(2, isl.prayers["fajr"]); assertEquals(2, isl.prayers["asr"]); assertEquals(0, isl.prayers["isha"])
        assertEquals("my1", isl.tasbih.mode)
        assertEquals(100, isl.tasbih.target)
        assertEquals(listOf("sub", "ham", "akb", "ist", "saw", "my1"), isl.tasbih.adhkar.map { it.id })
        assertEquals(listOf("2026-08-31"), isl.fasts)

        val sums = db.study().links(LinkKind.SUMMARIES.key)
        assertEquals(1, sums.size)                 // javascript: rejected
        assertEquals("https://docs.google.com/document/d/1", sums[0].url)
        assertTrue(files.backgroundFile(ThemeId.SAGE).exists())
    }

    @Test fun `export then import is lossless and web-shaped`() = runTest {
        restoreAll(webBackup)
        val out = ByteArrayOutputStream()
        codec.export(out, BackupSection.entries.toSet())
        val exported = out.toString(Charsets.UTF_8.name())

        // Shape checks against the web format.
        val root = Json.parseToJsonElement(exported).jsonObject
        assertEquals("ubad-academy-hub", root["app"]!!.jsonPrimitive.content)
        assertEquals("2", root["version"]!!.jsonPrimitive.content)
        assertTrue(Regex("""\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d\.\d{3}Z""").matches(root["exportedAt"]!!.jsonPrimitive.content))
        assertEquals(BackupSection.entries.map { it.id }, root["sections"]!!.jsonObject.keys.toList())
        val data = root["data"]!!.jsonObject
        val course = data["courses"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("id", "name", "code", "instructor", "credits", "semester", "createdAt", "units"), course.keys.toList())
        assertEquals("3.5", course["credits"]!!.jsonPrimitive.content)
        // Multi-image asset ids are exported too.
        assertEquals(setOf("a1", "a2"), data["courseAssets"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet())
        val img = data["notes"]!!.jsonArray[0].jsonObject["images"]!!.jsonArray[0].jsonObject
        assertEquals(listOf("name", "type", "data"), img.keys.toList())
        assertTrue(img["data"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,"))

        // Snapshot, wipe, re-import our own export, compare.
        val before = snapshot()
        db.clearAllTables(); files.clearAll()
        restoreAll(exported)
        assertEquals(before, snapshot())
        assertArrayEquals(pdfBytes, files.courseFile("a1").readBytes())
    }

    @Test fun `partial restore only touches selected sections`() = runTest {
        restoreAll(webBackup)
        val onlyForms = """{"app":"ubad-academy-hub","version":2,"sections":{"forms":true},"data":{"forms":[],"courses":[]}}"""
        val parsed = codec.parse(onlyForms.byteInputStream())
        codec.restore(parsed, setOf(BackupSection.FORMS))
        assertEquals(0, db.study().links(LinkKind.FORMS.key).size)
        assertEquals(1, db.courses().courses().size) // courses not selected → untouched
    }

    @Test fun `invalid files are rejected`() = runTest {
        for (bad in listOf("not json", "[]", """{"app":"other","data":{}}""", """{"app":"ubad-academy-hub"}""", """{"app":"ubad-academy-hub","data":{}}""")) {
            try { codec.parse(bad.byteInputStream()); fail("accepted: $bad") } catch (e: BackupException) { /* expected */ }
        }
        assertFalse(files.tmpDir.listFiles().orEmpty().any())
    }

    private suspend fun snapshot(): String {
        val courses = assembleCourses(db.courses().courses(), db.courses().units(), db.courses().contents())
        val notes = db.notes().notes().map { e -> e.toDomain(db.notes().attachmentsOf(e.id)).copy(images = emptyList(), audio = emptyList()) }
        val att = db.notes().attachments().map { it.name to files.noteFile(it.fileId).readBytes().toList() }
        return listOf(
            courses, notes, att, db.planner().events(), db.planner().schedule(), db.study().decks(), db.study().quizzes(),
            db.study().links("forms"), db.study().links("summaries"), db.islam().get(), settings.current().name,
            db.courses().assets(),
        ).joinToString("\n")
    }

    @Suppress("unused") private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
}
