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
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
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
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xhdpi")
class TodayScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-10T08:00:00Z")
    private val day = LocalDate.of(2026, 10, 10)
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
        latestInsight = com.resurgeai.core.model.CoachInsight(
            id = "i", kind = com.resurgeai.core.model.InsightKind.SLEEP, category = "SLEEP",
            title = "A little short on sleep",
            body = "You slept 40 minutes less than your weekly average, and your resting heart rate is up 3 bpm. Keep today's training light and aim to be in bed by 10:30.",
            highlights = emptyList(), suggestion = null, createdAt = now, weekly = null,
        ),
        meals = listOf(
            Meal(
                "m1", MealType.BREAKFAST, Instant.parse("2026-10-10T03:15:00Z"), day, MealSource.TEXT, "Masala omelette with toast",
                listOf(FoodItem("Omelette", 130.0, Nutrition(175.0, 11.0, 3.0, 13.0)), FoodItem("Toast", 60.0, Nutrition(250.0, 9.0, 43.0, 3.4))),
            ),
        ),
        workouts = listOf(
            Workout(
                "w1", WorkoutSource.HEALTH_CONNECT, "run", null, Instant.parse("2026-10-10T01:00:00Z"), Instant.parse("2026-10-10T01:42:00Z"),
                WorkoutMetricsInput(distanceM = 7_120.0, kcal = 468, avgHr = 151), sourceName = "Pixel Watch 3",
            ),
        ),
        waterMl = 1_000,
    )

    private fun capture(name: String, scheme: ColorScheme, ui: DashboardUiState = state) {
        compose.mainClock.autoAdvance = false // shimmer is an infinite animation
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen {
                    TodayScreen(ui, onConnect = {}, onInstall = {}, onRetry = {}, onOpenMetric = {}, today = day)
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000) // let ring reveal animations finish
        compose.onRoot().captureRoboImage("src/test/screenshots/today_$name.png")
    }

    @Test fun todayLight() = capture("light", ColorScheme.Light)

    @Test fun todayDark() = capture("dark", ColorScheme.Dark)

    @Test fun loadingLight() = capture("loading_light", ColorScheme.Light, state.copy(today = null))

    @Test fun loadingDark() = capture("loading_dark", ColorScheme.Dark, state.copy(today = null))
}
