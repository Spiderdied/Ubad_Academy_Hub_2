package com.ubad.academy.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mosque
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.ubad.academy.R

/** The eight hub sections, in the web's card order (LAYERS.hub `cards`). */
enum class HubSection(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val route: Route,
) {
    DASHBOARD(Icons.Outlined.GridView, R.string.nav_dashboard, R.string.sub_dashboard, Route.Dashboard),
    COURSES(Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_courses, R.string.sub_courses, Route.Courses),
    NOTES(Icons.Outlined.Description, R.string.nav_notes, R.string.sub_notes, Route.Notes),
    CALENDAR(Icons.Outlined.CalendarMonth, R.string.nav_calendar, R.string.sub_calendar, Route.Calendar()),
    ISLAM(Icons.Outlined.Mosque, R.string.nav_islam, R.string.sub_islam, Route.Islam),
    BLOG(Icons.AutoMirrored.Outlined.Article, R.string.nav_analytics, R.string.sub_analytics, Route.Blog),
    STUDY(Icons.Outlined.Layers, R.string.nav_study, R.string.sub_study, Route.Study()),
    SETTINGS(Icons.Outlined.Tune, R.string.nav_settings, R.string.sub_settings, Route.Settings),
}

/** Bottom bar (phones) / navigation rail (tablets & landscape) destinations. */
enum class TopLevel(val icon: ImageVector, @StringRes val label: Int, val route: Route) {
    HOME(Icons.Outlined.Home, R.string.nav_home_short, Route.Hub),
    DASHBOARD(Icons.Outlined.GridView, R.string.nav_dashboard, Route.Dashboard),
    COURSES(Icons.AutoMirrored.Outlined.MenuBook, R.string.nav_courses, Route.Courses),
    STUDY(Icons.Outlined.Layers, R.string.nav_study, Route.Study()),
    ISLAM(Icons.Outlined.Mosque, R.string.nav_islam, Route.Islam),
}
