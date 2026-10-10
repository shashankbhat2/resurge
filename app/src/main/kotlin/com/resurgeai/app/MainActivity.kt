package com.resurgeai.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.theme.ColorScheme
import com.resurgeai.app.main.MainScreen
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
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

        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val session by viewModel.session.collectAsStateWithLifecycle()
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
                Screen { AppRoot(session) }
            }
        }
    }
}

/** Switches between top-level flows based on auth + profile state. */
@Composable
private fun AppRoot(session: SessionState) {
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
            is SessionState.Ready -> MainScreen(profile = state.profile)
        }
    }
}
