package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitStatus
import com.resurgeai.core.model.HabitStatus.DONE
import com.resurgeai.core.model.HabitStatus.MISSED
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class TideMathTest {
    // Wed 14 Oct 2026. Week (Sun–Sat) = 11–17 Oct; previous week 4–10 Oct.
    private val today = LocalDate.of(2026, 10, 14)
    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    private fun habit(id: String, created: LocalDate, log: Map<LocalDate, HabitStatus>, on: LocalDate = today) =
        TodayHabit(
            Habit(id, "Habit $id", "book", "accent", 0, archived = false, createdDate = created),
            HabitStreaks.compute(created, on, log),
        )

    private fun done(vararg days: LocalDate) = days.associateWith { DONE }

    @Test
    fun `tide counts each habit, sleep, steps and meals equally`() {
        val habits = listOf(habit("a", d(1), done(today)), habit("b", d(1), emptyMap()))
        val tide = TideMath.tide(habits, health = true, sleep = Duration.ofHours(7).plusMinutes(42), steps = 4_000, mealsToday = 2)
        assertEquals(5, tide.total)
        assertEquals(3, tide.done) // habit a, sleep, meals
        assertEquals(60, tide.percent)
        assertEquals(2, tide.left)
        assertFalse(tide.high)
    }

    @Test
    fun `sleep and step goals come from the user's targets`() {
        val targets = Targets(sleepMinutes = 6 * 60, steps = 6_000)
        val tide = TideMath.tide(emptyList(), health = true, sleep = Duration.ofMinutes(6 * 60 + 20), steps = 6_500, mealsToday = 0, targets = targets)
        assertTrue(tide.segments.first { it.key == "sleep" }.done)
        assertTrue(tide.segments.first { it.key == "steps" }.done)
        val missed = TideMath.tide(emptyList(), health = true, sleep = Duration.ofMinutes(5 * 60), steps = 5_000, mealsToday = 0, targets = targets)
        assertEquals(listOf("Sleep 6h", "6,000 steps"), missed.segments.filter { it.key != "meals" }.map { it.label })
    }

    @Test
    fun `without Health Connect the tide is habits and meals only`() {
        val habits = listOf(habit("a", d(1), done(today)))
        val tide = TideMath.tide(habits, health = false, sleep = null, steps = null, mealsToday = 2)
        assertEquals(listOf("habit_a", "meals"), tide.segments.map { it.key })
        assertTrue(tide.high)
    }

    @Test
    fun `rise counts full days and today never breaks it`() {
        val log = done(d(10), d(11), d(12), d(13))
        val habits = listOf(habit("a", d(10), log), habit("b", d(10), log))
        assertEquals(4, TideMath.riseCount(habits, today))
        val doneToday = listOf(habit("a", d(10), log + done(today)), habit("b", d(10), log + done(today)))
        assertEquals(5, TideMath.riseCount(doneToday, today))
    }

    @Test
    fun `yesterday unlogged is in grace and keeps the run`() {
        val habits = listOf(habit("a", d(10), done(d(10), d(11), d(12))))
        assertEquals(3, TideMath.riseCount(habits, today))
    }

    @Test
    fun `a day missed by one habit ends the run`() {
        val habits = listOf(
            habit("a", d(10), done(d(10), d(11), d(12), d(13))),
            habit("b", d(10), done(d(10), d(11), d(13)) + (d(12) to MISSED)),
        )
        // b's miss on the 12th uses its weekly anchor, so the 12th is still a full day.
        assertEquals(4, TideMath.riseCount(habits, today))
        val twoMisses = listOf(
            habit("a", d(10), done(d(10), d(11), d(12), d(13))),
            habit("b", d(10), done(d(10), d(13)) + (d(11) to MISSED) + (d(12) to MISSED)),
        )
        // The second miss in the same week has no anchor left.
        assertEquals(1, TideMath.riseCount(twoMisses, today))
    }

    @Test
    fun `a habit added later only counts from its first day`() {
        val habits = listOf(
            habit("a", d(8), done(d(8), d(9), d(10), d(11), d(12), d(13))),
            habit("b", d(12), done(d(12), d(13))),
        )
        assertEquals(6, TideMath.riseCount(habits, today))
    }

    @Test
    fun `no habits means no rise`() {
        assertEquals(0, TideMath.riseCount(emptyList(), today))
    }

    @Test
    fun `a week with five full days earns a chevron`() {
        val habits = listOf(habit("a", d(4), done(d(4), d(5), d(6), d(7), d(8), d(11), d(12))))
        val ascent = TideMath.ascent(habits, today)
        assertEquals(1, ascent.chevrons)
        assertEquals(1, ascent.season)
        assertEquals(2, ascent.fullDaysThisWeek)
        assertFalse(ascent.fresh)
    }

    @Test
    fun `four full days earn nothing`() {
        // Done 4–6, the 7th is covered by the week's anchor, then 8–10 are missed.
        val habits = listOf(habit("a", d(4), done(d(4), d(5), d(6))))
        assertEquals(0, TideMath.ascent(habits, today).chevrons)
    }

    @Test
    fun `a new chevron is fresh on the first day of the next week`() {
        val sunday = d(11)
        val habits = listOf(habit("a", d(4), done(d(4), d(5), d(6), d(7), d(8)), on = sunday))
        assertTrue(TideMath.ascent(habits, sunday).fresh)
    }

    @Test
    fun `three chevrons complete the mark and start a new season`() {
        val start = LocalDate.of(2026, 9, 20) // a Sunday, three weeks before the 11th
        val log = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(d(11)) }.associateWith { DONE }
        val ascent = TideMath.ascent(listOf(habit("a", start, log)), today)
        assertEquals(0, ascent.chevrons)
        assertEquals(2, ascent.season)
    }

    @Test
    fun `calendar days read as full, forgiven, missed, open or before habits`() {
        // Done 9–10, missed 11 (freeze covers it), done 12, missed 13 explicitly (no freeze left).
        val habits = listOf(habit("a", d(9), done(d(9), d(10), d(12)) + (d(11) to MISSED) + (d(13) to MISSED)))
        assertEquals(FullDay.NONE, TideMath.dayStatus(habits, d(8), today))
        assertEquals(FullDay.FULL, TideMath.dayStatus(habits, d(10), today))
        assertEquals(FullDay.FORGIVEN, TideMath.dayStatus(habits, d(11), today))
        assertEquals(FullDay.MISSED, TideMath.dayStatus(habits, d(13), today))
        assertEquals(FullDay.OPEN, TideMath.dayStatus(habits, today, today))
    }

    @Test
    fun `best streak is the longest run, even when it has ended`() {
        // Done 1–6; the 7th is covered by the week's freeze (a run of 7); the 8th ends it; then done 12–13.
        val habits = listOf(habit("a", d(1), done(d(1), d(2), d(3), d(4), d(5), d(6), d(12), d(13)) + (d(7) to MISSED) + (d(8) to MISSED)))
        assertEquals(2, TideMath.riseCount(habits, today))
        assertEquals(7, TideMath.bestStreak(habits, today))
    }

    @Test
    fun `recent weeks count full days and mark weeks won`() {
        val habits = listOf(habit("a", d(4), done(d(4), d(5), d(6), d(7), d(8), d(11), d(12))))
        val weeks = TideMath.recentWeeks(habits, today, count = 2)
        assertEquals(listOf(d(4), d(11)), weeks.map { it.start })
        // Last week: 4–8 done and the 9th covered by its freeze.
        assertEquals(listOf(6, 2), weeks.map { it.fullDays })
        assertEquals(listOf(true, false), weeks.map { it.won })
    }
}
