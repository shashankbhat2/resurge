package com.resurgeai.feature.dashboard

import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import com.resurgeai.core.designsystem.components.SectionLabel
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.PlanMatch
import com.resurgeai.core.model.SourceKind
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UnitSystem
import java.time.LocalDate

@Composable
fun TodayRoute(
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenMeal: (String) -> Unit = {},
    onOpenWorkout: (String) -> Unit = {},
    onOpenHabit: (String) -> Unit = {},
    onAddHabit: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    /** Starts today's planned session: the session logger for strength, the editor for cardio. */
    onStartSession: (PlanDay) -> Unit = {},
    /** A new progress reel, when there is one (supplied by the app). */
    reelCard: (@Composable () -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) {
        viewModel.onPermissionsResult(it)
    }
    val writeWeightLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) { granted ->
        viewModel.importWeights(alsoWrite = viewModel.weightWritePermission in granted)
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    TodayScreen(
        state = state,
        onConnect = { permissionLauncher.launch(viewModel.permissionsToRequest) },
        onInstall = { runCatching { context.startActivity(viewModel.installIntent()) } },
        onRetry = { viewModel.refresh(force = true) },
        onOpenMetric = onOpenMetric,
        onOpenInsights = onOpenInsights,
        onOpenMeal = onOpenMeal,
        onOpenWorkout = onOpenWorkout,
        onToggleHabit = viewModel::toggleHabit,
        onOpenHabit = onOpenHabit,
        onAddHabit = onAddHabit,
        onOpenHabits = onOpenHabits,
        onStartSession = onStartSession,
        onCompactListChange = viewModel::setCompactList,
        onDismissDayComplete = viewModel::dismissDayComplete,
        onSaveDayGoals = viewModel::saveDayGoals,
        onDismissInsight = viewModel::dismissInsight,
        onImportWeights = { alsoWrite ->
            if (alsoWrite) writeWeightLauncher.launch(setOf(viewModel.weightWritePermission)) else viewModel.importWeights(false)
        },
        onDismissWeightImport = viewModel::dismissWeightImport,
        reelCard = reelCard,
    )
}

/**
 * Today (Resurge Mono), top to bottom: the day streak, this week against its target, the major goal,
 * today's list (the planned session, then each goal of the day), today's vitals, the newest coach
 * note, and a feed of today's and yesterday's meals, workouts, sleep and notes.
 */
@Composable
fun TodayScreen(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenInsights: () -> Unit = {},
    onOpenMeal: (String) -> Unit = {},
    onOpenWorkout: (String) -> Unit = {},
    onToggleHabit: (String) -> Unit = {},
    onOpenHabit: (String) -> Unit = {},
    onAddHabit: () -> Unit = {},
    onOpenHabits: () -> Unit = {},
    onStartSession: (PlanDay) -> Unit = {},
    onCompactListChange: (Boolean) -> Unit = {},
    onDismissDayComplete: () -> Unit = {},
    onSaveDayGoals: (DayGoals) -> Unit = {},
    onDismissInsight: (String) -> Unit = {},
    onImportWeights: (alsoWrite: Boolean) -> Unit = {},
    onDismissWeightImport: () -> Unit = {},
    reelCard: (@Composable () -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
) {
    val commander = state.profile?.coachStyle == CoachStyle.COMMANDER
    val pinned = state.pinnedInsight
    val feed = TodayFeed.build(
        today = today,
        meals = state.meals,
        workouts = state.workouts,
        sleep = state.today?.sleep.orEmpty(),
        insights = state.insights,
        // The newest unseen note has its own card, so the feed leaves it out until it's seen.
        lead = pinned,
    )
    val goal = state.profile?.majorGoal
    val units = state.profile?.units ?: UnitSystem.METRIC
    val losingWeight = goal?.takeIf { it.type.byWeight }?.let { g -> (g.targetKg ?: 0.0) < (g.startKg ?: 0.0) }
    var detail by rememberSaveable { mutableStateOf<StreakDetail?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(LocalHeaderInset.current + 4.dp))
        StreakHero(state.streak) { detail = StreakDetail.STREAK }
        Spacer(Modifier.height(16.dp))
        WeekCard(state.streak, goal, today) { detail = StreakDetail.WEEK }
        goal?.let { g ->
            Spacer(Modifier.height(10.dp))
            GoalCard(g, state.streak, state.weight, units, today) {
                if (g.type.byWeight) onOpenMetric(HealthMetric.WEIGHT) else detail = StreakDetail.WEEK
            }
        }

        val session = state.plan?.days?.firstOrNull { it.day == today.dayOfWeek }
        val sessionDone = session != null && state.workouts.any { it.date() == today && PlanMatch.matches(session, it) }
        TodayChecklist(
            state = state.streak,
            session = session,
            sessionDone = sessionDone,
            compact = state.compactList,
            onCompactChange = onCompactListChange,
            onStartSession = onStartSession,
            onToggleHabit = onToggleHabit,
            onOpenHabits = onOpenHabits,
            onOpenSettings = { detail = StreakDetail.GOALS },
        )
        if (state.habits?.isEmpty() == true) {
            Spacer(Modifier.height(4.dp))
            Button("Add a habit", onClick = onAddHabit, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        }

        SectionLabel("Vitals")
        when (state.availability) {
            null -> TodaySkeleton()
            HealthConnectAvailability.NOT_SUPPORTED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = "Health Connect isn't available",
                body = "This device doesn't support Health Connect, so sleep, steps and heart rate can't be shown.",
            )
            HealthConnectAvailability.NOT_INSTALLED, HealthConnectAvailability.UPDATE_REQUIRED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = if (state.availability == HealthConnectAvailability.NOT_INSTALLED) "Install Health Connect" else "Update Health Connect",
                body = "Resurge reads your steps, sleep and workouts through Health Connect, so they count toward your streak.",
                action = "Open Play Store",
                onAction = onInstall,
            )
            HealthConnectAvailability.AVAILABLE -> when (state.permissions) {
                PermissionState.UNKNOWN -> TodaySkeleton()
                PermissionState.NONE -> InfoCard(
                    icon = ResurgeIcons.Watch,
                    title = "Connect your health data",
                    body = "Allow Resurge to read sleep, steps, calories, workouts and heart rate via Health Connect. Runs and walks then log themselves.",
                    action = "Connect Health Connect",
                    onAction = onConnect,
                )
                PermissionState.PARTIAL, PermissionState.ALL -> {
                    if (state.permissions == PermissionState.PARTIAL) {
                        PartialBanner(onConnect)
                        Spacer(Modifier.height(10.dp))
                    }
                    state.error?.let {
                        InfoCard(ResurgeIcons.Activity, "Couldn't read Health Connect", "Check that Health Connect is set up, then try again.", action = "Retry", onAction = onRetry)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        // Draws nothing (and takes no space) unless a reel is waiting; it brings its own spacing.
        reelCard?.invoke()
        state.weightImport?.let { count ->
            WeightImportCard(count, state.importingWeight, onImport = onImportWeights, onDismiss = onDismissWeightImport)
            Spacer(Modifier.height(10.dp))
        }
        val day = state.today
        if (state.healthReady) {
            if (day != null) {
                VitalTiles(
                    day = day,
                    targets = state.profile?.targets ?: Targets(),
                    sparklines = state.sparklines,
                    weight = state.weight,
                    eatenKcal = state.eaten.kcal,
                    units = units,
                    losingWeight = losingWeight,
                    onOpen = onOpenMetric,
                )
            } else if (state.error == null) TodaySkeleton()
        } else if (state.availability != null) {
            Spacer(Modifier.height(10.dp))
            WeightVitalRow(state.weight, units, losingWeight, onOpenMetric)
        }

        pinned?.let { insight ->
            SectionLabel("From your coach")
            key(insight.id) {
                CoachCard(
                    insight,
                    onOpen = { onDismissInsight(insight.id); onOpenInsights() },
                    onDismiss = { onDismissInsight(insight.id) },
                )
            }
        }

        Feed(
            days = feed,
            today = today,
            units = units,
            commander = commander,
            actions = FeedActions(
                onOpenMeal = onOpenMeal,
                onOpenWorkout = onOpenWorkout,
                onOpenSleep = { onOpenMetric(HealthMetric.SLEEP) },
                onOpenInsights = onOpenInsights,
            ),
        )
        Spacer(Modifier.height(32.dp + LocalFloatingBarInset.current))
    }

    if (state.showDayComplete) DayCompleteDialog(state, onDismissDayComplete)
    detail?.let { StreakDetailDialog(it, state.streak, state.profile?.habitSettings ?: HabitSettings(), onSaveDayGoals) { detail = null } }
}

@DrawableRes
internal fun sourceIcon(kind: SourceKind): Int = when (kind) {
    SourceKind.WEARABLE -> ResurgeIcons.Watch
    SourceKind.PHONE -> ResurgeIcons.Phone
    SourceKind.MANUAL -> ResurgeIcons.Profile
    SourceKind.OTHER_DEVICE, SourceKind.UNKNOWN -> ResurgeIcons.Activity
}

@Composable
private fun InfoCard(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth()) {
        IconBadge(icon, tint = Resurge.colors.accent, size = 40.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = Resurge.type.headline)
        Spacer(Modifier.height(4.dp))
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PartialBanner(onManage: () -> Unit) {
    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Some data types are turned off.",
                style = Resurge.type.label,
                color = Resurge.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Button("Review", onClick = onManage, variant = ButtonVariant.Ghost)
        }
    }
}

@Preview
@Composable
private fun TodayNoPermissionPreview() {
    ResurgeTheme {
        TodayScreen(
            state = DashboardUiState(availability = HealthConnectAvailability.AVAILABLE, permissions = PermissionState.NONE),
            onConnect = {}, onInstall = {}, onRetry = {}, onOpenMetric = {},
        )
    }
}
