package com.resurgeai.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SourceKind
import java.time.LocalTime

@Composable
fun TodayRoute(
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenInsights: () -> Unit,
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
    )
}

@Composable
fun TodayScreen(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onRetry: () -> Unit,
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenInsights: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Greeting lives in the shared header; the latest insight leads the page.
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = LocalHeaderInset.current + 20.dp, bottom = 32.dp)) {
            CoachInsight(text = PLACEHOLDER_INSIGHT, time = LocalTime.of(7, 0), onClick = onOpenInsights)
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
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
                    body = "Resurge reads your steps, sleep and workouts through Health Connect.",
                    action = "Open Play Store",
                    onAction = onInstall,
                )
                HealthConnectAvailability.AVAILABLE -> when (state.permissions) {
                    PermissionState.UNKNOWN -> TodaySkeleton()
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
                        if (today == null) TodaySkeleton() else TodayGrid(state, today, onOpenMetric)
                    }
                }
            }
            Spacer(Modifier.height(32.dp + LocalFloatingBarInset.current))
        }
    }
}

/** Shown until the AI coach (Phase 4) writes real insights. */
private const val PLACEHOLDER_INSIGHT =
    "You slept 40 minutes less than your weekly average, and your resting heart rate is up 3 bpm. " +
        "Keep today's training light and aim to be in bed by 10:30."

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
