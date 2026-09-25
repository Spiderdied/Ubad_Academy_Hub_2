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
import com.ubad.academy.domain.model.UserSettings
import com.ubad.academy.ui.screens.PendingScreen
import com.ubad.academy.ui.screens.hub.HubScreen

@Composable
fun UbadNavHost(navController: NavHostController, settings: UserSettings) {
    val back: () -> Unit = { navController.navigateUp() }
    val go: (Route) -> Unit = { navController.navigate(it) }

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
        composable<Route.Dashboard> { PendingScreen("Dashboard", back) }
        composable<Route.Courses> { PendingScreen("Courses", back) }
        composable<Route.CourseDetail>(
            deepLinks = listOf(navDeepLink<Route.CourseDetail>(basePath = "${DeepLinks.BASE}course")),
        ) { PendingScreen("Course", back) }
        composable<Route.UnitDetail>(
            deepLinks = listOf(navDeepLink<Route.UnitDetail>(basePath = "${DeepLinks.BASE}unit")),
        ) { PendingScreen("Unit", back) }
        composable<Route.Notes> { PendingScreen("Notes", back) }
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
