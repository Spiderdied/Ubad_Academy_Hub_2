package com.ubad.academy.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Structured storage. Ordered collections keep a `position` column so the
 * web's array order (which the UI and backups depend on) is preserved.
 * Small nested value lists (tags, cards, questions, days…) are JSON columns:
 * they are always read/written as a whole, exactly like the web objects.
 */

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val instructor: String,
    val credits: Double,
    val semester: String,
    val createdAt: Long,
    val position: Int,
)

@Entity(
    tableName = "units",
    foreignKeys = [ForeignKey(CourseEntity::class, ["id"], ["courseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("courseId")],
)
data class UnitEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "contents",
    foreignKeys = [ForeignKey(UnitEntity::class, ["id"], ["unitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("unitId")],
)
data class ContentEntity(
    @PrimaryKey val id: String,
    val unitId: String,
    val type: String,
    val title: String,
    val text: String,
    val assetId: String,
    val assetsJson: String,
    val name: String,
    val mime: String,
    val source: String,
    val url: String,
    val done: Boolean,
    val createdAt: Long,
    val position: Int,
)

/** Metadata of a course file; bytes live in filesDir/course_assets. */
@Entity(tableName = "course_assets")
data class CourseAssetEntity(
    @PrimaryKey val id: String,
    val mime: String,
    val size: Long,
)

@Entity(tableName = "notes", indices = [Index("updatedAt")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val tagsJson: String,
    val pin: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "note_attachments",
    foreignKeys = [ForeignKey(NoteEntity::class, ["id"], ["noteId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("noteId")],
)
data class NoteAttachmentEntity(
    @PrimaryKey val fileId: String,
    val noteId: String,
    /** "image" | "audio" */
    val kind: String,
    val name: String,
    val mime: String,
    val position: Int,
)

@Entity(tableName = "events", indices = [Index("date")])
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val desc: String,
    val date: String,
    val time: String,
    val createdAt: Long,
    val position: Int,
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val done: Boolean,
    val due: String,
    val createdAt: Long,
    val position: Int,
)

@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val daysJson: String,
    val start: String,
    val end: String,
    val doneDatesJson: String,
    val createdAt: Long,
    val position: Int,
)

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val cardsJson: String,
    val position: Int,
)

@Entity(tableName = "quizzes")
data class QuizEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val questionsJson: String,
    val position: Int,
)

/** Google Form tests ("forms") and Summary links ("summaries"). */
@Entity(tableName = "links", primaryKeys = ["kind", "id"])
data class LinkEntity(
    val kind: String,
    val id: String,
    val title: String,
    val url: String,
    val createdAt: Long,
    val lastOpened: Long,
    val pinned: Boolean,
    val position: Int,
)

/** The whole Islam tracker object (single row — it's one nested document on the web). */
@Entity(tableName = "islam_state")
data class IslamEntity(
    @PrimaryKey val key: Int = 0,
    val json: String,
)

/** Offline cache of the last fetched blog page(s). */
@Entity(tableName = "blog_posts")
data class BlogPostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val published: String?,
    val updated: String?,
    val url: String?,
    val imageUrl: String?,
    val position: Int,
)
