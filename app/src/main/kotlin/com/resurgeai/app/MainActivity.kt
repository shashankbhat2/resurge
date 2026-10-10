package com.resurgeai.app

import android.graphics.Color
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.theme.ColorScheme
import com.resurgeai.app.main.MainScreen
import com.resurgeai.app.notifications.ResurgeMessagingService
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import com.resurgeai.feature.auth.SignInRoute
import com.resurgeai.feature.auth.onboarding.OnboardingRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.session.value is SessionState.Loading }
        handleOpenIntent(intent)

        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()
            val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
            val use24Hour = when (timeFormat) {
                TimeFormat.H24 -> true
                TimeFormat.H12 -> false
                null -> DateFormat.is24HourFormat(LocalContext.current)
            }
            val dark = when (theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            ResurgeTheme(colorScheme = if (dark) ColorScheme.Dark else ColorScheme.Light) {
                CompositionLocalProvider(LocalUse24HourClock provides use24Hour) {
                    val openInsights by viewModel.openInsights.collectAsStateWithLifecycle()
                    val askNotifications by viewModel.askNotifications.collectAsStateWithLifecycle()
                    Screen {
                        AppRoot(
                            session = session,
                            openInsightsSignal = openInsights,
                            askNotifications = askNotifications,
                            onNotificationPromptShown = viewModel::onNotificationPromptShown,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /** Notification taps carry open=insights (both our own and system-shown FCM notifications). */
    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.getStringExtra(ResurgeMessagingService.EXTRA_OPEN) == ResurgeMessagingService.OPEN_INSIGHTS) {
            viewModel.requestOpenInsights()
            intent.removeExtra(ResurgeMessagingService.EXTRA_OPEN)
        }
    }
}

/** Asks once (Android 13+) for permission to show insight notifications. */
@Composable
private fun NotificationPermissionPrompt(ask: Boolean, onShown: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onShown() }
    LaunchedEffect(ask) {
        if (!ask) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onShown()
        }
    }
}

/** Switches between top-level flows based on auth + profile state. */
@Composable
private fun AppRoot(
    session: SessionState,
    openInsightsSignal: Int,
    askNotifications: Boolean,
    onNotificationPromptShown: () -> Unit,
) {
    AnimatedContent(
        targetState = session,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        // Only animate when the flow changes, not on every profile update within a flow.
        contentKey = { it::class },
        label = "app-root",
    ) { state ->
        when (state) {
            SessionState.Loading -> Unit // splash screen is still showing
            SessionState.SignedOut -> SignInRoute()
            is SessionState.NeedsOnboarding -> OnboardingRoute(profile = state.profile)
            is SessionState.Ready -> {
                NotificationPermissionPrompt(askNotifications, onNotificationPromptShown)
                MainScreen(profile = state.profile, openInsightsSignal = openInsightsSignal)
            }
        }
    }
}
