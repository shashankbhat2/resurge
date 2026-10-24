package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TrainingTest {
    private val start = LocalDate.of(2026, 10, 26)

    @Test
    fun `losing pace is steady up to 0_75 kg, ambitious to 1 kg, then too fast`() {
        assertEquals(PaceSignal.UP, GoalMath.paceSignal(gaining = false, kgPerWeek = 0.5))
        assertEquals(PaceSignal.UP, GoalMath.paceSignal(gaining = false, kgPerWeek = 0.75))
        assertEquals(PaceSignal.MID, GoalMath.paceSignal(gaining = false, kgPerWeek = 0.9))
        assertEquals(PaceSignal.DOWN, GoalMath.paceSignal(gaining = false, kgPerWeek = 1.2))
    }

    @Test
    fun `gaining pace has tighter limits`() {
        assertEquals(PaceSignal.UP, GoalMath.paceSignal(gaining = true, kgPerWeek = 0.25))
        assertEquals(PaceSignal.MID, GoalMath.paceSignal(gaining = true, kgPerWeek = 0.5))
        assertEquals(PaceSignal.DOWN, GoalMath.paceSignal(gaining = true, kgPerWeek = 0.75))
    }

    @Test
    fun `weight goal follows the target and timeframe`() {
        val goal = MajorGoal(TrainingGoal.LOSE_FAT, weeks = 12, startDate = start, startKg = 84.0, targetKg = 78.0)
        assertEquals(0.5, goal.paceKgPerWeek!!, 1e-9)
        assertEquals(PaceSignal.UP, goal.paceSignal)
        assertEquals(LocalDate.of(2027, 1, 18), goal.endDate)
        val w = goal.weightGoal()!!
        assertEquals(84.0, w.startKg, 0.0)
        assertEquals(78.0, w.goalKg, 0.0)
        assertEquals(0.5, w.paceKgPerWeek, 1e-9)
    }

    @Test
    fun `goals without a weight change have no weight goal or pace`() {
        assertNull(MajorGoal(TrainingGoal.RUN_FURTHER, 12, start, raceKm = 10.0).weightGoal())
        assertNull(MajorGoal(TrainingGoal.RUN_FURTHER, 12, start, raceKm = 10.0).paceSignal)
        assertNull(MajorGoal(TrainingGoal.LOSE_FAT, 12, start, startKg = 80.0, targetKg = 80.0).weightGoal())
    }

    @Test
    fun `custom durations go week by week to 12, then month by month to a year`() {
        assertEquals(listOf(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 17, 22, 26, 30, 35, 39, 43, 48, 52), MajorGoal.DURATIONS)
        assertEquals("12 weeks", MajorGoal.durationLabel(12))
        assertEquals("6 months", MajorGoal.durationLabel(26))
        assertEquals("12 months", MajorGoal.durationLabel(52))
        // From a quick pick that's off the grid (16 weeks), steps land on the neighbouring notches.
        assertEquals(17, MajorGoal.stepDuration(16, 1))
        assertEquals(13, MajorGoal.stepDuration(16, -1))
        assertEquals(52, MajorGoal.stepDuration(52, 1))
        assertEquals(4, MajorGoal.stepDuration(4, -1))
    }

    @Test
    fun `weekday wire names round-trip`() {
        DayOfWeek.entries.forEach { assertEquals(it, dayOfWeekFromWire(it.wire)) }
        assertEquals("mon", DayOfWeek.MONDAY.wire)
    }

    @Test
    fun `parses a plan as the service returns it`() {
        val plan = WorkoutPlan.fromMap(
            "p1",
            mapOf(
                "name" to "Lean 12",
                "summary" to "Two strength days.",
                "progression" to "Add a rep.",
                "notes" to "",
                "days" to listOf(
                    mapOf("day" to "tue", "rest" to true, "title" to "Rest", "note" to "Walk 8,000 steps"),
                    mapOf(
                        "day" to "mon", "rest" to false, "kind" to "strength", "activityId" to "weights",
                        "title" to "Strength A", "durationMin" to 45L, "focus" to "Full body", "target" to "",
                        "exercises" to listOf(mapOf("name" to "Goblet squat", "sets" to 3L, "reps" to "10", "load" to "RPE 7", "restSec" to 90L)),
                    ),
                    mapOf("day" to "funday", "rest" to true),
                ),
            ),
        )!!
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), plan.days.map { it.day })
        val mon = plan.days.first()
        assertEquals(SessionKind.STRENGTH, mon.kind)
        assertEquals(45, mon.durationMin)
        assertEquals(PlanExercise("Goblet squat", 3, "10", "RPE 7", 90), mon.exercises.single())
        assertEquals(listOf("Strength A"), plan.sessions.map { it.title })
        assertTrue(plan.days[1].rest)
    }

    @Test
    fun `a plan without days is unusable`() {
        assertNull(WorkoutPlan.fromMap("p", mapOf("name" to "Empty")))
    }

    private fun workout(activityId: String) = Workout(
        "w", WorkoutSource.MANUAL, activityId, null,
        java.time.Instant.parse("2026-10-26T07:00:00Z"), java.time.Instant.parse("2026-10-26T07:45:00Z"), WorkoutMetricsInput(),
    )

    @Test
    fun `planned days match the right kind of workout`() {
        val strength = PlanDay(DayOfWeek.MONDAY, rest = false, title = "Strength A", kind = SessionKind.STRENGTH)
        val run = PlanDay(DayOfWeek.WEDNESDAY, rest = false, title = "Easy run", kind = SessionKind.RUN)
        val rest = PlanDay(DayOfWeek.TUESDAY, rest = true, title = "Rest")
        assertTrue(PlanMatch.matches(strength, workout("weights")))
        assertTrue(PlanMatch.matches(strength, workout("yoga")))
        assertFalse(PlanMatch.matches(strength, workout("run")))
        assertTrue(PlanMatch.matches(run, workout("treadmill")))
        assertFalse(PlanMatch.matches(run, workout("cycle")))
        assertFalse(PlanMatch.matches(rest, workout("walk")))
    }

    @Test
    fun `planned sets take the plan's reps and last time's top weight`() {
        assertEquals(8, PlanSets.reps("8-10"))
        assertEquals(40, PlanSets.reps("40 s"))
        assertEquals(10, PlanSets.reps("AMRAP"))
        val sets = PlanSets.planned(PlanExercise("Bench press", 3, "8", "RPE 7", 90), listOf(StrengthSet(8, 55.0), StrengthSet(8, 57.5)))
        assertEquals(List(3) { StrengthSet(8, 57.5) }, sets)
        assertEquals(StrengthSet(12, null), PlanSets.planned(PlanExercise("Push-up", 2, "12", "bodyweight", 60), null).first())
    }

    @Test
    fun `plan names are plain words from the goal and schedule`() {
        val gym = TrainingPrefs()
        assertEquals("Lose fat · 4 days a week", PlanNames.of(MajorGoal(TrainingGoal.LOSE_FAT, 12, start, 84.0, 78.0), gym))
        assertEquals(
            "Build muscle at home · 3 days a week",
            PlanNames.of(MajorGoal(TrainingGoal.BUILD_MUSCLE, 12, start), gym.copy(location = TrainingLocation.HOME, days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))),
        )
        assertEquals("Run a 10K with no equipment · 4 days a week", PlanNames.of(MajorGoal(TrainingGoal.RUN_FURTHER, 12, start, raceKm = 10.0), gym.copy(location = TrainingLocation.NONE)))
    }
}
