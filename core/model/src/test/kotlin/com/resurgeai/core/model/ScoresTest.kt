package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScoresTest {
    private val day = LocalDate.of(2026, 10, 24)
    private val session = PlanDay(DayOfWeek.SATURDAY, rest = false, title = "Full body", kind = SessionKind.STRENGTH, durationMin = 45)

    private fun recovery(
        asleepMin: Int? = 444,
        restingHr: Double? = 58.0,
        hrv: Double? = 41.0,
        shift: Int? = 48,
        yesterday: Int? = 64,
        history: Int = 30,
    ) = ScoreMath.recovery(
        RecoveryInputs(
            date = day, asleepMin = asleepMin, sleepGoalMin = 420,
            restingHr = restingHr, restingHrNormal = 60.0, hrv = hrv, hrvNormal = 44.0,
            bedtimeShiftMin = shift, yesterdayEffort = yesterday, yesterdayBand = 55..80, historyDays = history,
        ),
    )

    @Test
    fun `good sleep and a calm heart make a ready day, held back by bedtime`() {
        val s = recovery()
        assertEquals(PaceSignal.UP, s.signal)
        assertEquals("Ready to train", s.headline)
        assertTrue(s.value!! in 67..100)
        assertTrue(s.reason, s.reason.startsWith("You slept well"))
        assertEquals(1.0, s.parts.sumOf { it.weight }, 1e-9)
        assertTrue(s.parts.first { it.key == "sleep" }.impact > 0)
        assertEquals(0, s.parts.first { it.key == "yesterday" }.impact)
    }

    @Test
    fun `recovery needs last night's sleep`() {
        val s = recovery(asleepMin = null)
        assertNull(s.value)
        assertEquals("Needs last night’s sleep", s.missing)
    }

    @Test
    fun `missing HRV is left out and its weight shared`() {
        val s = recovery(hrv = null)
        assertFalse(s.parts.any { it.key == "hrv" })
        assertEquals(1.0, s.parts.sumOf { it.weight }, 1e-9)
        assertTrue(s.value != null)
    }

    @Test
    fun `short sleep and a raised heart rate mean rest`() {
        val s = recovery(asleepMin = 240, restingHr = 70.0, hrv = 30.0, shift = 120)
        assertEquals(PaceSignal.DOWN, s.signal)
        assertTrue(s.reason, s.reason.contains("short sleep"))
    }

    @Test
    fun `only a day well over its target costs recovery`() {
        val calm = recovery(yesterday = 85).parts.first { it.key == "yesterday" }
        val hard = recovery(yesterday = 120).parts.first { it.key == "yesterday" }
        assertEquals(0, calm.impact)
        assertTrue(hard.impact < 0)
    }

    @Test
    fun `under a week of history is still learning`() {
        assertTrue(recovery(history = 3).learning)
        assertFalse(recovery(history = 10).learning)
    }

    @Test
    fun `effort band drops with recovery on session days`() {
        assertEquals(55..80, ScoreMath.effortBand(sessionDay = true, recovery = 72))
        assertEquals(45..70, ScoreMath.effortBand(sessionDay = true, recovery = 50))
        assertEquals(30..55, ScoreMath.effortBand(sessionDay = true, recovery = 20))
        assertEquals(15..40, ScoreMath.effortBand(sessionDay = false, recovery = 50))
    }

    private fun effort(load: Double, minutes: Int, steps: Long?, done: Double, soFar: Boolean, recovery: Int? = 72, plan: PlanDay? = session) =
        ScoreMath.effort(
            EffortInputs(day, load, loadNormal = 40.0, workoutMin = minutes, steps = steps, stepsGoal = 10_000, session = plan, sessionDone = done, recovery = recovery, soFar = soFar),
        )

    @Test
    fun `a usual session day lands in its band`() {
        val s = effort(load = 40.0, minutes = 50, steps = 8_000, done = 1.0, soFar = false)
        assertTrue("${s.value}", s.value!! in s.band!!)
        assertEquals(PaceSignal.UP, s.signal)
    }

    @Test
    fun `effort so far stays grey below its band`() {
        val s = effort(load = 0.0, minutes = 0, steps = 3_000, done = 0.0, soFar = true)
        assertTrue(s.soFar)
        assertNull(s.signal)
        assertEquals("Full body to go", s.headline)
    }

    @Test
    fun `well over on a low recovery day is flagged`() {
        val s = effort(load = 120.0, minutes = 120, steps = 15_000, done = 1.0, soFar = false, recovery = 20)
        assertEquals(PaceSignal.DOWN, s.signal)
    }

    @Test
    fun `a missed session day finishes under`() {
        val s = effort(load = 0.0, minutes = 0, steps = 4_000, done = 0.0, soFar = false)
        assertEquals(PaceSignal.MID, s.signal)
        assertEquals("Full body was planned and not done.", s.reason)
    }

    @Test
    fun `a walk on a rest day is in range`() {
        val s = effort(load = 0.0, minutes = 0, steps = 10_000, done = 0.0, soFar = false, plan = null)
        assertTrue("${s.value}", s.value!! in 15..40)
        assertEquals(PaceSignal.UP, s.signal)
    }

    @Test
    fun `load uses logged effort, then heart rate`() {
        assertEquals(42.0, ScoreMath.load(60.0, rpe = 7, avgHr = null, restingHr = null), 1e-9)
        assertEquals(60.0 * (125.0 - 60) / 130, ScoreMath.load(60.0, rpe = null, avgHr = 125, restingHr = 60.0), 1e-9)
        assertEquals(36.0, ScoreMath.load(60.0, rpe = null, avgHr = null, restingHr = null), 1e-9)
    }

    private fun fuel(kcal: Double, protein: Double, meals: Int, water: Int, share: Double = 1.0) =
        ScoreMath.fuel(FuelInputs(day, kcal, 2100, protein, 130, meals, 3, water, 2500, share))

    @Test
    fun `a day on target is up`() {
        val s = fuel(kcal = 2050.0, protein = 135.0, meals = 3, water = 2600)
        assertEquals(100, s.value)
        assertEquals("On track", s.headline)
    }

    @Test
    fun `fuel needs a meal`() {
        assertEquals("Log a meal to see Fuel", fuel(0.0, 0.0, 0, 500, share = 0.5).missing)
        assertEquals("No meals logged", fuel(0.0, 0.0, 0, 500).missing)
    }

    @Test
    fun `midday fuel is judged against the day so far`() {
        val s = fuel(kcal = 1000.0, protein = 30.0, meals = 1, water = 1200, share = 0.5)
        assertTrue(s.soFar)
        assertEquals("So far · protein low", s.headline)
        assertTrue(s.reason, s.reason.contains("low protein"))
    }

    @Test
    fun `eating well over the target costs calories`() {
        val s = fuel(kcal = 3200.0, protein = 140.0, meals = 3, water = 2500)
        assertEquals(PaceSignal.DOWN, s.parts.first { it.key == "calories" }.signal)
        assertTrue(s.reason, s.reason.contains("too many calories"))
    }

    @Test
    fun `protein target follows the goal`() {
        assertEquals(130, ScoreMath.proteinTarget(80.0, TrainingGoal.BUILD_MUSCLE))
        assertEquals(95, ScoreMath.proteinTarget(80.0, TrainingGoal.LOSE_FAT))
        assertEquals(85, ScoreMath.proteinTarget(null, null))
    }

    @Test
    fun `eating share rises through the day`() {
        assertEquals(0.3, ScoreMath.dayShare(6 * 60), 1e-9)
        assertEquals(0.5, ScoreMath.dayShare(13 * 60 + 30), 1e-9)
        assertEquals(1.0, ScoreMath.dayShare(21 * 60), 1e-9)
    }

    @Test
    fun `period summaries count finished days`() {
        val days = listOf(fuel(2050.0, 135.0, 3, 2600), fuel(1000.0, 30.0, 1, 200), fuel(0.0, 0.0, 0, 0))
        assertEquals(2, ScoreMath.scoredDays(days))
        assertEquals(1, ScoreMath.goodDays(days))
        assertEquals((days[0].value!! + days[1].value!!) / 2.0, ScoreMath.average(days)!!.toDouble(), 0.5)
    }

    @Test
    fun `parts format their figures`() {
        assertEquals("7h 24m", PartUnit.MINUTES.format(444.0))
        assertEquals("48 min", PartUnit.MINUTES.format(48.0))
        assertEquals("7 h", PartUnit.MINUTES.format(420.0))
        assertEquals("2.1 L", PartUnit.ML.format(2100.0))
        assertEquals("58 bpm", PartUnit.BPM.format(58.2))
    }
}
