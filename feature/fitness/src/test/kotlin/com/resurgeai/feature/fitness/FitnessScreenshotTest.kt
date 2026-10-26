package com.resurgeai.feature.fitness

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.data.cardio.CardioPointsState
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.CardioStatus
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Record with :feature:fitness:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1400dp-xhdpi")
class FitnessScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneId.systemDefault()
    private fun at(day: LocalDate, hour: Int, minutes: Long) =
        day.atTime(hour, 0).atZone(zone).toInstant().let { it to it.plusSeconds(minutes * 60) }

    private val workouts = listOf(
        at(today, 7, 31).let { (s, e) -> Workout("a", WorkoutSource.HEALTH_CONNECT, "run", null, s, e, WorkoutMetricsInput(distanceM = 5240.0, kcal = 342, avgHr = 152), sourceName = "Pixel Watch 3") },
        at(today, 18, 55).let { (s, e) ->
            Workout("b", WorkoutSource.MANUAL, "weights", null, s, e, WorkoutMetricsInput(kcal = 280, rpe = 8, exercises = listOf(
                StrengthExercise("Bench press", listOf(StrengthSet(8, 60.0), StrengthSet(8, 60.0), StrengthSet(6, 65.0))),
                StrengthExercise("Pull-ups", listOf(StrengthSet(10), StrengthSet(8))),
            )))
        },
        at(today.minusDays(1), 6, 45).let { (s, e) -> Workout("c", WorkoutSource.HEALTH_CONNECT, "weights", null, s, e, WorkoutMetricsInput(kcal = 210, avgHr = 118), sourceName = "Galaxy Watch6") },
        at(today.minusDays(1), 19, 60).let { (s, e) -> Workout("d", WorkoutSource.MANUAL, "yoga", null, s, e, WorkoutMetricsInput(kcal = 180, rpe = 4)) },
    )

    private val state = FitnessUiState(
        loading = false,
        cardio = CardioPointsState(
            days = CardioPointsEngine.calculate(today.minusDays(29), today, zone, today.atTime(23, 0).atZone(zone).toInstant(), emptyList(), workouts),
            loading = false, status = CardioStatus.LIMITED_DATA, zone = zone.id,
        ),
        days = workouts.groupBy { it.date(zone) }.toList().sortedByDescending { it.first },
        // Wed 14 Oct: the week runs Sun 11 to Sat 17.
        week = WeekSummary(workouts = 4, activeMinutes = 96, kcal = 1012, dailyMinutes = listOf(0, 34, 0, 62, 0, 0, 0), todayIndex = 3),
        bests = mapOf("a" to PersonalBest.FastestPace(355)),
        today = today,
    )

    private fun capture(name: String, s: FitnessUiState, scheme: ColorScheme = ColorScheme.Light) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { FitnessScreen(s, onOpenWorkout = {}, onLogWorkout = {}, onDelete = {}, onUndo = {}, onUndoTimeout = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("src/test/screenshots/fitness_$name.png")
    }

    @Test fun feed() = capture("feed", state)
    @Test fun feedDark() = capture("feed_dark", state, ColorScheme.Dark)
    @Test fun empty() = capture("empty", state.copy(days = emptyList(), week = WeekSummary(0, 0, 0, todayIndex = 3), bests = emptyMap(),
        cardio = state.cardio.copy(days = state.cardio.days.map { it.copy(contributions = emptyList()) })))
}
