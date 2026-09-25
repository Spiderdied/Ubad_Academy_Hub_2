package com.ubad.academy.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe destinations. One per web LAYER (plus native-only viewers). */
sealed interface Route {
    @Serializable data object Hub : Route
    @Serializable data object Dashboard : Route
    @Serializable data object Courses : Route
    @Serializable data class CourseDetail(val id: String) : Route
    @Serializable data class UnitDetail(val courseId: String, val unitId: String) : Route
    @Serializable data class PdfViewer(val assetId: String, val title: String) : Route
    @Serializable data class ImageViewer(val contentId: String, val index: Int = 0) : Route
    @Serializable data class NoteImageViewer(val noteId: String, val index: Int = 0) : Route
    @Serializable data class MediaPlayer(val assetId: String, val title: String, val video: Boolean) : Route
    @Serializable data object Notes : Route
    @Serializable data class NoteEditor(val id: String? = null) : Route
    @Serializable data class Calendar(val date: String? = null) : Route
    @Serializable data object Islam : Route
    @Serializable data object Blog : Route
    @Serializable data class BlogPost(val id: String) : Route
    @Serializable data class Study(val tab: String? = null) : Route
    @Serializable data class Deck(val id: String) : Route
    @Serializable data class DeckTest(val id: String) : Route
    @Serializable data class QuizEdit(val id: String? = null) : Route
    @Serializable data class QuizPlay(val id: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object Search : Route
}

object DeepLinks {
    const val SCHEME = "ubadacademy"
    const val BASE = "$SCHEME://"
}
