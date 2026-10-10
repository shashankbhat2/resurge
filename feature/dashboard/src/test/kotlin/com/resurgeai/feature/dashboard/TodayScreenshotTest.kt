package com.resurgeai.feature.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.HeartRateSummary
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Today dashboard with synthetic data. Record with :feature:dashboard:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1500dp-xhdpi")
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-10T08:00:00Z")
    private val state = DashboardUiState(
        profile = UserProfile("u", "Shashank Bhat", null, null, setOf(Goal.SLEEP_BETTER), Targets(), true),
        availability = HealthConnectAvailability.AVAILABLE,
        permissions = PermissionState.ALL,
        today = DailyHealth(
            date = LocalDate.of(2026, 10, 10),
            steps = 7_412,
            distanceMeters = 5_430.0,
            caloriesTotal = 2_310.0,
            caloriesActive = 540.0,
            sleep = listOf(
                SleepSession(
                    now.minus(Duration.ofHours(8)), now.minus(Duration.ofMinutes(26)),
                    mapOf(
                        SleepStage.DEEP to Duration.ofMinutes(80), SleepStage.LIGHT to Duration.ofMinutes(250),
                        SleepStage.REM to Duration.ofMinutes(66), SleepStage.AWAKE to Duration.ofMinutes(18),
                    ),
                    null,
                ),
            ),
            exercise = listOf(ExerciseSession("e", "Running", null, now, now.plus(Duration.ofMinutes(42)), null)),
            heartRate = HeartRateSummary(minBpm = 52, avgBpm = 84, maxBpm = 164, restingBpm = 58),
            weightKg = 76.7,
            sources = emptyMap(),
        ),
        sparklines = mapOf(
            HealthMetric.HEART_RATE to listOf(80.0, 78.0, 83.0, 81.0, 86.0, 82.0, 84.0),
            HealthMetric.DISTANCE to listOf(4.1, 6.3, 2.2, 7.8, 5.0, 8.4, 5.43),
            HealthMetric.WEIGHT to listOf(77.2, null, 77.0, null, 76.9, null, 76.7),
        ),
        exerciseWeek = listOf(true, false, true, true, false, true, false),
        todayIndex = 5,
    )

    private fun capture(name: String, scheme: ColorScheme) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { TodayScreen(state, onConnect = {}, onInstall = {}, onRetry = {}, onOpenMetric = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(1_000) // let ring reveal animations finish
        compose.onRoot().captureRoboImage("src/test/screenshots/today_$name.png")
    }

    @Test fun todayLight() = capture("light", ColorScheme.Light)

    @Test fun todayDark() = capture("dark", ColorScheme.Dark)
}
