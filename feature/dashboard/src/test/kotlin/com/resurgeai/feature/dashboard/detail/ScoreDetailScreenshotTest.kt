package com.resurgeai.feature.dashboard.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.EffortInputs
import com.resurgeai.core.model.FuelInputs
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.RecoveryInputs
import com.resurgeai.core.model.ScoreKind
import com.resurgeai.core.model.ScoreMath
import com.resurgeai.core.model.SessionKind
import com.resurgeai.feature.dashboard.DayScores
import com.resurgeai.feature.dashboard.ScoreHistory
import com.resurgeai.feature.dashboard.ScorePeriod
import com.resurgeai.feature.dashboard.ScoreRings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate

/** Renders the score rings and detail with synthetic days. Record with :feature:dashboard:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1300dp-xhdpi")
class ScoreDetailScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 24)

    private val history: ScoreHistory = run {
        val first = today.withDayOfMonth(1).minusMonths(1)
        val days = mutableListOf<DayScores>()
        var d = first
        var i = 0
        var prev: com.resurgeai.core.model.Score? = null
        while (!d.isAfter(today)) {
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val isToday = d == today
            val recovery = ScoreMath.recovery(
                RecoveryInputs(
                    d, asleepMin = 380 + (i * 37) % 110, sleepGoalMin = 420,
                    restingHr = 58.0 + (i * 7) % 5, restingHrNormal = 60.0, hrv = 38.0 + (i * 11) % 12, hrvNormal = 44.0,
                    bedtimeShiftMin = (i * 23) % 70, yesterdayEffort = prev?.value, yesterdayBand = prev?.band, historyDays = 30,
                ),
            )
            val session = if (d.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) {
                PlanDay(d.dayOfWeek, rest = false, title = "Full body", kind = SessionKind.STRENGTH, durationMin = 45)
            } else null
            val trained = session != null && !isToday && i % 7 != 3
            val effort = ScoreMath.effort(
                EffortInputs(
                    d, load = if (trained) 30.0 + (i * 13) % 40 else 0.0, loadNormal = 40.0,
                    workoutMin = if (trained) 50 else 0, steps = if (isToday) 3_400 else 5_000L + (i * 1_731) % 7_000, stepsGoal = 10_000,
                    session = session, sessionDone = if (trained) 1.0 else 0.0, recovery = recovery.value, soFar = isToday,
                ),
            )
            val fuel = ScoreMath.fuel(
                FuelInputs(
                    d, kcal = if (isToday) 1_150.0 else if (weekend) 2_700.0 else 1_950.0 + (i * 61) % 300,
                    kcalTarget = 2_100, proteinG = if (isToday) 48.0 else if (weekend) 80.0 else 110.0 + (i * 7) % 30, proteinTarget = 130,
                    meals = if (weekend) 1 else 3, mealsGoal = 3, waterMl = 1_800 + (i * 170) % 900, waterTarget = 2_500,
                    dayShare = if (isToday) 0.55 else 1.0,
                ),
            )
            days += DayScores(recovery, effort, fuel)
            prev = effort
            d = d.plusDays(1); i++
        }
        ScoreHistory(loaded = true, today = today, days = days, hrvAllowed = true)
    }

    private fun capture(name: String, scheme: ColorScheme = ColorScheme.Dark, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = true
        compose.setContent { ResurgeTheme(colorScheme = scheme) { Screen { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/score_$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun detail(kind: ScoreKind, period: ScorePeriod, h: ScoreHistory = history) =
        ScoreDetailScreen(kind, period, h, onBack = {}, onPeriodChange = {})

    @Test fun rings() = capture("rings") {
        Column(Modifier.padding(16.dp)) { ScoreRings(history, ScorePeriod.TODAY, {}, {}) }
    }

    @Test fun ringsWeek() = capture("rings_week") {
        Column(Modifier.padding(16.dp)) { ScoreRings(history, ScorePeriod.WEEK, {}, {}) }
    }

    @Test fun recoveryToday() = capture("recovery_today") { detail(ScoreKind.RECOVERY, ScorePeriod.TODAY) }

    @Test fun recoveryTodayLight() = capture("recovery_today_light", ColorScheme.Light) { detail(ScoreKind.RECOVERY, ScorePeriod.TODAY, history.copy(hrvAllowed = false)) }

    @Test fun effortToday() = capture("effort_today") { detail(ScoreKind.EFFORT, ScorePeriod.TODAY) }

    @Test fun effortWeek() = capture("effort_week") { detail(ScoreKind.EFFORT, ScorePeriod.WEEK) }

    @Test fun fuelToday() = capture("fuel_today") { detail(ScoreKind.FUEL, ScorePeriod.TODAY) }

    @Test fun fuelMonth() = capture("fuel_month") { detail(ScoreKind.FUEL, ScorePeriod.MONTH) }
}
