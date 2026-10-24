package com.resurgeai.core.model

import com.resurgeai.core.model.HabitStatus.DONE
import com.resurgeai.core.model.HabitStatus.MISSED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HabitStreaksTest {
    // Wed 14 Oct 2026. Week (Sun–Sat) = 11–17 Oct; previous week 4–10 Oct.
    private val today = LocalDate.of(2026, 10, 14)
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private fun stats(created: Int, vararg log: Pair<Int, HabitStatus>) =
        HabitStreaks.compute(d(created), today, log.associate { (day, s) -> d(day) to s })

    @Test
    fun `habit day starts at the reset time`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val reset = LocalTime.of(4, 0)
        fun at(s: String) = Instant.parse(s)
        // 01:30 local on the 15th still belongs to the 14th.
        assertEquals(d(14), HabitStreaks.habitDay(at("2026-10-14T20:00:00Z"), zone, reset))
        // 04:30 local on the 15th is the 15th.
        assertEquals(d(15), HabitStreaks.habitDay(at("2026-10-14T23:00:00Z"), zone, reset))
        assertEquals(at("2026-10-14T22:30:00Z"), HabitStreaks.nextReset(at("2026-10-14T20:00:00Z"), zone, reset))
    }

    @Test
    fun `consecutive done days build a streak and today pending does not break it`() {
        val s = stats(10, 10 to DONE, 11 to DONE, 12 to DONE, 13 to DONE)
        assertEquals(4, s.current)
        assertEquals(4, s.best)
        assertEquals(DayState.PENDING, s.today)
    }

    @Test
    fun `yesterday unlogged is in grace, older unlogged is missed`() {
        val s = stats(10, 10 to DONE, 11 to DONE, 12 to DONE) // 13th unlogged = grace
        assertEquals(3, s.current)
        assertEquals(DayState.PENDING, s.days[d(13)])

        // 11th unlogged, older than grace: uses the week's freeze.
        val f = stats(9, 9 to DONE, 10 to DONE, 12 to DONE, 13 to DONE)
        assertEquals(DayState.FROZEN, f.days[d(11)])
        assertEquals(4, f.current)
        assertEquals(0, f.freezesLeftThisWeek)
    }

    @Test
    fun `second miss in the same week breaks the streak`() {
        val s = stats(9, 9 to DONE, 10 to DONE, 11 to MISSED, 12 to MISSED, 13 to DONE)
        assertEquals(DayState.FROZEN, s.days[d(11)])
        assertEquals(DayState.MISSED, s.days[d(12)])
        assertEquals(1, s.current)
        assertEquals(2, s.best)
    }

    @Test
    fun `freezes reset each Sunday and are only spent on running streaks`() {
        // Missed Wed 7th (week of 4th) frozen; missed Mon 12th (week of 11th) frozen too.
        val s = stats(5, 5 to DONE, 6 to DONE, 8 to DONE, 9 to DONE, 10 to DONE, 11 to DONE, 13 to DONE)
        assertEquals(DayState.FROZEN, s.days[d(7)])
        assertEquals(DayState.FROZEN, s.days[d(12)])
        assertEquals(7, s.current) // 7 done days; frozen days hold the streak without adding

        // No running streak: a miss stays a miss and keeps the freeze.
        val n = stats(12, 12 to MISSED, 13 to DONE)
        assertEquals(DayState.MISSED, n.days[d(12)])
        assertEquals(1, n.freezesLeftThisWeek)
    }

    @Test
    fun `not done today does not break the streak or spend a freeze`() {
        val s = stats(12, 12 to DONE, 13 to DONE, 14 to MISSED)
        assertEquals(2, s.current)
        assertEquals(DayState.MISSED, s.today)
        assertEquals(1, s.freezesLeftThisWeek)
    }

    @Test
    fun `completion over the last thirty days counts from creation`() {
        val s = stats(12, 12 to DONE, 13 to MISSED, 14 to DONE)
        assertEquals(2f / 3f, s.completion30, 0.001f)
    }

    @Test
    fun `backfill covers today and the six days before it`() {
        val today = java.time.LocalDate.of(2026, 10, 24)
        assertEquals(java.time.LocalDate.of(2026, 10, 18), Backfill.earliest(today))
        assertEquals(7, Backfill.days(today).size)
        assertEquals(today, Backfill.days(today).last())
        org.junit.Assert.assertTrue(Backfill.allows(today.minusDays(6), today))
        org.junit.Assert.assertFalse(Backfill.allows(today.minusDays(7), today))
        org.junit.Assert.assertFalse(Backfill.allows(today.plusDays(1), today))
    }
}
