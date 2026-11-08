package com.resurgeai.feature.fitness

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.data.cardio.CardioPointsState
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h1100dp-xhdpi")
class CardioPointsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 14)
    private val state = CardioPointsState(
        days = (0L..29L).map { offset -> DailyCardioPoints(today.minusDays(offset), listOf(
            CardioContribution(CardioMethod.CADENCE, "Pixel Watch", null, null, 1_200_000, 1_200_000),
            CardioContribution(CardioMethod.ACTIVITY_ESTIMATE, "Manual workout", "cycle", "Cycle", 600_000, 1_200_000),
        ), calculatedAt = Instant.parse("2026-10-14T10:00:00Z")) },
        loading = false, status = CardioStatus.READY, zone = "UTC",
    )
    private fun show(
        s: CardioPointsState = state,
        scheme: ColorScheme = ColorScheme.Light,
        scale: Float = 1f,
        details: Boolean = false,
        use24Hour: Boolean = true,
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale), LocalUse24HourClock provides use24Hour) {
                ResurgeTheme(colorScheme = scheme) {
                    Screen {
                        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = if (details) Alignment.BottomCenter else Alignment.TopStart) {
                            if (details) CardioPointsDetailsContent(s, today, onRetry, {})
                            else CardioPointsCard(s, today, onRetry)
                        }
                    }
                }
            }
        }
    }
    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/cardio_$name.png")
    @Test fun populated() { show(); capture("populated") }
    @Test fun dark() { show(scheme = ColorScheme.Dark); capture("dark") }
    @Test fun empty() { show(state.copy(days = emptyList(), status = CardioStatus.LIMITED_DATA)); capture("empty") }
    @Test fun stale() { show(state.copy(status = CardioStatus.STALE)); capture("stale") }
    @Test fun permissionDenied() { show(state.copy(status = CardioStatus.PERMISSION_REQUIRED)); capture("permission") }
    @Test fun largeText() { show(scale = 1.5f); capture("large_text") }
    @Test fun details() { show(details = true); capture("details") }
    @Test fun detailsDark() { show(details = true, scheme = ColorScheme.Dark); capture("details_dark") }
    @Test fun detailsLargeText() { show(details = true, scale = 1.5f); capture("details_large_text") }
    @Test fun partialWeek() {
        show(details = true)
        compose.onNodeWithContentDescription("Next week").assertIsNotEnabled()
        repeat(4) { compose.onNodeWithContentDescription("Previous week").performClick() }
        compose.onNodeWithText("13 Sep – 19 Sep").assertIsDisplayed()
        compose.onNodeWithText("Partial week").assertIsDisplayed()
        compose.onNodeWithText("points that week").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous week").assertIsNotEnabled()
        capture("partial")
        repeat(4) { compose.onNodeWithContentDescription("Next week").performClick() }
        compose.onNodeWithContentDescription("Next week").assertIsNotEnabled()
    }
    @Test fun summaryKeepsNavigationAndExplanationsInDetails() {
        show()
        compose.onNodeWithContentDescription("Previous week").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh points").assertDoesNotExist()
        compose.onNodeWithText("How points work").assertDoesNotExist()
        compose.onNodeWithContentDescription("View Cardio Points details").performClick()
        compose.onNodeWithContentDescription("Previous week").assertIsDisplayed()
        compose.onNodeWithText("Wed, 14 Oct").performTouchInput { click() }
        compose.onNodeWithContentDescription("Close Cardio Points").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Previous week").assertDoesNotExist()
        compose.onNodeWithContentDescription("View Cardio Points details").assertIsDisplayed()
    }
    @Test fun detailsExplainEstimatesAndPrivacyOnDemand() {
        show(details = true)
        val explanation = "Earn 1 point per minute of moderate activity, or 2 for vigorous activity."
        compose.onNodeWithText(explanation).assertDoesNotExist()
        compose.onNodeWithText("How points work").performScrollTo().performClick()
        compose.onNodeWithText(explanation).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Last 30 days", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("How points work").performScrollTo().performClick()
        compose.onNodeWithText(explanation).assertDoesNotExist()
    }
    @Test fun daySelectionShowsTheChosenDate() {
        show(details = true)
        compose.onNodeWithContentDescription("2026-10-12: 40 points").performClick()
        compose.onNodeWithText("Mon, 12 Oct").assertIsDisplayed()
    }
    @Test fun updatedTimeRespectsTwelveHourPreference() {
        show(details = true, use24Hour = false)
        compose.onNodeWithText("Updated 14 Oct, 10:00 AM").assertIsDisplayed()
    }
    @Test fun updatedTimeRespectsTwentyFourHourPreference() {
        show(details = true)
        compose.onNodeWithText("Updated 14 Oct, 10:00").assertIsDisplayed()
    }
    @Test fun refreshCanBeRetriedFromDetails() {
        var retries = 0
        show(details = true, onRetry = { retries++ })
        compose.onNodeWithContentDescription("Refresh points").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
    @Test fun refreshIsDisabledWhileUpdating() {
        show(state.copy(refreshing = true), details = true)
        compose.onNodeWithContentDescription("Refresh points").assertIsNotEnabled()
    }
}
