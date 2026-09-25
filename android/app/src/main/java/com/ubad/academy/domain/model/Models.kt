package com.ubad.academy.domain.model

import com.ubad.academy.core.JsNumberSerializer
import kotlinx.serialization.Serializable

/*
 * Domain models. Field names intentionally match the web app's normalized JSON
 * (appdata / backup v2), so encoding one of these yields exactly what app.js writes.
 */

@Serializable
data class Course(
    val id: String,
    val name: String,
    val code: String = "",
    val instructor: String = "",
    @Serializable(with = JsNumberSerializer::class) val credits: Double = 3.0,
    val semester: String = "",
    val createdAt: Long,
    val units: List<CourseUnit> = emptyList(),
) {
    val contents: List<CourseContent> get() = units.flatMap { it.contents }
    val progress: Progress get() = Progress.of(contents)
}

@Serializable
data class CourseUnit(
    val id: String,
    val title: String,
    val contents: List<CourseContent> = emptyList(),
) {
    val progress: Progress get() = Progress.of(contents)
}

enum class ContentType(val key: String) {
    TEXT("text"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), PDF("pdf");
    companion object { fun from(k: String?) = entries.firstOrNull { it.key == k } ?: TEXT }
}

@Serializable
data class AssetRef(val id: String, val name: String = "image", val mime: String = "")

@Serializable
data class CourseContent(
    val id: String,
    val type: String,
    val title: String,
    val text: String = "",
    val assetId: String = "",
    val assets: List<AssetRef> = emptyList(),
    val name: String = "",
    val mime: String = "",
    /** '' | 'youtube' | 'local' — only meaningful for video. */
    val source: String = "",
    val url: String = "",
    val done: Boolean = false,
    val createdAt: Long,
) {
    val contentType: ContentType get() = ContentType.from(type)
    val isYoutube: Boolean get() = contentType == ContentType.VIDEO && source == "youtube"
    /** Every stored file this item references (images use `assets`, others `assetId`). */
    val allAssetIds: List<String> get() = (assets.map { it.id } + assetId).filter { it.isNotEmpty() }.distinct()
}

data class Progress(val done: Int, val total: Int) {
    val pct: Int get() = if (total == 0) 0 else Math.round(done * 100f / total)
    companion object { fun of(l: List<CourseContent>) = Progress(l.count { it.done }, l.size) }
}

data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val pin: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val images: List<NoteAttachment> = emptyList(),
    val audio: List<NoteAttachment> = emptyList(),
)

/** A binary note attachment stored in private storage (web: `{name, blob}`). */
data class NoteAttachment(val fileId: String, val name: String, val mime: String)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val desc: String = "",
    val date: String,
    val time: String = "",
    val createdAt: Long,
)

@Serializable
data class Task(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val due: String = "",
    val createdAt: Long,
)

@Serializable
data class ScheduleEntry(
    val id: String,
    val title: String,
    /** JS Date.getDay(): 0 = Sunday … 6 = Saturday. */
    val days: List<Int>,
    val start: String,
    val end: String,
    val doneDates: Map<String, Boolean> = emptyMap(),
    val createdAt: Long,
)

@Serializable
data class Deck(
    val id: String,
    val title: String,
    val createdAt: Long,
    val cards: List<Flashcard> = emptyList(),
)

@Serializable
data class Flashcard(val id: String, val front: String = "", val back: String = "")

@Serializable
data class Quiz(
    val id: String,
    val title: String,
    val createdAt: Long,
    val questions: List<QuizQuestion> = emptyList(),
)

@Serializable
data class QuizQuestion(val q: String, val options: List<String>, val correct: Int = 0)

enum class LinkKind(val key: String) { FORMS("forms"), SUMMARIES("summaries") }

/** A saved Google Form test or Summary link. */
@Serializable
data class SavedLink(
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long,
    val lastOpened: Long = 0,
    val pinned: Boolean = false,
)

@Serializable
data class Dhikr(val id: String, val text: String = "", val builtin: Boolean)

@Serializable
data class Tasbih(
    val mode: String = "sub",
    val count: Long = 0,
    val total: Long = 0,
    val target: Int = 33,
    val adhkar: List<Dhikr> = emptyList(),
)

@Serializable
data class IslamState(
    val day: String = "",
    val prayers: Map<String, Int> = emptyMap(),
    val rawatib: Map<String, Int> = emptyMap(),
    val tasbih: Tasbih = Tasbih(),
    val fasts: List<String> = emptyList(),
    val hist: Map<String, Int> = emptyMap(),
) {
    companion object {
        val PRAYER_KEYS = listOf("fajr", "zuhr", "asr", "maghrib", "isha")
        val RAWATIB_KEYS = listOf("pf", "duha", "bz", "az", "am", "ai", "qiyam", "shaf", "witr")
        val TASBIH_DEFAULTS = listOf("sub", "ham", "akb", "ist", "saw")
        val TASBIH_TARGETS = listOf(33, 100, 1000)
    }
}

data class BlogPost(
    val id: String,
    val title: String,
    val content: String,
    val published: String?,
    val updated: String?,
    val url: String?,
    val imageUrl: String?,
)
