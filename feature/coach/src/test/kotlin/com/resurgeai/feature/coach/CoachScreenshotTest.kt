package com.resurgeai.feature.coach

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Record with :feature:coach:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class CoachScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val insights = CoachUiState(
        tab = CoachTab.INSIGHTS,
        insights = CoachViewModel(androidx.lifecycle.SavedStateHandle()).state.value.insights,
    )

    private fun capture(name: String, state: CoachUiState, scheme: ColorScheme) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { CoachScreen(state, onBack = {}, onTabChange = {}, onSend = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/coach_$name.png")
    }

    @Test fun insightsLight() = capture("insights_light", insights, ColorScheme.Light)

    @Test fun chatEmptyDark() = capture("chat_empty_dark", insights.copy(tab = CoachTab.CHAT), ColorScheme.Dark)

    @Test fun chatLight() = capture(
        "chat_light",
        insights.copy(
            tab = CoachTab.CHAT,
            messages = listOf(
                ChatMessage(0, Author.USER, "Why am I so tired today?"),
                ChatMessage(1, Author.COACH, CoachViewModel.PLACEHOLDER_REPLY),
                ChatMessage(2, Author.USER, "Should I still work out?"),
            ),
            replying = true,
        ),
        ColorScheme.Light,
    )
}
