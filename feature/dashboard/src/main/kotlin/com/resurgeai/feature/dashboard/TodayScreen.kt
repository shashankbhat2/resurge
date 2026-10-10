package com.resurgeai.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Avatar
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.MetricCard
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.WeekDots
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.SourceKind
import java.time.Duration
import java.time.LocalTime

@Composable
fun TodayRoute(
    onOpenMetric: (HealthMetric) -> Unit,
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
    )
}

@Composable
fun TodayScreen(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenMetric: (HealthMetric) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Header(state)
        Spacer(Modifier.height(20.dp))

        when (state.availability) {
            null -> Unit
            HealthConnectAvailability.NOT_SUPPORTED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = "Health Connect isn't available",
                body = "This device doesn't support Health Connect, so health data can't be shown.",
            )
            HealthConnectAvailability.NOT_INSTALLED, HealthConnectAvailability.UPDATE_REQUIRED -> InfoCard(
                icon = ResurgeIcons.Activity,
                title = if (state.availability == HealthConnectAvailability.NOT_INSTALLED) "Install Health Connect" else "Update Health Connect",
                body = "Resurge reads your steps, sleep and workouts through Health Connect.",
                action = "Open Play Store",
                onAction = onInstall,
            )
            HealthConnectAvailability.AVAILABLE -> when (state.permissions) {
                PermissionState.UNKNOWN -> LoadingRow()
                PermissionState.NONE -> InfoCard(
                    icon = ResurgeIcons.Watch,
                    title = "Connect your health data",
                    body = "Allow Resurge to read sleep, steps, calories, workouts and heart rate from your phone and wearable via Health Connect.",
                    action = "Connect Health Connect",
                    onAction = onConnect,
                )
                PermissionState.PARTIAL, PermissionState.ALL -> {
                    if (state.permissions == PermissionState.PARTIAL) {
                        PartialBanner(onConnect)
                        Spacer(Modifier.height(12.dp))
                    }
                    state.error?.let {
                        InfoCard(ResurgeIcons.Activity, "Something went wrong", it, action = "Retry", onAction = onRetry)
                        Spacer(Modifier.height(12.dp))
                    }
                    val today = state.today
                    if (today == null) LoadingRow() else MetricGrid(state, today, onOpenMetric)
                }
            }
        }
        Spacer(Modifier.height(24.dp + LocalFloatingBarInset.current))
    }
}

@Composable
private fun Header(state: DashboardUiState) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = Resurge.type.body, color = Resurge.colors.textMuted)
            Text(state.profile?.firstName ?: "Today", style = Resurge.type.display)
        }
        if (state.loading) {
            Spinner(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        state.profile?.let { Avatar(it.displayName, it.photoUrl, size = 44.dp) }
    }
}

@Composable
private fun MetricGrid(state: DashboardUiState, day: DailyHealth, onOpen: (HealthMetric) -> Unit) {
    val cards = buildList {
        add(HealthMetric.STEPS)
        add(HealthMetric.SLEEP)
        add(HealthMetric.CALORIES_BURNED)
        add(HealthMetric.DISTANCE)
        add(HealthMetric.EXERCISE)
        add(HealthMetric.HEART_RATE)
        if (day.weightKg != null) add(HealthMetric.WEIGHT)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(2).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { metric ->
                    TodayCard(metric, state, day, { onOpen(metric) }, Modifier.weight(1f).fillMaxHeight())
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TodayCard(
    metric: HealthMetric,
    state: DashboardUiState,
    day: DailyHealth,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val spark = state.sparklines[metric]
    when (metric) {
        HealthMetric.STEPS -> MetricCard(
            title = "Steps", icon = ResurgeIcons.Footprints, tint = Resurge.colors.activity,
            value = day.steps?.let(Formatters::count),
            sparkline = spark, onClick = onClick, modifier = modifier,
        )
        HealthMetric.SLEEP -> MetricCard(
            title = "Sleep", icon = ResurgeIcons.Bed, tint = Resurge.colors.sleep,
            value = day.sleepDuration?.let(Formatters::duration),
            subtitle = sleepSubtitle(day),
            sparkline = spark, onClick = onClick, modifier = modifier,
        )
        HealthMetric.CALORIES_BURNED -> MetricCard(
            title = "Calories burned", icon = ResurgeIcons.Flame, tint = Resurge.colors.energy,
            value = day.caloriesTotal?.let(Formatters::kcal), unit = "kcal",
            subtitle = day.caloriesActive?.let { "${Formatters.kcal(it)} active" },
            sparkline = spark, onClick = onClick, modifier = modifier,
        )
        HealthMetric.DISTANCE -> MetricCard(
            title = "Distance", icon = ResurgeIcons.Distance, tint = Resurge.colors.water,
            value = day.distanceMeters?.let(Formatters::kilometres), unit = "km",
            sparkline = spark, onClick = onClick, modifier = modifier,
        )
        HealthMetric.EXERCISE -> MetricCard(
            title = "Exercise days", icon = ResurgeIcons.Dumbbell, tint = Resurge.colors.activity,
            value = "${state.exerciseWeek.count { it }}", unit = "/ 7",
            subtitle = if (day.exerciseDuration > Duration.ZERO) "${Formatters.duration(day.exerciseDuration)} today" else "This week",
            footer = { WeekDots(state.exerciseWeek, Resurge.colors.activity, todayIndex = state.todayIndex) },
            onClick = onClick, modifier = modifier,
        )
        HealthMetric.HEART_RATE -> {
            val hr = day.heartRate
            MetricCard(
                title = "Heart rate", icon = ResurgeIcons.Heart, tint = Resurge.colors.heart,
                value = (hr?.restingBpm ?: hr?.avgBpm)?.toString(), unit = "bpm",
                subtitle = hr?.let {
                    listOfNotNull(
                        if (it.restingBpm != null) "Resting" else "Average",
                        if (it.minBpm != null && it.maxBpm != null) "${it.minBpm}–${it.maxBpm}" else null,
                    ).joinToString(" · ")
                },
                sparkline = spark, onClick = onClick, modifier = modifier,
            )
        }
        HealthMetric.WEIGHT -> MetricCard(
            title = "Weight", icon = ResurgeIcons.Scale, tint = Resurge.colors.textMuted,
            value = day.weightKg?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) }, unit = "kg",
            subtitle = "Latest", onClick = onClick, modifier = modifier,
        )
    }
}

private fun sleepSubtitle(day: DailyHealth): String? {
    val stages = day.sleep.flatMap { it.stages.entries }.groupBy({ it.key }, { it.value })
        .mapValues { (_, d) -> d.fold(Duration.ZERO, Duration::plus) }
    val deep = stages[SleepStage.DEEP]
    val rem = stages[SleepStage.REM]
    return listOfNotNull(
        deep?.let { "Deep ${Formatters.duration(it)}" },
        rem?.let { "REM ${Formatters.duration(it)}" },
    ).joinToString(" · ").ifEmpty { if (day.sleep.isNotEmpty()) "Last night" else null }
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

@Composable
private fun LoadingRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalArrangement = Arrangement.Center,
    ) { Spinner() }
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
