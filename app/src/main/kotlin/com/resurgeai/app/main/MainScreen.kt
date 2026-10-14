package com.resurgeai.app.main

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.resurgeai.core.designsystem.components.NavCoach
import com.resurgeai.core.model.CoachStyle
import androidx.compose.foundation.layout.size
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MainHeader
import com.resurgeai.core.designsystem.components.MainHeaderHeight
import androidx.compose.ui.text.style.TextOverflow
import com.composeunstyled.Text
import java.time.LocalTime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.resurgeai.feature.coach.CoachRoute as CoachScreen
import com.resurgeai.feature.coach.InsightsRoute as InsightsScreen
import com.resurgeai.feature.habits.HabitDetailRoute as HabitDetailScreen
import com.resurgeai.feature.habits.HabitEditorRoute as HabitEditorScreen
import com.resurgeai.feature.habits.HabitsRoute as HabitsScreenRoute
import com.resurgeai.feature.profile.SettingsScreen
import com.resurgeai.core.designsystem.components.IconButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resurgeai.feature.fitness.ActivityPickerRoute as ActivityPickerScreen
import com.resurgeai.feature.fitness.FitnessRoute as FitnessScreenRoute
import com.resurgeai.feature.fitness.WorkoutEditorRoute as WorkoutEditorScreen
import com.resurgeai.feature.food.FoodLogRoute as FoodLogScreen
import com.resurgeai.feature.food.FoodRoute as FoodScreenRoute
import com.resurgeai.feature.food.MealReviewRoute as MealReviewScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

// Root graph: the tab shell plus full-screen destinations that cover it (incl. the bottom bar).
@Serializable data object TabsRoute
@Serializable data class MetricDetailRoute(val metric: String)
@Serializable data class MetricSourcesRoute(val metric: String)
@Serializable data object CatalogRoute

@Serializable data class HabitDetailRoute(val id: String)

/** Add a habit (id = null) or edit one. */
@Serializable data class HabitEditorRoute(val id: String? = null)

/** Full-screen coach chat; [newChat] opens on an empty conversation instead of the latest one. */
@Serializable data class CoachScreenRoute(val newChat: Boolean = false)

/** Activity catalog search; [forResult] returns the pick to the workout editor instead of opening it. */
@Serializable data class ActivityPickerRoute(val forResult: Boolean = false)

/** Log a workout ([activityId]) or edit one ([workoutId]). */
@Serializable data class WorkoutEditorRoute(val workoutId: String? = null, val activityId: String? = null)

/** Scan / photo / type / log again. [mode] "scan" opens the camera, "type" focuses the text field. */
@Serializable data class FoodLogRoute(val mode: String? = null)

/** Review a fresh estimate (mealId null) or edit a logged meal. */
@Serializable data class MealReviewRoute(val mealId: String? = null)

/** Settings, opened from the profile photo. */
@Serializable data object SettingsRoute

/** Standalone list of past insights. */
@Serializable data object InsightsScreenRoute

// Tab graph, nested inside the shell.
@Serializable data object TodayRoute
@Serializable data object FitnessRoute
@Serializable data object FoodRoute
@Serializable data object HabitsRoute

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
    Habits(HabitsRoute, "Habits", ResurgeIcons.Habits, ResurgeIcons.Habits, "Habits"),
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
fun MainScreen(profile: UserProfile, openInsightsSignal: Int = 0, openHabitsSignal: Int = 0, openFoodSignal: FoodSignal = FoodSignal()) {
    val rootNav = rememberNavController()
    // A tapped insight notification opens Coach on the Insights tab.
    LaunchedEffect(openInsightsSignal) {
        if (openInsightsSignal > 0) rootNav.navigate(InsightsScreenRoute) { launchSingleTop = true }
    }
    // Meal reminder actions: Scan / Type open the log screen in that mode.
    LaunchedEffect(openFoodSignal.count) {
        val mode = openFoodSignal.mode
        if (openFoodSignal.count > 0 && mode != null) {
            rootNav.popBackStack(TabsRoute, inclusive = false)
            rootNav.navigate(FoodLogRoute(mode))
        }
    }
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
                onOpenCoach = { rootNav.navigate(CoachScreenRoute(newChat = true)) },
                onOpenInsights = { rootNav.navigate(InsightsScreenRoute) },
                onOpenHabit = { id -> rootNav.navigate(HabitDetailRoute(id)) },
                onAddHabit = { rootNav.navigate(HabitEditorRoute()) },
                onLogWorkout = { rootNav.navigate(ActivityPickerRoute()) },
                onOpenWorkout = { id -> rootNav.navigate(WorkoutEditorRoute(workoutId = id)) },
                onLogMeal = { rootNav.navigate(FoodLogRoute()) },
                onScanMeal = { rootNav.navigate(FoodLogRoute(mode = "scan")) },
                onOpenMeal = { id -> rootNav.navigate(MealReviewRoute(mealId = id)) },
                openFoodTabSignal = if (openFoodSignal.mode == null) openFoodSignal.count else 0,
                openHabitsSignal = openHabitsSignal,
                onReturnToTabs = { rootNav.popBackStack(TabsRoute, inclusive = false) },
            )
        }
        composable<HabitDetailRoute> { entry ->
            val id = entry.toRoute<HabitDetailRoute>().id
            FullScreen {
                HabitDetailScreen(
                    habitId = id,
                    onBack = { rootNav.popBackStack() },
                    onEdit = { rootNav.navigate(HabitEditorRoute(it)) },
                )
            }
        }
        composable<HabitEditorRoute> { entry ->
            FullScreen { HabitEditorScreen(habitId = entry.toRoute<HabitEditorRoute>().id, onDone = { rootNav.popBackStack() }) }
        }
        composable<CoachScreenRoute> {
            FullScreen { CoachScreen(onBack = { rootNav.popBackStack() }) }
        }
        composable<ActivityPickerRoute> { entry ->
            val forResult = entry.toRoute<ActivityPickerRoute>().forResult
            FullScreen {
                ActivityPickerScreen(
                    onPick = { id ->
                        if (forResult) {
                            rootNav.previousBackStackEntry?.savedStateHandle?.set(PICKED_ACTIVITY, id)
                            rootNav.popBackStack()
                        } else {
                            rootNav.navigate(WorkoutEditorRoute(activityId = id)) {
                                popUpTo<ActivityPickerRoute> { inclusive = true }
                            }
                        }
                    },
                    onBack = { rootNav.popBackStack() },
                )
            }
        }
        composable<WorkoutEditorRoute> { entry ->
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_ACTIVITY, null).collectAsStateWithLifecycle()
            FullScreen {
                WorkoutEditorScreen(
                    onDone = { rootNav.popBackStack() },
                    onChangeActivity = { rootNav.navigate(ActivityPickerRoute(forResult = true)) },
                    pickedActivity = picked,
                    onPickedActivityConsumed = { entry.savedStateHandle[PICKED_ACTIVITY] = null },
                )
            }
        }
        composable<FoodLogRoute> { entry ->
            FullScreen {
                FoodLogScreen(
                    mode = entry.toRoute<FoodLogRoute>().mode,
                    onReview = {
                        rootNav.navigate(MealReviewRoute()) { popUpTo<FoodLogRoute> { inclusive = true } }
                    },
                    onBack = { rootNav.popBackStack() },
                )
            }
        }
        composable<MealReviewRoute> {
            FullScreen { MealReviewScreen(onDone = { rootNav.popBackStack() }) }
        }
        composable<SettingsRoute> {
            FullScreen {
                SettingsScreen(
                    onBack = { rootNav.popBackStack() },
                    onOpenCatalog = if (BuildConfig.DEBUG) {
                        { rootNav.navigate(CatalogRoute) }
                    } else null,
                )
            }
        }
        composable<InsightsScreenRoute> {
            FullScreen { InsightsScreen(onBack = { rootNav.popBackStack() }) }
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
    onOpenCoach: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
    onLogWorkout: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onLogMeal: () -> Unit,
    onScanMeal: () -> Unit,
    onOpenMeal: (String) -> Unit,
    openFoodTabSignal: Int,
    openHabitsSignal: Int,
    onReturnToTabs: () -> Unit,
) {
    val tabsNav = rememberNavController()
    // The habit reminder notification opens the Habits tab.
    LaunchedEffect(openHabitsSignal) {
        if (openHabitsSignal > 0) {
            onReturnToTabs()
            tabsNav.switchTab(HabitsRoute)
        }
    }
    // The meal reminder's main tap opens the Food tab.
    LaunchedEffect(openFoodTabSignal) {
        if (openFoodTabSignal > 0) {
            onReturnToTabs()
            tabsNav.switchTab(FoodRoute)
        }
    }
    val backStackEntry by tabsNav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    } ?: Tab.Today

    // Header, ask bar and navbar float over the content; scrolling screens pad with these insets.
    val bottomInset = FloatingNavBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = MainHeaderHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFloatingBarInset provides bottomInset, LocalHeaderInset provides topInset) {
            NavHost(
                navController = tabsNav,
                startDestination = TodayRoute,
                enterTransition = { fadeIn(tween(180, easing = easing)) },
                exitTransition = { fadeOut(tween(180, easing = easing)) },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable<TodayRoute> {
                    TodayScreenRoute(
                        onOpenMetric = onOpenMetric,
                        onOpenInsights = onOpenInsights,
                        onOpenMeal = onOpenMeal,
                        onOpenWorkout = onOpenWorkout,
                        onOpenHabit = onOpenHabit,
                        onAddHabit = onAddHabit,
                        onOpenHabits = { tabsNav.switchTab(HabitsRoute) },
                    )
                }
                composable<FitnessRoute> { FitnessScreenRoute(onOpenWorkout = onOpenWorkout, onLogWorkout = onLogWorkout) }
                composable<FoodRoute> { FoodScreenRoute(onOpenMeal = onOpenMeal, onLogMeal = onLogMeal) }
                composable<HabitsRoute> { HabitsScreenRoute(onOpenHabit = onOpenHabit, onAddHabit = onAddHabit) }
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
                // Fitness and Food get a "+" to log, left of the profile photo.
                val onAdd = when (currentTab) {
                    Tab.Fitness -> onLogWorkout
                    Tab.Food -> onLogMeal
                    else -> null
                }
                if (onAdd != null) {
                    IconButton(
                        ResurgeIcons.Plus,
                        contentDescription = if (currentTab == Tab.Fitness) "Log a workout" else "Log a meal",
                        onClick = onAdd,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                // The photo shrinks a little while pressed, on top of the press tint, so the tap reads on a photo.
                val avatarPress = remember { MutableInteractionSource() }
                val pressed by avatarPress.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(120), label = "avatarPress")
                UnstyledButton(
                    onClick = onOpenSettings,
                    role = Role.Button,
                    interactionSource = avatarPress,
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(Resurge.shape.pill)
                        .semantics { contentDescription = "Settings" },
                    indication = LocalIndication.current,
                ) {
                    Avatar(profile.displayName, profile.photoUrl, size = 38.dp)
                }
            },
        )
        // The coach sits in the middle of the navbar, between the first two and last two tabs.
        FloatingNavBar(
            items = Tab.entries.map { NavItem(it, it.label, it.icon, it.selectedIcon) },
            selected = currentTab,
            onSelect = { tab -> tabsNav.switchTab(tab.route) },
            modifier = Modifier.align(Alignment.BottomCenter),
            center = { NavCoach(commander = profile.coachStyle == CoachStyle.COMMANDER, onClick = onOpenCoach) },
        )
    }
}

private const val PICKED_ACTIVITY = "pickedActivity"

/** Request from a meal reminder: [mode] "scan" / "type" opens the log screen, null opens the Food tab. */
data class FoodSignal(val count: Int = 0, val mode: String? = null)

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
