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
}
