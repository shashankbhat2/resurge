package com.resurgeai.app.main

import com.resurgeai.feature.fitness.PlanRoute
import androidx.compose.runtime.mutableIntStateOf
import com.resurgeai.feature.fitness.SessionLoggerRoute
import com.resurgeai.core.model.wire
import com.resurgeai.core.model.PlanDay
import androidx.compose.animation.core.LinearEasing
import dev.chrisbanes.haze.HazeProgressive
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.unit.sp
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
import com.resurgeai.core.designsystem.components.NavAdd
import com.resurgeai.core.designsystem.components.CoachButton
import com.resurgeai.core.designsystem.components.CoachButtonClearance
import com.resurgeai.core.designsystem.components.BottomSheet
import androidx.compose.foundation.layout.size
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MainHeader
import com.resurgeai.core.designsystem.components.MainHeaderHeight
import androidx.compose.ui.text.style.TextOverflow
import com.composeunstyled.Text
import java.time.LocalTime
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.resurgeai.core.designsystem.components.Icon
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
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
import com.resurgeai.app.widget.WeightWidget
import com.resurgeai.core.designsystem.catalog.DesignCatalog
import com.resurgeai.core.designsystem.components.NavBar
import com.resurgeai.core.designsystem.components.NavBarHeight
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.NavItem
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.UserProfile
import com.resurgeai.feature.dashboard.TodayRoute as TodayScreenRoute
import com.resurgeai.feature.dashboard.detail.MetricDetailRoute as MetricDetailScreenRoute
import com.resurgeai.feature.dashboard.detail.ScoreDetailRoute as ScoreDetailScreenRoute
import com.resurgeai.feature.dashboard.ScorePeriod
import com.resurgeai.core.model.ScoreKind
import com.resurgeai.feature.dashboard.sources.MetricSourcesRoute as MetricSourcesScreenRoute
import com.resurgeai.feature.coach.CoachRoute as CoachScreen
import com.resurgeai.feature.coach.InsightsRoute as InsightsScreen
import com.resurgeai.feature.coach.InsightDetailRoute as InsightDetailScreen
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
import com.resurgeai.feature.progress.ProgressStrip
import com.resurgeai.feature.weight.GoalCelebrationHost
import com.resurgeai.feature.progress.ReelReadyCard
import com.resurgeai.feature.progress.ReelPlayerRoute as ReelPlayerScreen
import com.resurgeai.feature.progress.ProgressCaptureRoute as ProgressCaptureScreen
import com.resurgeai.feature.progress.ProgressRoute as ProgressScreen
import com.resurgeai.feature.progress.ProgressSettingsRoute as ProgressSettingsScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import com.resurgeai.feature.weight.WeightRoute as WeightScreen
import com.resurgeai.feature.scale.SmartScaleRoute as SmartScaleScreen

// Root graph: the tab shell plus full-screen destinations that cover it (incl. the bottom bar).
@Serializable data object TabsRoute
@Serializable data class MetricDetailRoute(val metric: String)
@Serializable data class MetricSourcesRoute(val metric: String)
@Serializable data object CatalogRoute

@Serializable data class HabitDetailRoute(val id: String)

/** Add a habit (id = null) or edit one. */
@Serializable data class HabitEditorRoute(val id: String? = null)

/** Full-screen coach chat; [newChat] opens on an empty conversation instead of the latest one. */
@Serializable data class CoachScreenRoute(val newChat: Boolean = false, val threadId: String? = null, val insightId: String? = null)

/** Activity catalog search; [forResult] returns the pick to the workout editor instead of opening it. */
@Serializable data class ActivityPickerRoute(val forResult: Boolean = false)

/** Log a workout ([activityId]) or edit one ([workoutId]). */
@Serializable data class WorkoutEditorRoute(val workoutId: String? = null, val activityId: String? = null)

/** The training plan in full, with rebuilding. */
@Serializable data class PlanScreenRoute(val change: String? = null)
@Serializable data class ScoreDetailRoute(val kind: String, val period: String)

/** The tap-through logger for the plan's session on [day] ("mon" … "sun"). */
@Serializable data class SessionLogRoute(val day: String)

/** Scan / photo / type / log again. [mode] "scan" opens the camera, "type" focuses the text field. */
@Serializable data class FoodLogRoute(val mode: String? = null)

/** Review a fresh estimate (mealId null) or edit a logged meal. */
@Serializable data class MealReviewRoute(val mealId: String? = null)

/** Weight trend, goal and weigh-ins; [log] opens the log sheet straight away, [newGoal] the goal editor for a fresh goal. */
@Serializable data class WeightScreenRoute(val log: Boolean = false, val newGoal: Boolean = false)

/** Progress photos: compare, grid, viewer. Reached from Weight, Settings and the launcher shortcut. */
@Serializable data object ProgressScreenRoute

/** The progress camera. */
@Serializable data object ProgressCaptureRoute

/** Plays a progress reel. */
@Serializable data class ReelRoute(val id: String)

/** Backup, recovery key and privacy for progress photos. */
@Serializable data object ProgressSettingsRoute

/** Pairing and settings for Bluetooth smart scales. Reached from Weight and Settings. */
@Serializable data object SmartScaleScreenRoute

/** Settings, opened from the profile photo. */
@Serializable data object SettingsRoute

/** Standalone list of past insights. */
@Serializable data class InsightsScreenRoute(val filter: String? = null)
@Serializable data class InsightDetailRoute(val id: String)

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
fun MainScreen(
    profile: UserProfile,
    openInsightsSignal: Int = 0,
    openHabitsSignal: Int = 0,
    openFoodSignal: FoodSignal = FoodSignal(),
    openProgressSignal: ProgressSignal = ProgressSignal(),
    openWeightSignal: WeightSignal = WeightSignal(),
) {
    val rootNav = rememberNavController()
    // A tapped insight notification opens Coach on the Insights tab.
    LaunchedEffect(openInsightsSignal) {
        if (openInsightsSignal > 0) rootNav.navigate(InsightsScreenRoute()) { launchSingleTop = true }
    }
    // Meal reminder actions: Scan / Type open the log screen in that mode.
    LaunchedEffect(openFoodSignal.count) {
        val mode = openFoodSignal.mode
        if (openFoodSignal.count > 0 && mode != null) {
            rootNav.popBackStack(TabsRoute, inclusive = false)
            rootNav.navigate(FoodLogRoute(mode))
        }
    }
    // Launcher shortcut: straight to the camera; a reel notification: the Progress screen.
    LaunchedEffect(openProgressSignal.count) {
        if (openProgressSignal.count > 0) {
            rootNav.popBackStack(TabsRoute, inclusive = false)
            rootNav.navigate(ProgressScreenRoute)
            if (openProgressSignal.capture) rootNav.navigate(ProgressCaptureRoute)
            openProgressSignal.reelId?.let { rootNav.navigate(ReelRoute(it)) }
        }
    }
    LaunchedEffect(openWeightSignal.count) {
        if (openWeightSignal.count > 0) {
            rootNav.popBackStack(TabsRoute, inclusive = false)
            rootNav.navigate(WeightScreenRoute(log = openWeightSignal.log))
            if (openWeightSignal.smartScale) rootNav.navigate(SmartScaleScreenRoute)
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
                onOpenMetric = { metric ->
                    // Weight is logged in Resurge now, with its own screen.
                    if (metric == HealthMetric.WEIGHT) rootNav.navigate(WeightScreenRoute()) else rootNav.navigate(MetricDetailRoute(metric.name))
                },
                onOpenScore = { kind, period -> rootNav.navigate(ScoreDetailRoute(kind.name, period.name)) },
                onOpenSettings = { rootNav.navigate(SettingsRoute) },
                onOpenCoach = { rootNav.navigate(CoachScreenRoute(newChat = true)) },
                onOpenInsights = { rootNav.navigate(InsightsScreenRoute()) },
                onOpenInsight = { id -> rootNav.navigate(InsightDetailRoute(id)) },
                onOpenHabit = { id -> rootNav.navigate(HabitDetailRoute(id)) },
                onAddHabit = { rootNav.navigate(HabitEditorRoute()) },
                onLogWorkout = { rootNav.navigate(ActivityPickerRoute()) },
                // A planned strength session opens the tap-through logger; cardio opens the editor for its activity.
                onStartSession = { day ->
                    if (day.exercises.isNotEmpty()) rootNav.navigate(SessionLogRoute(day.day.wire))
                    else rootNav.navigate(WorkoutEditorRoute(activityId = day.activityId))
                },
                onLogActivity = { id -> rootNav.navigate(WorkoutEditorRoute(activityId = id)) },
                onOpenPlan = { rootNav.navigate(PlanScreenRoute()) },
                onOpenWorkout = { id -> rootNav.navigate(WorkoutEditorRoute(workoutId = id)) },
                onLogMeal = { rootNav.navigate(FoodLogRoute()) },
                onScanMeal = { rootNav.navigate(FoodLogRoute(mode = "scan")) },
                onLogWeight = { rootNav.navigate(WeightScreenRoute(log = true)) },
                onOpenMeal = { id -> rootNav.navigate(MealReviewRoute(mealId = id)) },
                onOpenReel = { id -> rootNav.navigate(ReelRoute(id)) },
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
            FullScreen {
                CoachScreen(
                    onBack = { rootNav.popBackStack() },
                    onOpenChats = { rootNav.navigate(InsightsScreenRoute(filter = "CHATS")) },
                    onOpenPlanChange = { change -> rootNav.navigate(PlanScreenRoute(change)) },
                )
            }
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
        composable<PlanScreenRoute> {
            FullScreen { PlanRoute(onBack = { rootNav.popBackStack() }) }
        }
        composable<SessionLogRoute> {
            FullScreen { SessionLoggerRoute(onClose = { rootNav.popBackStack() }) }
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
        composable<WeightScreenRoute> { entry ->
            FullScreen {
                WeightScreen(
                    onBack = { rootNav.popBackStack() },
                    openLog = entry.toRoute<WeightScreenRoute>().log,
                    openNewGoal = entry.toRoute<WeightScreenRoute>().newGoal,
                    onOpenSmartScale = { rootNav.navigate(SmartScaleScreenRoute) { launchSingleTop = true } },
                    progressStrip = {
                        ProgressStrip(
                            onOpen = { rootNav.navigate(ProgressScreenRoute) },
                            onCapture = { rootNav.navigate(ProgressScreenRoute); rootNav.navigate(ProgressCaptureRoute) },
                        )
                    },
                )
            }
        }
        composable<SmartScaleScreenRoute> {
            val context = LocalContext.current
            FullScreen {
                SmartScaleScreen(
                    onBack = { rootNav.popBackStack() },
                    onAddWidget = if (WeightWidget.canPin(context)) ({ WeightWidget.pin(context) }) else null,
                )
            }
        }
        composable<ProgressScreenRoute> {
            FullScreen {
                ProgressScreen(
                    onBack = { rootNav.popBackStack() },
                    onCapture = { rootNav.navigate(ProgressCaptureRoute) { launchSingleTop = true } },
                    onOpenSettings = { rootNav.navigate(ProgressSettingsRoute) },
                    onOpenReel = { id -> rootNav.navigate(ReelRoute(id)) },
                )
            }
        }
        composable<ProgressCaptureRoute> {
            FullScreen {
                ProgressCaptureScreen(
                    onDone = { rootNav.popBackStack() },
                    // No key on this phone yet: the Progress screen sets it up or unlocks.
                    onNeedsKey = { rootNav.popBackStack() },
                )
            }
        }
        composable<ReelRoute> {
            FullScreen { ReelPlayerScreen(onClose = { rootNav.popBackStack() }) }
        }
        composable<ProgressSettingsRoute> {
            FullScreen {
                ProgressSettingsScreen(onBack = { rootNav.popBackStack() })
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
                    onOpenProgressPhotos = { rootNav.navigate(ProgressSettingsRoute) },
                    onOpenSmartScale = { rootNav.navigate(SmartScaleScreenRoute) },
                    versionName = BuildConfig.VERSION_NAME,
                )
            }
        }
        composable<InsightsScreenRoute> {
            FullScreen {
                InsightsScreen(
                    onBack = { rootNav.popBackStack() },
                    onOpenInsight = { id -> rootNav.navigate(InsightDetailRoute(id)) },
                    onOpenChat = { id -> rootNav.navigate(CoachScreenRoute(threadId = id)) },
                    onNewChat = { rootNav.navigate(CoachScreenRoute(newChat = true)) },
                )
            }
        }
        composable<InsightDetailRoute> {
            FullScreen {
                InsightDetailScreen(
                    onBack = { rootNav.popBackStack() },
                    onAsk = { id -> rootNav.navigate(CoachScreenRoute(insightId = id)) },
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
        composable<ScoreDetailRoute> {
            FullScreen {
                ScoreDetailScreenRoute(
                    onBack = { rootNav.popBackStack() },
                    onOpenMetric = { metric -> rootNav.navigate(MetricDetailRoute(metric.name)) },
                    onOpenScore = { kind -> rootNav.navigate(ScoreDetailRoute(kind.name, ScorePeriod.WEEK.name)) },
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
    // Milestones and the goal are celebrated wherever the person is when the trend gets there.
    GoalCelebrationHost(onNewGoal = {
        rootNav.popBackStack(TabsRoute, inclusive = false)
        rootNav.navigate(WeightScreenRoute(newGoal = true))
    })
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
    onOpenScore: (ScoreKind, ScorePeriod) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCoach: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenInsight: (String) -> Unit,
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
    onLogWorkout: () -> Unit,
    onStartSession: (PlanDay) -> Unit,
    onLogActivity: (String) -> Unit,
    onOpenPlan: () -> Unit,
    onOpenWorkout: (String) -> Unit,
    onLogMeal: () -> Unit,
    onScanMeal: () -> Unit,
    onLogWeight: () -> Unit,
    onOpenMeal: (String) -> Unit,
    onOpenReel: (String) -> Unit,
    openFoodTabSignal: Int,
    openHabitsSignal: Int,
    onReturnToTabs: () -> Unit,
) {
    val tabsNav = rememberNavController()
    val headerHaze = remember { HazeState() }
    // The Fitness header's "+" opens quick add on the tab.
    var fitnessQuickAdd by remember { mutableIntStateOf(0) }
    // Add in the navbar opens the quick logging sheet.
    var quickAdd by remember { mutableStateOf(false) }
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
    val bottomInset = NavBarHeight + CoachButtonClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = MainHeaderHeight + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFloatingBarInset provides bottomInset, LocalHeaderInset provides topInset) {
            NavHost(
                navController = tabsNav,
                startDestination = TodayRoute,
                modifier = Modifier.hazeSource(headerHaze),
                enterTransition = { fadeIn(tween(180, easing = easing)) },
                exitTransition = { fadeOut(tween(180, easing = easing)) },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable<TodayRoute> {
                    TodayScreenRoute(
                        onOpenMetric = onOpenMetric,
                        onOpenInsights = onOpenInsights,
                        onOpenInsight = onOpenInsight,
                        onOpenMeal = onOpenMeal,
                        onOpenWorkout = onOpenWorkout,
                        onOpenHabit = onOpenHabit,
                        onAddHabit = onAddHabit,
                        onOpenHabits = { tabsNav.switchTab(HabitsRoute) },
                        onStartSession = onStartSession,
                        onOpenScore = onOpenScore,
                        reelCard = { ReelReadyCard(onPlay = onOpenReel, modifier = Modifier.padding(bottom = 12.dp)) },
                    )
                }
                composable<FitnessRoute> {
                    FitnessScreenRoute(
                        onOpenWorkout = onOpenWorkout,
                        onLogWorkout = onLogWorkout,
                        onLogActivity = onLogActivity,
                        onStartSession = onStartSession,
                        quickAddSignal = fitnessQuickAdd,
                        onOpenPlan = onOpenPlan,
                    )
                }
                composable<FoodRoute> { FoodScreenRoute(onOpenMeal = onOpenMeal, onLogMeal = onLogMeal) }
                composable<HabitsRoute> { HabitsScreenRoute(onOpenHabit = onOpenHabit, onAddHabit = onAddHabit) }
            }
        }
        MainHeader(
            title = currentTab.title,
            // Frosts whatever scrolls underneath: full blur at the top, none by the fade's end.
            modifier = Modifier
                .align(Alignment.TopCenter)
                .hazeEffect(headerHaze) {
                    blurRadius = 20.dp
                    // Blur tapers evenly to none at the fade's end, so there's no hard edge at the bottom.
                    progressive = HazeProgressive.verticalGradient(easing = LinearEasing, startIntensity = 1f, endIntensity = 0f)
                },
            // Today's header greets by the time of day, left of the profile photo.
            leading = if (currentTab == Tab.Today) {
                { Text(greeting(LocalTime.now(), profile.firstName), style = Resurge.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            // On Today the blur stops where the day strip starts.
            fade = if (currentTab == Tab.Today) 0.dp else 40.dp,
            trailing = {
                // Fitness and Food get a "+" to log, left of the profile photo.
                val onAdd = when (currentTab) {
                    Tab.Fitness -> { { fitnessQuickAdd += 1 } }
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
                Box(Modifier.size(42.dp)) {
                    UnstyledButton(
                        onClick = onOpenSettings,
                        role = Role.Button,
                        interactionSource = avatarPress,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(Resurge.shape.pill)
                            .background(Resurge.colors.bg)
                            .padding(2.dp)
                            .semantics { contentDescription = "Settings" },
                        indication = LocalIndication.current,
                    ) {
                        Avatar(profile.displayName, profile.photoUrl, size = 38.dp)
                    }
                }
            },
        )
        // Add sits in the middle of the navbar, between the first two and last two tabs.
        NavBar(
            items = Tab.entries.map { NavItem(it, it.label, it.icon, it.selectedIcon) },
            selected = currentTab,
            onSelect = { tab -> tabsNav.switchTab(tab.route) },
            modifier = Modifier.align(Alignment.BottomCenter),
            center = { NavAdd(onClick = { quickAdd = true }) },
        )
        // The coach floats over the right end of the navbar.
        CoachButton(onClick = onOpenCoach, modifier = Modifier.align(Alignment.BottomEnd))
    }
    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onLogMeal = { quickAdd = false; onLogMeal() },
            onScanMeal = { quickAdd = false; onScanMeal() },
            onLogWorkout = { quickAdd = false; onLogWorkout() },
            onLogWeight = { quickAdd = false; onLogWeight() },
            onAddHabit = { quickAdd = false; onAddHabit() },
        )
    }
}

/** What Add in the navbar offers: the quick ways to log something from any tab. */
@Composable
private fun QuickAddSheet(
    onDismiss: () -> Unit,
    onLogMeal: () -> Unit,
    onScanMeal: () -> Unit,
    onLogWorkout: () -> Unit,
    onLogWeight: () -> Unit,
    onAddHabit: () -> Unit,
) {
    BottomSheet(onDismiss) {
        Text("Add", style = Resurge.type.title)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAddRow(ResurgeIcons.Bowl, "Log a meal", onLogMeal)
            QuickAddRow(ResurgeIcons.Scan, "Scan a meal", onScanMeal)
            QuickAddRow(ResurgeIcons.Fitness, "Log a workout", onLogWorkout)
            QuickAddRow(ResurgeIcons.Scale, "Log weight", onLogWeight)
            QuickAddRow(ResurgeIcons.Habits, "Add a habit", onAddHabit)
        }
    }
}

@Composable
private fun QuickAddRow(@DrawableRes icon: Int, label: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        indication = LocalIndication.current,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Resurge.colors.textMuted, size = 22.dp)
            Spacer(Modifier.width(12.dp))
            Text(label, style = Resurge.type.label.copy(fontSize = Resurge.type.body.fontSize))
        }
    }
}

private const val PICKED_ACTIVITY = "pickedActivity"

/** Request from a meal reminder: [mode] "scan" / "type" opens the log screen, null opens the Food tab. */
data class FoodSignal(val count: Int = 0, val mode: String? = null)

/** Open Progress; [capture] goes on to the camera, [reelId] plays that reel. */
data class ProgressSignal(val count: Int = 0, val capture: Boolean = false, val reelId: String? = null)

/** Scale notifications and the weight widget: open Weight, its log sheet, or Smart scale settings. */
data class WeightSignal(val count: Int = 0, val log: Boolean = false, val smartScale: Boolean = false)

/** "Good morning, Sam" and so on through the day. */
private fun greeting(now: LocalTime, firstName: String?): String {
    val part = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    return firstName?.let { "$part, $it" } ?: part
}

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
