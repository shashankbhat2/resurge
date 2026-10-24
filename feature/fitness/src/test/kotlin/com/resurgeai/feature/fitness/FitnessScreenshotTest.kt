package com.resurgeai.feature.fitness

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
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
    /** Mon/Wed/Fri strength, Sat run; today (Wed 14) still to do, Monday done by the logged lift. */
    private val plan = com.resurgeai.core.model.WorkoutPlan(
        id = "p", name = "Lean 12", summary = "", progression = "", notes = "",
        days = java.time.DayOfWeek.entries.map { d ->
            val ex = List(5) { com.resurgeai.core.model.PlanExercise(listOf("Goblet squat", "Bench press", "Romanian deadlift", "Seated row", "Plank")[it], 3, if (it == 4) "40 s" else "8", "RPE 7", 90) }
            when (d) {
                java.time.DayOfWeek.MONDAY -> com.resurgeai.core.model.PlanDay(d, false, "Strength A", com.resurgeai.core.model.SessionKind.STRENGTH, "weights", 45, exercises = ex)
                java.time.DayOfWeek.WEDNESDAY -> com.resurgeai.core.model.PlanDay(d, false, "Strength B", com.resurgeai.core.model.SessionKind.STRENGTH, "weights", 45, exercises = ex)
                java.time.DayOfWeek.FRIDAY -> com.resurgeai.core.model.PlanDay(d, false, "Strength A", com.resurgeai.core.model.SessionKind.STRENGTH, "weights", 45, exercises = ex)
                java.time.DayOfWeek.SATURDAY -> com.resurgeai.core.model.PlanDay(d, false, "Long easy run", com.resurgeai.core.model.SessionKind.RUN, "run", 45, focus = "Conversational pace")
                else -> com.resurgeai.core.model.PlanDay(d, true, "Rest", note = "Walk 8,000 steps")
            }
        },
    )
    private val planned get() = state.copy(
        plan = plan,
        planWeek = QuickLog.planWeek(plan, today.with(java.time.DayOfWeek.MONDAY), workouts.filter { it.id != "b" } +
            at(today.minusDays(2), 18, 42).let { (s, e) -> Workout("m", WorkoutSource.MANUAL, "weights", "Strength A", s, e, WorkoutMetricsInput()) }, zone),
    )

    @Test fun plannedDark() = capture("planned_dark", planned, ColorScheme.Dark)

    @Test fun quickLogDark() {
        compose.setContent {
            ResurgeTheme(colorScheme = ColorScheme.Dark) {
                Screen { QuickLogSheet(state.recents, onSave = { _, _, _ -> }, onAddDetails = {}, onAllActivities = {}, onDismiss = {}) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/fitness_quick_log_dark.png")
    }

    @Test fun sessionLoggerDark() {
        val logger = SessionLoggerState(
            loaded = true,
            day = plan.days[2],
            exercises = listOf(
                LoggedExercise("Front squat", listOf(StrengthSet(8, 57.5), StrengthSet(8, 57.5)), List(3) { LoggedSet(8, 57.5, done = true) }),
                LoggedExercise("Overhead press", null, listOf(LoggedSet(8, 35.0, done = true), LoggedSet(8, 35.0), LoggedSet(8, 35.0))),
                LoggedExercise("Push-up", null, List(3) { LoggedSet(12, null) }),
            ),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ResurgeTheme(colorScheme = ColorScheme.Dark) {
                SessionLoggerScreen(logger, onClose = {}, onToggle = { _, _ -> }, onReps = { _, _, _ -> }, onWeight = { _, _, _ -> }, onAddSet = {}, onAllDone = {}, onFinish = {})
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("src/test/screenshots/fitness_session_logger_dark.png")
    }

    @Test fun planScreenDark() {
        compose.setContent {
            ResurgeTheme(colorScheme = ColorScheme.Dark) {
                Screen {
                    PlanScreen(
                        PlanUiState(
                            loaded = true,
                            goal = com.resurgeai.core.model.MajorGoal(com.resurgeai.core.model.TrainingGoal.LOSE_FAT, 12, today.minusDays(9), 84.0, 78.0),
                            plan = plan.copy(summary = "Two strength days keep the muscle; two easy runs burn the fat.", progression = "Add a rep a week, then load when you hit the top of the range."),
                        ),
                        onBack = {}, onRebuild = { _, _ -> }, onAccept = {}, onDiscard = {}, today = today,
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/fitness_plan_dark.png")
    }

    @Test fun empty() = capture("empty", state.copy(days = emptyList(), week = WeekSummary(0, 0, 0, todayIndex = 3), bests = emptyMap()))
}
