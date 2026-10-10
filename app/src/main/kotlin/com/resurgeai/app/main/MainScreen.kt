package com.resurgeai.app.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.resurgeai.app.BuildConfig
import com.resurgeai.core.designsystem.catalog.DesignCatalog
import com.resurgeai.core.designsystem.components.BottomBar
import com.resurgeai.core.designsystem.components.BottomBarItem
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.model.UserProfile
import com.resurgeai.feature.dashboard.TodayRoute as TodayScreenRoute
import com.resurgeai.feature.dashboard.detail.MetricDetailRoute as MetricDetailScreenRoute
import com.resurgeai.feature.profile.ProfileRoute
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object FoodRoute
@Serializable data object CoachRoute
@Serializable data object ProfileTabRoute
@Serializable data class MetricDetailRoute(val metric: String)
@Serializable data object CatalogRoute

private enum class Tab(val route: Any, val label: String, val icon: Int) {
    Today(TodayRoute, "Today", ResurgeIcons.Home),
    Food(FoodRoute, "Food", ResurgeIcons.Food),
    Coach(CoachRoute, "Coach", ResurgeIcons.Coach),
    Profile(ProfileTabRoute, "Profile", ResurgeIcons.Profile),
}

@Composable
fun MainScreen(@Suppress("UNUSED_PARAMETER") profile: UserProfile) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTab = Tab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(100)) },
            ) {
                composable<TodayRoute> {
                    TodayScreenRoute(
                        onOpenMetric = { metric -> navController.navigate(MetricDetailRoute(metric.name)) },
                    )
                }
                composable<MetricDetailRoute> {
                    MetricDetailScreenRoute(onBack = { navController.popBackStack() })
                }
                composable<FoodRoute> {
                    ComingSoon("Food", "Scan meals, track calories and log water.", "Phase 3")
                }
                composable<CoachRoute> {
                    ComingSoon("Coach", "Chat with an AI coach that knows your data.", "Phase 4")
                }
                composable<ProfileTabRoute> {
                    ProfileRoute(
                        onOpenCatalog = if (BuildConfig.DEBUG) {
                            { navController.navigate(CatalogRoute) }
                        } else null,
                    )
                }
                if (BuildConfig.DEBUG) {
                    composable<CatalogRoute> {
                        Column(Modifier.fillMaxSize()) {
                            TopBar("Design catalog", onBack = { navController.popBackStack() })
                            DesignCatalog(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        // Top-level tabs only; detail screens use the full height.
        if (currentTab != null || destination == null) {
            BottomBar(
                items = Tab.entries.map { BottomBarItem(it, it.label, it.icon) },
                selected = currentTab ?: Tab.Today,
                onSelect = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
