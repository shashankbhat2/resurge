package com.resurgeai.feature.fitness

import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FitnessLogicTest {
    private val t0 = Instant.parse("2026-10-01T07:00:00Z")
    private fun workout(id: String, activity: String, day: Long, minutes: Long, metrics: WorkoutMetricsInput) =
        t0.plusSeconds(day * 86_400).let { s -> Workout(id, WorkoutSource.MANUAL, activity, null, s, s.plusSeconds(minutes * 60), metrics) }

    @Test
    fun `the fastest of two or more runs gets the pace best`() {
        val all = listOf(
            workout("slow", "run", 0, 30, WorkoutMetricsInput(distanceM = 5_000.0)),
            workout("fast", "run", 2, 27, WorkoutMetricsInput(distanceM = 5_000.0)),
        )
        val bests = PersonalBests.find(all)
        assertEquals(setOf("fast"), bests.keys)
        assertEquals(PersonalBest.FastestPace(324), bests["fast"])
    }

    @Test
    fun `a single run, or a short one, has nothing to beat`() {
        assertTrue(PersonalBests.find(listOf(workout("only", "run", 0, 30, WorkoutMetricsInput(distanceM = 5_000.0)))).isEmpty())
        val short = listOf(
            workout("a", "run", 0, 3, WorkoutMetricsInput(distanceM = 600.0)),
            workout("b", "run", 1, 30, WorkoutMetricsInput(distanceM = 5_000.0)),
        )
        assertTrue(PersonalBests.find(short).isEmpty())
    }

    @Test
    fun `more volume than the previous strength session is a best, less is nothing`() {
        fun lift(id: String, day: Long, kg: Double) =
            workout(id, "weights", day, 45, WorkoutMetricsInput(exercises = listOf(StrengthExercise("Bench", listOf(StrengthSet(10, kg), StrengthSet(10, kg))))))
        val bests = PersonalBests.find(listOf(lift("a", 0, 60.0), lift("b", 2, 65.0), lift("c", 4, 50.0)))
        assertEquals(mapOf("b" to PersonalBest.MoreVolume(100.0)), bests)
    }

    @Test
    fun `last session finds the latest earlier sets for each exercise`() {
        fun record(id: String, day: Long, sets: List<StrengthSet>, deleted: Boolean = false) = com.resurgeai.core.data.fitness.WorkoutRecord(
            id, WorkoutSource.MANUAL, "weights", null, t0.plusSeconds(day * 86_400), t0.plusSeconds(day * 86_400 + 2_700),
            WorkoutMetricsInput(exercises = listOf(StrengthExercise("Bench Press ", sets))), null, deleted,
        )
        val older = listOf(StrengthSet(8, 55.0))
        val newer = listOf(StrengthSet(8, 60.0), StrengthSet(8, 60.0))
        val records = listOf(record("a", 0, older), record("b", 3, newer), record("hidden", 4, listOf(StrengthSet(1, 200.0)), deleted = true))
        val found = LastSession.find(records, "weights", t0.plusSeconds(5 * 86_400), excludeId = null)
        assertEquals(mapOf("bench press" to newer), found)
        // Editing session b itself: its own sets don't count as "last time".
        assertEquals(mapOf("bench press" to older), LastSession.find(records, "weights", t0.plusSeconds(3 * 86_400 + 1), excludeId = "b"))
    }

    @Test
    fun `last session reads as sets by reps at weight`() {
        val kg: (Double) -> String = { "${UnitFormat.decimal(it, 1)} kg" }
        assertEquals("3 × 8 at 60 kg", LastSession.describe(List(3) { StrengthSet(8, 60.0) }, kg))
        assertEquals("3 sets, top 62.5 kg × 7", LastSession.describe(listOf(StrengthSet(8, 60.0), StrengthSet(8, 60.0), StrengthSet(7, 62.5)), kg))
        assertEquals("2 × 10", LastSession.describe(List(2) { StrengthSet(10) }, kg))
    }

    @Test
    fun `quick add offers the most logged activities first, topped up with common ones`() {
        val all = listOf(
            workout("1", "yoga", 0, 30, WorkoutMetricsInput()),
            workout("2", "yoga", 1, 30, WorkoutMetricsInput()),
            workout("3", "cycle", 2, 30, WorkoutMetricsInput()),
            workout("4", "other", 3, 30, WorkoutMetricsInput()),
        )
        assertEquals(listOf("yoga", "cycle", "walk", "run", "weights", "swim_pool"), QuickLog.recents(all))
    }

    @Test
    fun `the plan's week is matched to fitting workouts on each day`() {
        val monday = LocalDate.of(2026, 10, 5)
        val zone = java.time.ZoneOffset.UTC
        val plan = com.resurgeai.core.model.WorkoutPlan(
            "p", "Plan", "", java.time.DayOfWeek.entries.map { d ->
                when (d) {
                    java.time.DayOfWeek.MONDAY -> com.resurgeai.core.model.PlanDay(d, false, "Strength A", com.resurgeai.core.model.SessionKind.STRENGTH)
                    java.time.DayOfWeek.WEDNESDAY -> com.resurgeai.core.model.PlanDay(d, false, "Easy run", com.resurgeai.core.model.SessionKind.RUN)
                    else -> com.resurgeai.core.model.PlanDay(d, true, "Rest")
                }
            }, "", "",
        )
        val start = monday.atTime(7, 0).toInstant(zone)
        val lift = Workout("l", WorkoutSource.MANUAL, "weights", null, start, start.plusSeconds(2700), WorkoutMetricsInput())
        val walkWednesday = start.plusSeconds(2 * 86_400).let { Workout("w", WorkoutSource.MANUAL, "walk", null, it, it.plusSeconds(1800), WorkoutMetricsInput()) }
        val week = QuickLog.planWeek(plan, monday, listOf(lift, walkWednesday), zone)
        assertEquals(7, week.size)
        assertEquals("l", week.first { it.day.title == "Strength A" }.workout?.id)
        // A walk isn't the planned run.
        assertEquals(null, week.first { it.day.title == "Easy run" }.workout)
    }
}
