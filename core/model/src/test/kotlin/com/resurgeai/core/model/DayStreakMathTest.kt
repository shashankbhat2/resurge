package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DayStreakMathTest {
    // Sunday 18 Oct 2026; weeks start Sunday in the US, Monday in the UK.
    private val today = LocalDate.of(2026, 10, 18)
    private val us = Locale.US
    private val water = Habit("w", "Drink water", "droplet", "water", 0, false, today.minusDays(60))
    private val read = Habit("r", "Read", "book", "accent", 1, false, today.minusDays(60))
    private val targets = Targets(sleepMinutes = 420, steps = 10_000, waterMl = 2_500)

    private fun met(daysAgo: Long) = DayResult(today.minusDays(daysAgo), met = true, done = 1, total = 1)
    private fun missed(daysAgo: Long) = DayResult(today.minusDays(daysAgo), met = false, done = 0, total = 1)

    private fun inputs(
        daysAgo: Long,
        habits: List<Pair<Habit, DayState>> = emptyList(),
        sleep: Int? = null,
        steps: Long? = null,
        meals: Int = 0,
        waterMl: Int = 0,
        health: Boolean = true,
    ) = DayInputs(today.minusDays(daysAgo), habits, sleep, steps, meals, waterMl, health)

    @Test
    fun `a day counts only when every chosen goal is met`() {
        val goals = DayGoals(sleep = true, steps = true, meals = 2)
        val all = DayStreakMath.items(inputs(3, listOf(water to DayState.DONE), sleep = 450, steps = 12_000, meals = 2), goals, targets, isToday = false)
        assertEquals(DayResult(today.minusDays(3), true, 4, 4), DayStreakMath.settle(today.minusDays(3), all))
        val short = DayStreakMath.items(inputs(3, listOf(water to DayState.DONE), sleep = 450, steps = 9_000, meals = 2), goals, targets, isToday = false)
        assertFalse(DayStreakMath.settle(today.minusDays(3), short)!!.met)
    }

    @Test
    fun `a habit's own freeze doesn't make the whole day count`() {
        val goals = DayGoals(sleep = false, steps = false, meals = 0)
        val items = DayStreakMath.items(inputs(3, listOf(water to DayState.FROZEN)), goals, targets, isToday = false)
        assertFalse(DayStreakMath.settle(today.minusDays(3), items)!!.met)
    }

    @Test
    fun `left out goals and missing health readings don't count`() {
        val goals = DayGoals(excludedHabits = setOf("r"), sleep = true, steps = true, meals = 0)
        val items = DayStreakMath.items(inputs(3, listOf(water to DayState.DONE, read to DayState.MISSED)), goals, targets, isToday = false)
        // Read is left out; no sleep or steps reading that day: only water counts.
        assertEquals(DayResult(today.minusDays(3), true, 1, 1), DayStreakMath.settle(today.minusDays(3), items))
        // Without Health Connect, sleep and steps aren't goals at all.
        assertEquals(1, DayStreakMath.items(inputs(0, listOf(water to DayState.PENDING), health = false), goals, targets, isToday = true).size)
        assertNull(DayStreakMath.settle(today, emptyList()))
    }

    @Test
    fun `yesterday stays open only while a habit is unlogged and nothing failed`() {
        val goals = DayGoals(sleep = false, steps = false, meals = 2)
        val pending = DayStreakMath.items(inputs(1, listOf(water to DayState.PENDING), meals = 2), goals, targets, isToday = false)
        assertTrue(DayStreakMath.yesterdayOpen(pending))
        // Another habit was missed: yesterday is settled as missed, even with one still unlogged.
        val mixed = DayStreakMath.items(inputs(1, listOf(water to DayState.PENDING, read to DayState.MISSED), meals = 2), goals, targets, isToday = false)
        assertFalse(DayStreakMath.yesterdayOpen(mixed))
        val noMeals = DayStreakMath.items(inputs(1, listOf(water to DayState.PENDING), meals = 1), goals, targets, isToday = false)
        assertFalse(DayStreakMath.yesterdayOpen(noMeals))
    }

    @Test
    fun `a forgiven day keeps the streak but doesn't grow it`() {
        // Met 6 to 3 days ago, missed 2 days ago (freeze), met yesterday: 5, not 6.
        val results = listOf(met(6), met(5), met(4), met(3), missed(2), met(1))
        val s = DayStreakMath.streak(results, today, todayMet = false, locale = us)
        assertEquals(5, s.current)
        assertEquals(DayStatus.FORGIVEN, s.days[today.minusDays(2)])
        assertEquals(DayStatus.OPEN, s.days[today])
    }

    @Test
    fun `one freeze a week, then a miss breaks it`() {
        // Week of 11 Oct (US): misses on Tuesday and Thursday.
        val results = listOf(met(8), met(7), missed(5), met(4), missed(3), met(2), met(1))
        val s = DayStreakMath.streak(results, today, todayMet = true, locale = us)
        assertEquals(DayStatus.FORGIVEN, s.days[today.minusDays(5)])
        assertEquals(DayStatus.MISSED, s.days[today.minusDays(3)])
        assertEquals(3, s.current) // 2 and 1 days ago, and today
        assertEquals(3, s.best)
    }

    @Test
    fun `today never breaks the streak and an open yesterday neither counts nor breaks`() {
        val results = listOf(met(3), met(2), missed(1))
        val s = DayStreakMath.streak(results, today, todayMet = false, yesterdayOpen = true, locale = us)
        assertEquals(2, s.current)
        assertEquals(DayStatus.OPEN, s.days[today.minusDays(1)])
    }

    @Test
    fun `days with nothing chosen don't break the streak`() {
        val s = DayStreakMath.streak(listOf(met(4), met(2), met(1)), today, todayMet = false, locale = us)
        assertEquals(3, s.current)
        assertEquals(DayStatus.NONE, s.days[today.minusDays(3)])
    }

    @Test
    fun `the week counts met days against the chosen target, from the locale's first day`() {
        // Today is Sunday. US week starts today; UK week started Monday 12 Oct.
        val results = listOf(met(6), met(5), met(4), missed(3), met(2), met(1))
        val s = DayStreakMath.streak(results, today, todayMet = true, locale = us)
        assertEquals(1, DayStreakMath.week(s, today, target = 5, locale = us).metDays)
        val uk = DayStreakMath.streak(results, today, todayMet = true, locale = Locale.UK)
        val w = DayStreakMath.week(uk, today, target = 5, locale = Locale.UK)
        assertEquals(today.minusDays(6), w.start)
        assertEquals(6, w.metDays) // the forgiven Thursday doesn't count
        assertTrue(w.reached)
    }

    @Test
    fun `weeks won skip a first week that started part way`() {
        // US weeks: 4–10 Oct and 11–17 Oct. History starts Wednesday 7 Oct.
        val results = (1L..11L).map { met(it) }
        val s = DayStreakMath.streak(results, today, todayMet = false, locale = us)
        assertEquals(1, DayStreakMath.week(s, today, target = 5, locale = us).weeksWon)
        // With a missed Monday the full week has 6 met days: won at 5, not at 7.
        val gap = DayStreakMath.streak(results.filterNot { it.date == today.minusDays(6) } + missed(6), today, todayMet = false, locale = us)
        assertEquals(1, DayStreakMath.week(gap, today, target = 5, locale = us).weeksWon)
        assertEquals(0, DayStreakMath.week(gap, today, target = 7, locale = us).weeksWon)
    }
}
