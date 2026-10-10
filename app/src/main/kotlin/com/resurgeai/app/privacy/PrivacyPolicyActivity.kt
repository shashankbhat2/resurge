package com.resurgeai.app.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme

/**
 * Explains how Resurge uses health data. Health Connect opens this from its permission screens
 * (rationale intent on Android 13-, VIEW_PERMISSION_USAGE alias on 14+).
 * TODO(launch): link the hosted privacy policy URL before Play review.
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResurgeTheme {
                Screen { PrivacyPolicy(onBack = ::finish) }
            }
        }
    }
}

@Composable
private fun PrivacyPolicy(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar("How Resurge uses your data", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp),
        ) {
            Section(
                "What we read",
                "With your permission, Resurge reads steps, distance, calories burned, sleep, exercise sessions, " +
                    "heart rate, resting heart rate and weight from Health Connect.",
            )
            Section(
                "Why",
                "To show your daily dashboard, identify whether data came from your phone or a wearable, and give " +
                    "your AI coach the context it needs to spot trends and suggest improvements.",
            )
            Section(
                "What leaves your device",
                "Daily totals (for example: steps per day, minutes slept, average heart rate) and a summary of each " +
                    "finished workout and night of sleep (duration, distance, pace, average and peak heart rate, sleep " +
                    "stages, bedtime) are stored in your private Resurge account. Raw samples stay on your phone. " +
                    "To write insights and answer your questions, these summaries are sent to our AI provider (Anthropic).",
            )
            Section(
                "What we never do",
                "We don't sell your data, use it for advertising, or share it with anyone except as needed to run the " +
                    "features above.",
            )
            Section(
                "Your control",
                "You can revoke access at any time in Health Connect settings, and delete your Resurge data from " +
                    "the Profile tab.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = Resurge.type.headline)
    Spacer(Modifier.height(4.dp))
    Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted)
}
