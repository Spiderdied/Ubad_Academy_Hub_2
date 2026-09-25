package com.ubad.academy.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CourseEntity::class, UnitEntity::class, ContentEntity::class, CourseAssetEntity::class,
        NoteEntity::class, NoteAttachmentEntity::class,
        EventEntity::class, TaskEntity::class, ScheduleEntity::class,
        DeckEntity::class, QuizEntity::class, LinkEntity::class,
        IslamEntity::class, BlogPostEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UbadDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    abstract fun notes(): NoteDao
    abstract fun planner(): PlannerDao
    abstract fun study(): StudyDao
    abstract fun islam(): IslamDao
    abstract fun blog(): BlogDao
}
