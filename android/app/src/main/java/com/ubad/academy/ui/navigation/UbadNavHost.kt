package com.ubad.academy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.ubad.academy.ui.screens.viewer.GallerySource
import com.ubad.academy.ui.screens.viewer.ImageViewerScreen
import com.ubad.academy.ui.screens.viewer.MediaPlayerScreen
import com.ubad.academy.ui.screens.viewer.PdfViewerScreen
import com.ubad.academy.domain.model.UserSettings
import com.ubad.academy.ui.navigateTopLevel
import com.ubad.academy.ui.screens.PendingScreen
import com.ubad.academy.ui.screens.hub.HubScreen
import com.ubad.academy.ui.screens.dashboard.DashboardScreen
import com.ubad.academy.ui.screens.courses.CourseDetailScreen
import com.ubad.academy.ui.screens.courses.CoursesScreen
import com.ubad.academy.ui.screens.courses.UnitScreen

@Composable
fun UbadNavHost(navController: NavHostController, settings: UserSettings) {
    val back: () -> Unit = { navController.navigateUp() }
    // Top-level sections (bottom bar / rail) switch tabs; everything else pushes a layer.
    val go: (Route) -> Unit = { r ->
        if (TopLevel.entries.any { it.route::class == r::class }) navController.navigateTopLevel(r) else navController.navigate(r)
    }

    NavHost(
        navController = navController,
        startDestination = Route.Hub,
        // Native take on the web's "spatial depth" transitions: entering layers zoom in
        // from slightly smaller, leaving layers recede.
        enterTransition = { fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.94f) },
        exitTransition = { fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 1.04f) },
        popEnterTransition = { fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.04f) },
        popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 0.94f) },
    ) {
        composable<Route.Hub> { HubScreen(onNavigate = go) }
        composable<Route.Dashboard> { DashboardScreen(onNavigate = go, onBack = null) }
        composable<Route.Courses> { CoursesScreen(onNavigate = go) }
        composable<Route.CourseDetail>(
            deepLinks = listOf(navDeepLink<Route.CourseDetail>(basePath = "${DeepLinks.BASE}course")),
        ) { CourseDetailScreen(onNavigate = go, onBack = back) }
        composable<Route.UnitDetail>(
            deepLinks = listOf(navDeepLink<Route.UnitDetail>(basePath = "${DeepLinks.BASE}unit")),
        ) { UnitScreen(onNavigate = go, onBack = back) }
        composable<Route.PdfViewer> { PdfViewerScreen(onBack = back) }
        composable<Route.MediaPlayer> { MediaPlayerScreen(onBack = back) }
        composable<Route.ImageViewer> { e ->
            val r = e.toRoute<Route.ImageViewer>()
            ImageViewerScreen(GallerySource.CourseContent(r.contentId), r.index, onBack = back)
        }
        composable<Route.NoteImageViewer> { e ->
            val r = e.toRoute<Route.NoteImageViewer>()
            ImageViewerScreen(GallerySource.Note(r.noteId), r.index, onBack = back)
        }
        composable<Route.Notes> { PendingScreen("Notes", back) }
        composable<Route.NoteEditor> { PendingScreen("Note", back) }
        composable<Route.Calendar> { PendingScreen("Calendar", back) }
        composable<Route.Islam> { PendingScreen("Islam", back) }
        composable<Route.Blog> { PendingScreen("Blog", back) }
        composable<Route.BlogPost>(
            deepLinks = listOf(navDeepLink<Route.BlogPost>(basePath = "${DeepLinks.BASE}blog")),
        ) { PendingScreen("Post", back) }
        composable<Route.Study>(
            deepLinks = listOf(navDeepLink<Route.Study>(basePath = "${DeepLinks.BASE}study")),
        ) { PendingScreen("Study", back) }
        composable<Route.Settings> { PendingScreen("Settings", back) }
        composable<Route.Search> { PendingScreen("Search", back) }
    }
}

@Suppress("unused")
private typealias Transitions = AnimatedContentTransitionScope<*>
