package com.ubad.academy.data.local.db

import com.ubad.academy.domain.model.AssetRef
import com.ubad.academy.domain.model.CalendarEvent
import com.ubad.academy.domain.model.Course
import com.ubad.academy.domain.model.CourseContent
import com.ubad.academy.domain.model.CourseUnit
import com.ubad.academy.domain.model.Deck
import com.ubad.academy.domain.model.Flashcard
import com.ubad.academy.domain.model.LinkKind
import com.ubad.academy.domain.model.Note
import com.ubad.academy.domain.model.NoteAttachment
import com.ubad.academy.domain.model.Quiz
import com.ubad.academy.domain.model.QuizQuestion
import com.ubad.academy.domain.model.SavedLink
import com.ubad.academy.domain.model.ScheduleEntry
import com.ubad.academy.domain.model.Task
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON used for internal columns (tolerant reader). */
val DbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private val assetList = ListSerializer(AssetRef.serializer())
private val stringList = ListSerializer(String.serializer())
private val intList = ListSerializer(Int.serializer())
private val boolMap = MapSerializer(String.serializer(), Boolean.serializer())
private val cardList = ListSerializer(Flashcard.serializer())
private val questionList = ListSerializer(QuizQuestion.serializer())

private inline fun <T> safe(default: T, block: () -> T): T = runCatching(block).getOrDefault(default)

fun assembleCourses(courses: List<CourseEntity>, units: List<UnitEntity>, contents: List<ContentEntity>): List<Course> {
    val byUnit = contents.groupBy { it.unitId }
    val byCourse = units.groupBy { it.courseId }
    return courses.map { c ->
        c.toDomain(byCourse[c.id].orEmpty().map { u -> u.toDomain(byUnit[u.id].orEmpty().map { it.toDomain() }) })
    }
}

fun CourseEntity.toDomain(units: List<CourseUnit>) = Course(id, name, code, instructor, credits, semester, createdAt, units)
fun UnitEntity.toDomain(contents: List<CourseContent>) = CourseUnit(id, title, contents)
fun ContentEntity.toDomain() = CourseContent(
    id = id, type = type, title = title, text = text, assetId = assetId,
    assets = safe(emptyList()) { DbJson.decodeFromString(assetList, assetsJson) },
    name = name, mime = mime, source = source, url = url, done = done, createdAt = createdAt,
)

fun Course.toEntity(position: Int) = CourseEntity(id, name, code, instructor, credits, semester, createdAt, position)
fun CourseUnit.toEntity(courseId: String, position: Int) = UnitEntity(id, courseId, title, position)
fun CourseContent.toEntity(unitId: String, position: Int) = ContentEntity(
    id, unitId, type, title, text, assetId, DbJson.encodeToString(assetList, assets),
    name, mime, source, url, done, createdAt, position,
)

fun NoteEntity.toDomain(att: List<NoteAttachmentEntity>) = Note(
    id = id, title = title, body = body,
    tags = safe(emptyList()) { DbJson.decodeFromString(stringList, tagsJson) },
    pin = pin, createdAt = createdAt, updatedAt = updatedAt,
    images = att.filter { it.kind == "image" }.map { NoteAttachment(it.fileId, it.name, it.mime) },
    audio = att.filter { it.kind == "audio" }.map { NoteAttachment(it.fileId, it.name, it.mime) },
)

fun Note.toEntity() = NoteEntity(id, title, body, DbJson.encodeToString(stringList, tags), pin, createdAt, updatedAt)
fun Note.attachmentEntities(): List<NoteAttachmentEntity> =
    images.mapIndexed { i, a -> NoteAttachmentEntity(a.fileId, id, "image", a.name, a.mime, i) } +
        audio.mapIndexed { i, a -> NoteAttachmentEntity(a.fileId, id, "audio", a.name, a.mime, 1000 + i) }

fun EventEntity.toDomain() = CalendarEvent(id, title, desc, date, time, createdAt)
fun CalendarEvent.toEntity(position: Int) = EventEntity(id, title, desc, date, time, createdAt, position)

fun TaskEntity.toDomain() = Task(id, title, done, due, createdAt)
fun Task.toEntity(position: Int) = TaskEntity(id, title, done, due, createdAt, position)

fun ScheduleEntity.toDomain() = ScheduleEntry(
    id, title, safe(emptyList()) { DbJson.decodeFromString(intList, daysJson) }, start, end,
    safe(emptyMap()) { DbJson.decodeFromString(boolMap, doneDatesJson) }, createdAt,
)
fun ScheduleEntry.toEntity(position: Int) = ScheduleEntity(
    id, title, DbJson.encodeToString(intList, days), start, end, DbJson.encodeToString(boolMap, doneDates), createdAt, position,
)

fun DeckEntity.toDomain() = Deck(id, title, createdAt, safe(emptyList()) { DbJson.decodeFromString(cardList, cardsJson) })
fun Deck.toEntity(position: Int) = DeckEntity(id, title, createdAt, DbJson.encodeToString(cardList, cards), position)

fun QuizEntity.toDomain() = Quiz(id, title, createdAt, safe(emptyList()) { DbJson.decodeFromString(questionList, questionsJson) })
fun Quiz.toEntity(position: Int) = QuizEntity(id, title, createdAt, DbJson.encodeToString(questionList, questions), position)

fun LinkEntity.toDomain() = SavedLink(id, title, url, createdAt, lastOpened, pinned)
fun SavedLink.toEntity(kind: LinkKind, position: Int) = LinkEntity(kind.key, id, title, url, createdAt, lastOpened, pinned, position)
