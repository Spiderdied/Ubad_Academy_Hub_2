package com.ubad.academy.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ubad.academy.domain.model.UserSettings
import com.ubad.academy.ui.components.UbadBackground
import com.ubad.academy.ui.navigation.Route
import com.ubad.academy.ui.navigation.TopLevel
import com.ubad.academy.ui.navigation.UbadNavHost

@Composable
fun UbadApp(settings: UserSettings, navController: NavHostController = rememberNavController()) {
    val activity = LocalContext.current as? ComponentActivity
    // singleTask activity: deep links arriving while running go through onNewIntent.
    DisposableEffect(activity, navController) {
        val listener = Consumer<android.content.Intent> { navController.handleDeepLink(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTop = TopLevel.entries.firstOrNull { top ->
        destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true
    }
    val adaptive = currentWindowAdaptiveInfo()
    val layoutType = if (currentTop == null) NavigationSuiteType.None
    else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptive)

    UbadBackground(userBackground = null) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            containerColor = Color.Transparent,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            navigationSuiteItems = {
                TopLevel.entries.forEach { top ->
                    item(
                        selected = top == currentTop,
                        onClick = { navController.navigateTopLevel(top.route) },
                        icon = { Icon(top.icon, contentDescription = null) },
                        label = { Text(stringResource(top.label), maxLines = 1) },
                    )
                }
            },
        ) {
            UbadNavHost(navController = navController, settings = settings)
        }
    }
}

/** Standard top-level switching: single copy per tab, state saved/restored, Hub is the root. */
fun NavHostController.navigateTopLevel(route: Route) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
