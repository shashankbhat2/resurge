package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeightMathTest {
    private val zone = ZoneOffset.UTC
    private val day0 = LocalDate.of(2026, 9, 1)
    private fun w(day: Long, kg: Double, hour: Int = 7) =
        WeighIn("w$day-$hour", kg, day0.plusDays(day).atTime(hour, 0).toInstant(zone))

    @Test
    fun `with few weigh-ins the trend is their average, carried across days without one`() {
        val trend = WeightMath.trend(listOf(w(0, 80.0), w(2, 70.0), w(3, 75.0)), day0.plusDays(4), zone)
        assertEquals(5, trend.size)
        assertEquals(80.0, trend[0].kg, 1e-9)
        assertEquals(80.0, trend[1].kg, 1e-9) // no weigh-in: carried
        assertEquals(75.0, trend[2].kg, 1e-9)
        assertEquals(75.0, trend[4].kg, 1e-9)
    }

    @Test
    fun `once there's history, a reading after a long gap counts for more than the next day's`() {
        val steady = (0L..19L).map { w(it, 80.0) }
        val nextDay = WeightMath.trend(steady + w(20, 78.0), day0.plusDays(20), zone).last().kg
        val afterGap = WeightMath.trend(steady + w(30, 78.0), day0.plusDays(30), zone).last().kg
        assertEquals(79.8, nextDay, 1e-9)
        assertTrue(afterGap < 79.0)
    }

    @Test
    fun `several readings on one day are averaged first`() {
        val trend = WeightMath.trend(listOf(w(0, 80.0), w(1, 79.0, 7), w(1, 81.0, 21)), day0.plusDays(1), zone)
        assertEquals(80.0, trend.last().kg, 1e-9)
    }

    @Test
    fun `one high reading barely moves the trend`() {
        val steady = (0L..20L).map { w(it, 80.0) } + w(21, 83.0)
        val trend = WeightMath.trend(steady, day0.plusDays(21), zone)
        assertEquals(80.3, trend.last().kg, 1e-9)
    }

    @Test
    fun `change and weekly rate over a period use the trend at each end`() {
        val falling = (0L..28L).map { w(it, 80.0 - it * 0.1) }
        val trend = WeightMath.trend(falling, day0.plusDays(28), zone)
        val change = WeightMath.change(trend, day0, day0.plusDays(28))!!
        assertTrue(change < -1.8 && change > -2.8)
        assertEquals(change / 4, WeightMath.weeklyRate(trend, day0, day0.plusDays(28))!!, 1e-9)
        assertNull(WeightMath.weeklyRate(trend, day0, day0.plusDays(2)))
    }

    @Test
    fun `projection needs the trend heading toward the goal`() {
        val today = LocalDate.of(2026, 10, 17)
        assertEquals(today.plusDays(56), WeightMath.projectedDate(78.0, 74.0, -0.5, today))
        assertNull(WeightMath.projectedDate(78.0, 74.0, 0.3, today))
        assertNull(WeightMath.projectedDate(78.0, 74.0, -0.01, today))
        assertEquals(today.plusDays(56), WeightMath.dateAtPace(78.0, 74.0, 0.5, today))
    }

    @Test
    fun `goal progress and milestones every 2 kg`() {
        val goal = WeightGoal(startKg = 82.0, startDate = day0, goalKg = 74.0, paceKgPerWeek = 0.5)
        assertEquals(0.5, goal.progress(78.0), 1e-9)
        assertEquals(2, WeightMath.milestonesReached(goal, 77.9))
        assertEquals(4, WeightMath.milestonesReached(goal, 70.0))
        assertEquals(0.0, goal.progress(83.0), 1e-9)

        val gain = WeightGoal(startKg = 60.0, startDate = day0, goalKg = 64.0, paceKgPerWeek = 0.25)
        assertEquals(0.5, gain.progress(62.0), 1e-9)
    }

    @Test
    fun `a milestone crossed this week is news, an old one isn't`() {
        val goal = WeightGoal(startKg = 82.0, startDate = day0, goalKg = 74.0, paceKgPerWeek = 0.5)
        val trend = (0L..30L).map { TrendPoint(day0.plusDays(it), 82.0 - it * 0.1) }
        // Day 20 crosses 80.0 (2 kg down); on day 24 that's news, on day 30 it's not.
        assertEquals(2.0, WeightMath.newMilestone(goal, trend, day0.plusDays(24))!!, 1e-9)
        assertNull(WeightMath.newMilestone(goal, trend, day0.plusDays(30)))
    }

    @Test
    fun `the plan moves at the chosen pace and stops at the goal`() {
        val goal = WeightGoal(startKg = 80.0, startDate = day0, goalKg = 75.0, paceKgPerWeek = 0.5)
        assertEquals(80.0, goal.planKgOn(day0.minusDays(3)), 1e-9)
        assertEquals(79.0, goal.planKgOn(day0.plusDays(14)), 1e-9)
        assertEquals(75.0, goal.planKgOn(day0.plusDays(400)), 1e-9)
        assertEquals(day0.plusDays(70), goal.planEndDate)
        val gain = WeightGoal(startKg = 60.0, startDate = day0, goalKg = 64.0, paceKgPerWeek = 0.25)
        assertEquals(60.5, gain.planKgOn(day0.plusDays(14)), 1e-9)
    }

    @Test
    fun `next milestone is the next 2 kg step, and the last one is the goal`() {
        // 10.9 kg: steps at 2, 4, 6, 8, then 8 to 10.9 (the 0.9 kg leftover joins the last step).
        val goal = WeightGoal(startKg = 76.9, startDate = day0, goalKg = 66.0, paceKgPerWeek = 0.5)
        val first = WeightMath.nextMilestone(goal, 76.7)!!
        assertEquals(74.9, first.targetKg, 1e-9)
        assertEquals(76.9, first.fromKg, 1e-9)
        assertEquals(0.1, first.progress, 1e-9)
        assertEquals(1.8, first.remainingKg, 1e-9)
        val third = WeightMath.nextMilestone(goal, 72.0)!!
        assertEquals(70.9, third.targetKg, 1e-9)
        assertEquals(5, WeightMath.milestoneSteps(goal).size)
        val last = WeightMath.nextMilestone(goal, 67.0)!!
        assertEquals(66.0, last.targetKg, 1e-9)
        assertEquals(68.9, last.fromKg, 1e-9)
        assertEquals(1.0, last.remainingKg, 1e-9)
        // A leftover of a full step or more stays its own step.
        assertEquals(listOf(0.0 to 2.0, 2.0 to 3.0), WeightMath.milestoneSteps(WeightGoal(80.0, day0, 77.0, 0.5)))
        assertNull(WeightMath.nextMilestone(goal, 65.8))
    }

    @Test
    fun `each milestone is celebrated once, the highest when several pass at once`() {
        val goal = WeightGoal(startKg = 80.0, startDate = day0, goalKg = 72.0, paceKgPerWeek = 0.5) // steps at 2, 4, 6, then 8
        assertNull(WeightMath.celebration(goal, 78.5))
        val first = WeightMath.celebration(goal, 77.9) as GoalCelebration.Milestone
        assertEquals(1, first.number)
        assertEquals(3, first.count)
        assertEquals(2.0, first.kgDone, 1e-9)
        assertEquals(76.0, first.next!!.targetKg, 1e-9)
        assertNull(WeightMath.celebration(goal.copy(milestonesCelebrated = 1), 77.9))
        // A big drop (or an import) passing two milestones gets one celebration, for the higher one.
        assertEquals(3, (WeightMath.celebration(goal.copy(milestonesCelebrated = 1), 73.5) as GoalCelebration.Milestone).number)
    }

    @Test
    fun `reaching the goal is celebrated once and then sticks`() {
        val goal = WeightGoal(startKg = 80.0, startDate = day0, goalKg = 72.0, paceKgPerWeek = 0.5, milestonesCelebrated = 3)
        val c = WeightMath.celebration(goal, 71.9) as GoalCelebration.Goal
        assertEquals(8.0, c.totalKg, 1e-9)
        assertEquals(3, c.milestones)
        val done = goal.copy(reachedOn = day0.plusDays(100))
        assertNull(WeightMath.celebration(done, 71.9))
        assertTrue(done.reached(73.0)) // drifted back: still reached
        assertNull(WeightMath.celebration(done, 73.0))
    }

    @Test
    fun `maintaining has nothing to celebrate and is never reached`() {
        val hold = WeightGoal.maintain(72.0, day0)
        assertNull(WeightMath.celebration(hold, 72.0))
        assertEquals(false, hold.reached(72.0))
    }
}
