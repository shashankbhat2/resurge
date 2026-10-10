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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Avatar
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MainHeader
import com.resurgeai.core.designsystem.components.MainHeaderHeight
import com.resurgeai.core.designsystem.components.TopShade
import androidx.compose.ui.text.style.TextOverflow
import com.composeunstyled.Text
import java.time.LocalTime
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
@Serializable data object SettingsRoute

// Tab graph, nested inside the shell.
@Serializable data object TodayRoute
@Serializable data object FitnessRoute
@Serializable data object FoodRoute
@Serializable data object AssistantRoute

private enum class Tab(
    val route: Any,
    val label: String,
    val icon: Int,
    val selectedIcon: Int,
    /** Header title; Today has none because its greeting sits right below. */
    val title: String?,
) {
    Today(TodayRoute, "Today", ResurgeIcons.Today, ResurgeIcons.TodayFilled, null),
    Fitness(FitnessRoute, "Fitness", ResurgeIcons.Fitness, ResurgeIcons.FitnessFilled, "Fitness"),
    Food(FoodRoute, "Food", ResurgeIcons.Food, ResurgeIcons.FoodFilled, "Food"),
    Assistant(AssistantRoute, "Assistant", ResurgeIcons.Coach, ResurgeIcons.CoachFilled, "Assistant"),
}

private const val PUSH_MS = 280
private const val POP_MS = 220
private val easing = FastOutSlowInEasing

/**
 * Pushed screens slide in from the right over a static shell; on back they slide out again while
 * the shell underneath stays put. The shell never changes size, so nothing reflows.
 */
private val pushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(PUSH_MS, easing = easing)) { it }
}

// Back (incl. predictive back gesture) slides the screen away; no fade, so nothing "dissolves".
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(POP_MS, easing = easing)) { it }
}

@Composable
fun MainScreen(profile: UserProfile) {
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
                profile = profile,
                onOpenMetric = { metric -> rootNav.navigate(MetricDetailRoute(metric.name)) },
                onOpenSettings = { rootNav.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            FullScreen {
                ProfileRoute(
                    onBack = { rootNav.popBackStack() },
                    onOpenCatalog = if (BuildConfig.DEBUG) {
                        { rootNav.navigate(CatalogRoute) }
                    } else null,
                )
            }
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
    profile: UserProfile,
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val tabsNav = rememberNavController()
    val backStackEntry by tabsNav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    } ?: Tab.Today

    // Header and navbar float over the content; scrolling screens pad with these insets.
    val bottomInset = FloatingNavBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = MainHeaderHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        TopShade()
        CompositionLocalProvider(LocalFloatingBarInset provides bottomInset, LocalHeaderInset provides topInset) {
            NavHost(
                navController = tabsNav,
                startDestination = TodayRoute,
                enterTransition = { fadeIn(tween(180, easing = easing)) },
                exitTransition = { fadeOut(tween(180, easing = easing)) },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable<TodayRoute> { TodayScreenRoute(onOpenMetric = onOpenMetric) }
                composable<FitnessRoute> {
                    ComingSoon("Workouts, training load and activity trends from your wearable.", "a later phase")
                }
                composable<FoodRoute> {
                    ComingSoon("Scan meals, track calories and log water.", "Phase 3")
                }
                composable<AssistantRoute> {
                    ComingSoon("Chat with an AI assistant that knows your health data.", "Phase 4")
                }
            }
        }
        MainHeader(
            title = currentTab.title,
            modifier = Modifier.align(Alignment.TopCenter),
            // Today greets the user in the header, left of the profile photo.
            leading = if (currentTab == Tab.Today) {
                { Text(greeting(profile), style = Resurge.type.brand, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            trailing = {
                UnstyledButton(
                    onClick = onOpenSettings,
                    role = Role.Button,
                    modifier = Modifier
                        .clip(Resurge.shape.pill)
                        .semantics { contentDescription = "Settings" },
                    indication = LocalIndication.current,
                ) {
                    Avatar(profile.displayName, profile.photoUrl, size = 38.dp)
                }
            },
        )
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

private fun greeting(profile: UserProfile, now: LocalTime = LocalTime.now()): String {
    val part = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    return profile.firstName?.let { "$part, $it" } ?: part
}

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
