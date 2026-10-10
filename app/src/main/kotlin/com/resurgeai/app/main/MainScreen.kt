package com.resurgeai.app.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resurgeai.app.BuildConfig
import com.resurgeai.core.designsystem.catalog.DesignCatalog
import com.resurgeai.core.designsystem.components.FloatingNavBar
import com.resurgeai.core.designsystem.components.FloatingNavBarHeight
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.NavItem
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.UserProfile
import com.resurgeai.feature.dashboard.TodayRoute as TodayScreenRoute
import com.resurgeai.feature.dashboard.detail.MetricDetailRoute as MetricDetailScreenRoute
import com.resurgeai.feature.dashboard.sources.MetricSourcesRoute as MetricSourcesScreenRoute
import com.resurgeai.feature.profile.ProfileRoute
import kotlinx.serialization.Serializable

// Root graph: the tab shell plus full-screen destinations that cover it (incl. the bottom bar).
@Serializable data object TabsRoute
@Serializable data class MetricDetailRoute(val metric: String)
@Serializable data class MetricSourcesRoute(val metric: String)
@Serializable data object CatalogRoute

// Tab graph, nested inside the shell.
@Serializable data object TodayRoute
@Serializable data object FoodRoute
@Serializable data object CoachRoute
@Serializable data object ProfileTabRoute

private enum class Tab(val route: Any, val label: String, val icon: Int, val selectedIcon: Int) {
    Today(TodayRoute, "Today", ResurgeIcons.Today, ResurgeIcons.TodayFilled),
    Food(FoodRoute, "Food", ResurgeIcons.Food, ResurgeIcons.FoodFilled),
    Coach(CoachRoute, "Coach", ResurgeIcons.Coach, ResurgeIcons.CoachFilled),
    Profile(ProfileTabRoute, "Profile", ResurgeIcons.Profile, ResurgeIcons.ProfileFilled),
}

private const val PUSH_MS = 280
private const val POP_MS = 220
private val easing = FastOutSlowInEasing

/**
 * Pushed screens slide in a quarter-width over a static shell; on back they slide/fade away while
 * the shell underneath is already in place. The shell never changes size, so nothing reflows.
 */
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(PUSH_MS, easing = easing)) { it / 4 } + fadeIn(tween(PUSH_MS, easing = easing))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(POP_MS, easing = easing)) { it / 4 } + fadeOut(tween(POP_MS, easing = easing))
}

@Composable
fun MainScreen(@Suppress("UNUSED_PARAMETER") profile: UserProfile) {
    val rootNav = rememberNavController()
    NavHost(
        navController = rootNav,
        startDestination = TabsRoute,
        enterTransition = pushEnter,
        // The covered screen stays put (and visible) underneath while the new one animates in/out.
        exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = popExit,
    ) {
        composable<TabsRoute> {
            TabsShell(
                onOpenMetric = { metric -> rootNav.navigate(MetricDetailRoute(metric.name)) },
                onOpenCatalog = if (BuildConfig.DEBUG) {
                    { rootNav.navigate(CatalogRoute) }
                } else null,
            )
        }
        composable<MetricDetailRoute> {
            FullScreen {
                MetricDetailScreenRoute(
                    onBack = { rootNav.popBackStack() },
                    onOpenSources = { metric -> rootNav.navigate(MetricSourcesRoute(metric.name)) },
                )
            }
        }
        composable<MetricSourcesRoute> {
            FullScreen { MetricSourcesScreenRoute(onBack = { rootNav.popBackStack() }) }
        }
        if (BuildConfig.DEBUG) {
            composable<CatalogRoute> {
                FullScreen {
                    Column(Modifier.fillMaxSize()) {
                        TopBar("Design catalog", onBack = { rootNav.popBackStack() })
                        DesignCatalog(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Opaque background so a pushed screen fully covers the shell during transitions. */
@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Resurge.colors.bg),
    ) { content() }
}

@Composable
private fun TabsShell(
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenCatalog: (() -> Unit)?,
) {
    val tabsNav = rememberNavController()
    val backStackEntry by tabsNav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    } ?: Tab.Today

    // The bar floats over the content; scrolling screens pad their end with LocalFloatingBarInset.
    val inset = FloatingNavBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFloatingBarInset provides inset) {
            NavHost(
                navController = tabsNav,
                startDestination = TodayRoute,
                enterTransition = { fadeIn(tween(180, easing = easing)) },
                exitTransition = { fadeOut(tween(180, easing = easing)) },
            ) {
                composable<TodayRoute> { TodayScreenRoute(onOpenMetric = onOpenMetric) }
                composable<FoodRoute> {
                    ComingSoon("Food", "Scan meals, track calories and log water.", "Phase 3")
                }
                composable<CoachRoute> {
                    ComingSoon("Coach", "Chat with an AI coach that knows your data.", "Phase 4")
                }
                composable<ProfileTabRoute> { ProfileRoute(onOpenCatalog = onOpenCatalog) }
            }
        }
        FloatingNavBar(
            items = Tab.entries.map { NavItem(it, it.label, it.icon, it.selectedIcon) },
            selected = currentTab,
            onSelect = { tab -> tabsNav.switchTab(tab.route) },
            // Food scanning lands in Phase 3; until then the action opens the Food tab.
            action = NavItem(Unit, "Scan food", ResurgeIcons.Scan),
            onAction = { tabsNav.switchTab(FoodRoute) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
