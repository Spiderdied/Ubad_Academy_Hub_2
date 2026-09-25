package com.ubad.academy.data.backup

import com.ubad.academy.core.Web
import com.ubad.academy.domain.model.AssetRef
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.CourseContent
import com.ubad.academy.domain.model.CourseUnit
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.Dhikr
import com.ubad.academy.domain.model.Flashcard
import com.ubad.academy.domain.model.FocusSettings
import com.ubad.academy.domain.model.IslamState
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.QuizQuestion
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.domain.model.Task
import com.ubad.academy.domain.model.Tasbih
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Faithful port of app.js's defensive normalization (normCourse, normIslam,
 * normSchedule and the inline mappers in hydrate()/restoreBackup()).
 *
 * The rules are applied to *untrusted* backup JSON, exactly like the web app,
 * so a backup produces the same data on both platforms.
 */
object WebNormalizer {

    // ── JS coercion helpers ────────────────────────────────────────────

    /** `normStr(v,max,f)` — strings are truncated, numbers stringified, anything else → f. */
    fun str(v: JsonElement?, max: Int, f: String = ""): String {
        val p = v as? JsonPrimitive ?: return f
        if (p is JsonNull) return f
        return when {
            p.isString -> p.content.jsTake(max)
            p.booleanOrNull != null -> f
            p.doubleOrNull != null -> jsNumberString(p.doubleOrNull!!).jsTake(max)
            else -> f
        }
    }

    /** `clampNum(v,min,max,f)` using JS `Number()` coercion semantics. */
    fun num(v: JsonElement?, min: Double, max: Double, f: Double): Double {
        val n: Double = when (v) {
            null -> Double.NaN                       // undefined
            is JsonNull -> 0.0                       // Number(null) === 0
            is JsonPrimitive -> when {
                v.isString -> v.content.trim().let { s -> if (s.isEmpty()) 0.0 else s.toDoubleOrNull() ?: Double.NaN }
                v.booleanOrNull != null -> if (v.booleanOrNull == true) 1.0 else 0.0
                else -> v.doubleOrNull ?: Double.NaN
            }
            is JsonArray -> when (v.size) { 0 -> 0.0; 1 -> num(v[0], -Double.MAX_VALUE, Double.MAX_VALUE, Double.NaN); else -> Double.NaN }
            is JsonObject -> Double.NaN
        }
        return if (n.isFinite()) n.coerceIn(min, max) else f
    }

    fun long(v: JsonElement?, min: Long, max: Long, f: Long): Long =
        num(v, min.toDouble(), max.toDouble(), f.toDouble()).toLong()

    fun int(v: JsonElement?, min: Int, max: Int, f: Int): Int =
        num(v, min.toDouble(), max.toDouble(), f.toDouble()).toInt()

    /** JS truthiness `!!v`. */
    fun truthy(v: JsonElement?): Boolean = when (v) {
        null, is JsonNull -> false
        is JsonPrimitive -> when {
            v.isString -> v.content.isNotEmpty()
            v.booleanOrNull != null -> v.booleanOrNull!!
            else -> (v.doubleOrNull ?: 0.0).let { it != 0.0 && !it.isNaN() }
        }
        else -> true
    }

    fun arr(v: JsonElement?): List<JsonElement> = (v as? JsonArray)?.toList() ?: emptyList()
    fun obj(v: JsonElement?): JsonObject? = v as? JsonObject
    operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    private fun rawString(v: JsonElement?): String? = (v as? JsonPrimitive)?.takeIf { it.isString }?.content

    private val NOW get() = System.currentTimeMillis()

    // ── domain mappers ─────────────────────────────────────────────────

    fun course(c: JsonElement?): Course? {
        val o = obj(c) ?: return null
        return Course(
            id = str(o["id"], 40).ifEmpty { Web.uid() },
            name = str(o["name"], 80, "Course"),
            code = str(o["code"], 24),
            instructor = str(o["instructor"], 80),
            credits = num(o["credits"], 0.0, 99.0, 3.0),
            semester = str(o["semester"], 40),
            createdAt = long(o["createdAt"], 0, 1_000_000_000_000_000, NOW),
            units = arr(o["units"]).map { unit(it) },
        )
    }

    private fun unit(u: JsonElement?): CourseUnit {
        val legacy = arr(u["lessons"]).map { l ->
            CourseContent(
                id = str(l["id"], 40).ifEmpty { Web.uid() }, type = "text",
                title = str(l["title"], 120, "Content"), text = "", done = truthy(l["done"]), createdAt = NOW,
            )
        }
        val raw = arr(u["contents"])
        val contents = if (raw.isNotEmpty()) raw.map { content(it) } else legacy
        return CourseUnit(id = str(u["id"], 40).ifEmpty { Web.uid() }, title = str(u["title"], 80, "Unit"), contents = contents)
    }

    fun content(x: JsonElement?): CourseContent {
        val typeRaw = rawString(x["type"])
        val type = if (typeRaw in listOf("text", "image", "video", "audio", "pdf")) typeRaw!! else "text"
        val rawUrl = if (type == "video") Web.safeHttpUrl(rawString(x["url"])) else null
        val ytId = if (type == "video") Web.youtubeVideoId(rawUrl) else null
        val legacyAssetId = str(x["assetId"], 80)
        val multi = if (type == "image") arr(x["assets"]).map { a ->
            AssetRef(id = str(a["id"], 80), name = str(a["name"], 160, "image"), mime = str(a["mime"], 120))
        }.filter { it.id.isNotEmpty() }.toMutableList() else mutableListOf()
        if (type == "image" && multi.isEmpty() && legacyAssetId.isNotEmpty()) {
            multi += AssetRef(legacyAssetId, str(x["name"], 160, "image"), str(x["mime"], 120))
        }
        val keepAsset = !(type == "text" || type == "image" || (type == "video" && ytId != null))
        return CourseContent(
            id = str(x["id"], 40).ifEmpty { Web.uid() },
            type = type,
            title = str(x["title"], 120, "Content"),
            text = if (type == "text") str(x["text"], 50_000) else "",
            assetId = if (keepAsset) legacyAssetId else "",
            assets = multi,
            name = str(x["name"], 160),
            mime = str(x["mime"], 120),
            source = if (type == "video") (if (ytId != null) "youtube" else "local") else "",
            url = if (ytId != null) rawUrl.orEmpty() else "",
            done = truthy(x["done"]),
            createdAt = long(x["createdAt"], 0, 1_000_000_000_000_000, NOW),
        )
    }

    fun event(e: JsonElement?): CalendarEvent = CalendarEvent(
        id = str(e["id"], 40).ifEmpty { Web.uid() },
        title = str(e["title"], 120, "Event"),
        desc = str(e["desc"], 500),
        date = rawString(e["date"]).takeIf { Web.isYmd(it) } ?: Web.today(),
        time = rawString(e["time"]).takeIf { Web.isHm(it) } ?: "",
        createdAt = long(e["createdAt"], 0, 1_000_000_000_000_000, NOW),
    )

    fun task(x: JsonElement?): Task = Task(
        id = str(x["id"], 40).ifEmpty { Web.uid() },
        title = str(x["title"], 120, "Task"),
        done = truthy(x["done"]),
        due = rawString(x["due"]).takeIf { Web.isYmd(it) } ?: "",
        createdAt = long(x["createdAt"], 0, 1_000_000_000_000_000, NOW),
    )

    fun schedule(list: JsonElement?): List<ScheduleEntry> = arr(list).mapNotNull { x ->
        val o = obj(x) ?: return@mapNotNull null
        val start = rawString(o["start"]).takeIf { Web.isHm(it) } ?: "18:00"
        val end = rawString(o["end"]).takeIf { Web.isHm(it) } ?: Web.minToHm((Web.hmToMin(start) ?: 1080) + 60)
        val days = arr(o["days"]).mapNotNull { d ->
            val n = num(d, -1e9, 1e9, Double.NaN)
            if (!n.isNaN() && n % 1.0 == 0.0 && n in 0.0..6.0) n.toInt() else null
        }.distinct()
        val done = LinkedHashMap<String, Boolean>()
        obj(o["doneDates"])?.entries?.take(366)?.forEach { (k, v) -> if (Web.isYmd(k) && truthy(v)) done[k] = true }
        ScheduleEntry(
            id = str(o["id"], 40).ifEmpty { Web.uid() },
            title = str(o["title"], 120, "Study"),
            days = days.ifEmpty { listOf(jsDayOfWeek()) },
            start = start, end = end, doneDates = done,
            createdAt = long(o["createdAt"], 0, 1_000_000_000_000_000, NOW),
        )
    }

    fun deck(k: JsonElement?): Deck = Deck(
        id = str(k["id"], 40).ifEmpty { Web.uid() },
        title = str(k["title"], 80, "Deck"),
        createdAt = long(k["createdAt"], 0, 1_000_000_000_000_000, NOW),
        cards = arr(k["cards"]).map { c ->
            Flashcard(id = str(c["id"], 40).ifEmpty { Web.uid() }, front = str(c["front"], 300), back = str(c["back"], 300))
        },
    )

    fun quiz(z: JsonElement?): Quiz = Quiz(
        id = str(z["id"], 40).ifEmpty { Web.uid() },
        title = str(z["title"], 80, "Quiz"),
        createdAt = long(z["createdAt"], 0, 1_000_000_000_000_000, NOW),
        questions = arr(z["questions"]).map { q ->
            QuizQuestion(
                q = str(q["q"], 400),
                options = arr(q["options"]).take(4).map { str(it, 160) },
                correct = int(q["correct"], 0, 3, 0),
            )
        }.filter { q -> q.q.isNotEmpty() && q.options.count { it.isNotEmpty() } >= 2 && q.options.getOrNull(q.correct).orEmpty().isNotEmpty() },
    )

    fun link(f: JsonElement?, fallbackTitle: String): SavedLink? {
        val url = Web.safeHttpUrl(rawString(f["url"])) ?: return null
        return SavedLink(
            id = str(f["id"], 40).ifEmpty { Web.uid() },
            title = str(f["title"], 120, fallbackTitle),
            url = url,
            createdAt = long(f["createdAt"], 0, 1_000_000_000_000_000, NOW),
            lastOpened = long(f["lastOpened"], 0, 1_000_000_000_000_000, 0),
            pinned = truthy(f["pinned"]),
        )
    }

    fun focus(f: JsonElement?): FocusSettings {
        val o = obj(f) ?: return FocusSettings()
        return FocusSettings(
            day = str(o["day"], 10),
            done = int(o["done"], 0, 999, 0),
            focusMins = int(o["focusMins"], FocusSettings.MIN_LEN, FocusSettings.MAX_LEN, 25),
            breakMins = int(o["breakMins"], FocusSettings.MIN_LEN, FocusSettings.MAX_LEN, 5),
        )
    }

    /** `normIslam(x)` */
    fun islam(x: JsonElement?): IslamState {
        val o = obj(x) ?: JsonObject(emptyMap())
        val day = rawString(o["day"]).takeIf { Web.isYmd(it) } ?: ""
        val prayers = IslamState.PRAYER_KEYS.associateWith { int(o["prayers"][it], 0, 2, 0) }
        val rawatib = IslamState.RAWATIB_KEYS.associateWith { int(o["rawatib"][it], 0, 1, 0) }
        val hist = LinkedHashMap<String, Int>()
        obj(o["hist"])?.forEach { (k, v) -> if (Web.isYmd(k)) hist[k] = int(v, 0, 5, 0) }
        val oldT = obj(o["tasbih"]) ?: JsonObject(emptyMap())
        val saved = arr(oldT["adhkar"]).mapNotNull { obj(it) }.filter { rawString(it["id"]) != null }
        val adhkar = IslamState.TASBIH_DEFAULTS.map { id ->
            val a = saved.firstOrNull { rawString(it["id"]) == id }
            Dhikr(id = id, text = rawString(a?.get("text"))?.jsTake(120) ?: "", builtin = true)
        }.toMutableList()
        saved.filter { rawString(it["id"]) !in IslamState.TASBIH_DEFAULTS }.take(30).forEach { a ->
            adhkar += Dhikr(id = rawString(a["id"])!!.jsTake(60), text = rawString(a["text"])?.jsTake(120) ?: "", builtin = false)
        }
        val modeCandidate = rawString(oldT["mode"]) ?: "sub"
        val targetRaw = (oldT["target"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        val target = IslamState.TASBIH_TARGETS.firstOrNull { targetRaw != null && it.toDouble() == targetRaw } ?: 33
        return IslamState(
            day = day, prayers = prayers, rawatib = rawatib,
            tasbih = Tasbih(
                mode = if (adhkar.any { it.id == modeCandidate }) modeCandidate else "sub",
                count = long(oldT["count"], 0, 1_000_000, 0),
                total = long(oldT["total"], 0, 1_000_000_000, 0),
                target = target,
                adhkar = adhkar,
            ),
            fasts = arr(o["fasts"]).mapNotNull { rawString(it) }.filter { Web.isYmd(it) }.take(1000),
            hist = hist,
        )
    }

    fun defaultIslam(): IslamState = islam(null)

    // ── misc ───────────────────────────────────────────────────────────

    /** JS `Date.getDay()` for today (0 = Sunday). */
    fun jsDayOfWeek(): Int = java.time.LocalDate.now().dayOfWeek.value % 7

    /** JS String(number): integers without ".0". */
    private fun jsNumberString(d: Double): String =
        if (d % 1.0 == 0.0 && kotlin.math.abs(d) < 1e21) d.toLong().toString() else d.toString()

    /**
     * JS `slice(0,max)` counts UTF-16 code units, which is also what Kotlin's
     * `take` does — but never split a surrogate pair into invalid text.
     */
    private fun String.jsTake(max: Int): String {
        if (length <= max) return this
        var end = max
        if (end > 0 && Character.isHighSurrogate(this[end - 1])) end-- // keep valid UTF-16
        return substring(0, end)
    }
}
