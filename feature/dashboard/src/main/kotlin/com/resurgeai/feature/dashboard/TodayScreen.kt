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
import com.resurgeai.core.model.HealthMetric
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
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) {
        viewModel.onPermissionsResult(it)
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
        onDismissHighTide = viewModel::dismissHighTide,
        onDismissInsight = viewModel::dismissInsight,
    )
}

/**
 * Today, built around one daily tide: the tide card (with streak and weekly goal), one-tap habits,
 * today's health numbers as compact tiles, then a feed of today's and yesterday's meals, workouts,
 * sleep and coach notes.
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
    onDismissHighTide: () -> Unit = {},
    onDismissInsight: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
) {
    val commander = state.profile?.coachStyle == CoachStyle.COMMANDER
    val feed = TodayFeed.build(
        today = today,
        meals = state.meals,
        workouts = state.workouts,
        sleep = state.today?.sleep.orEmpty(),
        insights = state.insights,
        // The pinned insight sits above the tide, so the feed leaves it out until it's seen.
        lead = state.pinnedInsight,
    )
    val habits = state.habits
    var detail by rememberSaveable { mutableStateOf<TideDetail?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(LocalHeaderInset.current + 12.dp))
        val withHabits = !habits.isNullOrEmpty()
        // The latest unseen insight lies over the tide card, stopping short of its bottom so the tide's
        // edge peeks out underneath like the next card in a stack; dismissing reveals it.
        Box {
            TideCard(
                state.tide,
                rise = state.riseCount.takeIf { withHabits },
                ascent = state.ascent.takeIf { withHabits },
                onOpenDetail = { detail = it },
            )
            state.pinnedInsight?.let { insight ->
                key(insight.id) {
                    PinnedInsight(
                        insight,
                        commander,
                        onOpen = { onDismissInsight(insight.id); onOpenInsights() },
                        onDismiss = { onDismissInsight(insight.id) },
                        modifier = Modifier.matchParentSize().padding(bottom = 14.dp),
                    )
                }
            }
        }

        when {
            habits == null -> Unit
            habits.isEmpty() -> {
                SectionLabel("Habits")
                FirstHabitCard(onAddHabit)
            }
            else -> {
                SectionLabel("Habits", trailing = "${habits.count { it.done }} of ${habits.size} done")
                HabitGrid(habits, onToggle = onToggleHabit, onOpen = onOpenHabit, onAdd = onAddHabit, onSeeAll = onOpenHabits)
            }
        }

        SectionLabel("Health")
        when (state.availability) {
            null -> TodaySkeleton()
            HealthConnectAvailability.NOT_SUPPORTED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = "Health Connect isn't available",
                body = "This device doesn't support Health Connect, so health data can't be shown.",
            )
            HealthConnectAvailability.NOT_INSTALLED, HealthConnectAvailability.UPDATE_REQUIRED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = if (state.availability == HealthConnectAvailability.NOT_INSTALLED) "Install Health Connect" else "Update Health Connect",
                body = "Resurge reads your steps, sleep and workouts through Health Connect. They count toward your tide once connected.",
                action = "Open Play Store",
                onAction = onInstall,
            )
            HealthConnectAvailability.AVAILABLE -> when (state.permissions) {
                PermissionState.UNKNOWN -> TodaySkeleton()
                PermissionState.NONE -> InfoCard(
                    icon = ResurgeIcons.Watch,
                    title = "Connect your health data",
                    body = "Allow Resurge to read sleep, steps, calories, workouts and heart rate via Health Connect. Sleep and steps then count toward your tide.",
                    action = "Connect Health Connect",
                    onAction = onConnect,
                )
                PermissionState.PARTIAL, PermissionState.ALL -> {
                    if (state.permissions == PermissionState.PARTIAL) {
                        PartialBanner(onConnect)
                        Spacer(Modifier.height(12.dp))
                    }
                    state.error?.let {
                        InfoCard(ResurgeIcons.Activity, "Couldn't read Health Connect", "Check that Health Connect is set up, then try again.", action = "Retry", onAction = onRetry)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
        val day = state.today
        if (state.healthReady) {
            if (day != null) HealthTiles(day, state.profile?.targets ?: Targets(), onOpenMetric) else if (state.error == null) TodaySkeleton()
        }

        Feed(
            days = feed,
            today = today,
            units = state.profile?.units ?: UnitSystem.METRIC,
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

    if (state.showHighTide) HighTideDialog(state, onDismissHighTide)
    detail?.let { TideDetailDialog(it, state) { detail = null } }
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
