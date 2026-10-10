package com.resurgeai.feature.dashboard.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.DailyValue
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Renders detail screens with synthetic data. Record with :feature:dashboard:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1500dp-xhdpi")
class MetricDetailScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private fun sleepNights(days: Int) = (0 until days).map { i ->
        val date = today.minusDays(i.toLong())
        val bed = date.minusDays(1).atTime(22, 0).plusMinutes((i % 3) * 50L + (i * 13) % 60).atZone(zone).toInstant()
        val hours = 6.5 + (i % 4) * 0.5
        SleepSession(
            bed, bed.plus(Duration.ofMinutes((hours * 60).toLong())),
            mapOf(
                SleepStage.DEEP to Duration.ofMinutes(70L + i * 3),
                SleepStage.LIGHT to Duration.ofMinutes(220),
                SleepStage.REM to Duration.ofMinutes(80),
                SleepStage.AWAKE to Duration.ofMinutes(15),
            ),
            null,
        )
    }

    private fun state(metric: HealthMetric, range: DetailRange, values: (Int) -> Double?): MetricDetailUiState {
        val first = range.firstDay(today)
        val days = java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1
        val series = MetricSeries(metric, (0 until days).map { DailyValue(first.plusDays(it.toLong()), values(it)) })
        val periods = if (range == DetailRange.WEEK) Periods.daily(series) else Periods.weekly(series)
        val workouts = (0 until days step 2).map { i ->
            val t = first.plusDays(i.toLong()).atTime(7, 0).atZone(zone).toInstant()
            ExerciseSession("$i", "Running", null, t, t.plus(Duration.ofMinutes(25L + i * 2)), null)
        }
        return MetricDetailUiState(
            metric = metric, range = range, loading = false, series = series, periods = periods,
            selected = periods.lastIndex,
            sleepSessions = if (metric == HealthMetric.SLEEP) sleepNights(days) else emptyList(),
            exerciseSessions = if (metric == HealthMetric.EXERCISE) workouts else emptyList(),
            heartRanges = if (metric == HealthMetric.HEART_RATE) (0 until days).associate { first.plusDays(it.toLong()) to (50L + it % 5 to 120L + (it * 7) % 45) } else emptyMap(),
        )
    }

    private fun capture(name: String, state: MetricDetailUiState, scheme: ColorScheme = ColorScheme.Light) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { MetricDetailScreen(state, onBack = {}, onRangeChange = {}, onSelect = {}, onOpenSources = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/detail_$name.png")
    }

    @Test fun sleepWeek() = capture("sleep_week", state(HealthMetric.SLEEP, DetailRange.WEEK) { 6.5 + (it % 4) * 0.5 })

    @Test fun sleepMonth() = capture("sleep_month", state(HealthMetric.SLEEP, DetailRange.MONTH) { 6.5 + (it % 4) * 0.5 })

    @Test fun stepsMonthDark() = capture("steps_month_dark", state(HealthMetric.STEPS, DetailRange.MONTH) { if (it % 6 == 5) null else 6000.0 + (it * 937) % 6000 }, ColorScheme.Dark)

    @Test fun heartWeek() = capture("heart_week", state(HealthMetric.HEART_RATE, DetailRange.WEEK) { 68.0 + it })

    @Test fun exerciseMonth() = capture("exercise_month", state(HealthMetric.EXERCISE, DetailRange.MONTH) { if (it % 2 == 0) 25.0 + it * 2 else null })
}
