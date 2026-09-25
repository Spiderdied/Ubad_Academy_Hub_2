package com.ubad.academy.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY position") fun observeCourses(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM units ORDER BY position") fun observeUnits(): Flow<List<UnitEntity>>
    @Query("SELECT * FROM contents ORDER BY position") fun observeContents(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM courses ORDER BY position") suspend fun courses(): List<CourseEntity>
    @Query("SELECT * FROM units ORDER BY position") suspend fun units(): List<UnitEntity>
    @Query("SELECT * FROM contents ORDER BY position") suspend fun contents(): List<ContentEntity>

    @Query("SELECT * FROM courses WHERE id = :id") suspend fun course(id: String): CourseEntity?
    @Query("SELECT * FROM units WHERE id = :id") suspend fun unit(id: String): UnitEntity?
    @Query("SELECT * FROM contents WHERE id = :id") suspend fun content(id: String): ContentEntity?
    @Query("SELECT * FROM units WHERE courseId = :courseId ORDER BY position") suspend fun unitsOf(courseId: String): List<UnitEntity>
    @Query("SELECT * FROM contents WHERE unitId = :unitId ORDER BY position") suspend fun contentsOf(unitId: String): List<ContentEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM courses") suspend fun nextCoursePos(): Int
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM units WHERE courseId = :courseId") suspend fun nextUnitPos(courseId: String): Int
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM contents WHERE unitId = :unitId") suspend fun nextContentPos(unitId: String): Int

    @Upsert suspend fun upsertCourse(c: CourseEntity)
    @Upsert suspend fun upsertUnit(u: UnitEntity)
    @Upsert suspend fun upsertContent(c: ContentEntity)
    @Upsert suspend fun upsertUnits(u: List<UnitEntity>)
    @Upsert suspend fun upsertContents(c: List<ContentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCourses(c: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE id = :id") suspend fun deleteCourse(id: String)
    @Query("DELETE FROM units WHERE id = :id") suspend fun deleteUnit(id: String)
    @Query("DELETE FROM contents WHERE id = :id") suspend fun deleteContent(id: String)
    @Query("DELETE FROM courses") suspend fun clearCourses()

    @Query("UPDATE contents SET done = :done WHERE id = :id") suspend fun setDone(id: String, done: Boolean)

    @Query("SELECT * FROM course_assets") suspend fun assets(): List<CourseAssetEntity>
    @Query("SELECT * FROM course_assets WHERE id = :id") suspend fun asset(id: String): CourseAssetEntity?
    @Upsert suspend fun upsertAsset(a: CourseAssetEntity)
    @Query("DELETE FROM course_assets WHERE id = :id") suspend fun deleteAsset(id: String)
    @Query("DELETE FROM course_assets") suspend fun clearAssets()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pin DESC, updatedAt DESC") fun observeNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM note_attachments ORDER BY position") fun observeAttachments(): Flow<List<NoteAttachmentEntity>>
    @Query("SELECT * FROM notes ORDER BY pin DESC, updatedAt DESC") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM note_attachments ORDER BY position") suspend fun attachments(): List<NoteAttachmentEntity>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun note(id: String): NoteEntity?
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY position") suspend fun attachmentsOf(noteId: String): List<NoteAttachmentEntity>

    @Upsert suspend fun upsertNote(n: NoteEntity)
    @Upsert suspend fun upsertAttachments(a: List<NoteAttachmentEntity>)
    @Query("DELETE FROM note_attachments WHERE noteId = :noteId") suspend fun clearAttachmentsOf(noteId: String)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("UPDATE notes SET pin = :pin WHERE id = :id") suspend fun setPin(id: String, pin: Boolean)

    @Transaction
    suspend fun replaceNote(n: NoteEntity, attachments: List<NoteAttachmentEntity>) {
        upsertNote(n)
        clearAttachmentsOf(n.id)
        upsertAttachments(attachments)
    }
}

@Dao
interface PlannerDao {
    @Query("SELECT * FROM events ORDER BY position") fun observeEvents(): Flow<List<EventEntity>>
    @Query("SELECT * FROM events ORDER BY position") suspend fun events(): List<EventEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM events") suspend fun nextEventPos(): Int
    @Upsert suspend fun upsertEvent(e: EventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEvents(e: List<EventEntity>)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("DELETE FROM events") suspend fun clearEvents()

    @Query("SELECT * FROM tasks ORDER BY position") fun observeTasks(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks ORDER BY position") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tasks") suspend fun nextTaskPos(): Int
    /** Web quick-add uses `tasks.unshift(...)` → new tasks go to the front. */
    @Query("SELECT COALESCE(MIN(position), 1) - 1 FROM tasks") suspend fun firstTaskPos(): Int
    @Query("UPDATE tasks SET done = NOT done WHERE id = :id") suspend fun toggleTask(id: String)
    @Upsert suspend fun upsertTask(t: TaskEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTasks(t: List<TaskEntity>)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: String)
    @Query("DELETE FROM tasks") suspend fun clearTasks()

    @Query("SELECT * FROM schedule ORDER BY position") fun observeSchedule(): Flow<List<ScheduleEntity>>
    @Query("SELECT * FROM schedule ORDER BY position") suspend fun schedule(): List<ScheduleEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM schedule") suspend fun nextSchedulePos(): Int
    /** Web `state.schedule.unshift(draft)`: new sessions go first. */
    @Query("SELECT COALESCE(MIN(position), 1) - 1 FROM schedule") suspend fun firstSchedulePos(): Int
    @Upsert suspend fun upsertSchedule(s: ScheduleEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSchedule(s: List<ScheduleEntity>)
    @Query("DELETE FROM schedule WHERE id = :id") suspend fun deleteSchedule(id: String)
    @Query("DELETE FROM schedule") suspend fun clearSchedule()
}

@Dao
interface StudyDao {
    @Query("SELECT * FROM decks ORDER BY position") fun observeDecks(): Flow<List<DeckEntity>>
    @Query("SELECT * FROM decks ORDER BY position") suspend fun decks(): List<DeckEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM decks") suspend fun nextDeckPos(): Int
    @Upsert suspend fun upsertDeck(d: DeckEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDecks(d: List<DeckEntity>)
    @Query("DELETE FROM decks WHERE id = :id") suspend fun deleteDeck(id: String)
    @Query("DELETE FROM decks") suspend fun clearDecks()

    @Query("SELECT * FROM quizzes ORDER BY position") fun observeQuizzes(): Flow<List<QuizEntity>>
    @Query("SELECT * FROM quizzes ORDER BY position") suspend fun quizzes(): List<QuizEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM quizzes") suspend fun nextQuizPos(): Int
    @Upsert suspend fun upsertQuiz(q: QuizEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQuizzes(q: List<QuizEntity>)
    @Query("DELETE FROM quizzes WHERE id = :id") suspend fun deleteQuiz(id: String)
    @Query("DELETE FROM quizzes") suspend fun clearQuizzes()

    @Query("SELECT * FROM links WHERE kind = :kind ORDER BY position") fun observeLinks(kind: String): Flow<List<LinkEntity>>
    @Query("SELECT * FROM links WHERE kind = :kind ORDER BY position") suspend fun links(kind: String): List<LinkEntity>
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM links WHERE kind = :kind") suspend fun nextLinkPos(kind: String): Int
    @Upsert suspend fun upsertLink(l: LinkEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLinks(l: List<LinkEntity>)
    @Query("DELETE FROM links WHERE kind = :kind AND id = :id") suspend fun deleteLink(kind: String, id: String)
    @Query("DELETE FROM links WHERE kind = :kind") suspend fun clearLinks(kind: String)
}

@Dao
interface IslamDao {
    @Query("SELECT json FROM islam_state WHERE `key` = 0") fun observe(): Flow<String?>
    @Query("SELECT json FROM islam_state WHERE `key` = 0") suspend fun get(): String?
    @Upsert suspend fun put(e: IslamEntity)
    @Query("DELETE FROM islam_state") suspend fun clear()
}

@Dao
interface BlogDao {
    @Query("SELECT * FROM blog_posts ORDER BY position") fun observe(): Flow<List<BlogPostEntity>>
    @Query("SELECT * FROM blog_posts WHERE id = :id") suspend fun post(id: String): BlogPostEntity?
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM blog_posts") suspend fun nextPos(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(p: List<BlogPostEntity>)
    @Query("DELETE FROM blog_posts") suspend fun clear()

    @Transaction
    suspend fun replaceAll(p: List<BlogPostEntity>) { clear(); insert(p) }
}
