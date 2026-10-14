package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitStats
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.Targets
import java.time.Duration
import java.time.LocalDate

/** An active habit as Today shows it. */
data class TodayHabit(val habit: Habit, val stats: HabitStats) {
    val done: Boolean get() = stats.today == DayState.DONE
}

/** One equal share of today's tide. */
data class TideSegment(val key: String, val label: String, val done: Boolean)

/** Today's single score: every segment counts the same; all done is high tide. */
data class Tide(val segments: List<TideSegment>) {
    val done: Int get() = segments.count { it.done }
    val total: Int get() = segments.size
    val left: Int get() = total - done
    val percent: Int get() = if (total == 0) 0 else done * 100 / total
    val high: Boolean get() = total > 0 && left == 0
}

/** The slow collection: a chevron for every week with [TideMath.STRONG_WEEK_DAYS] full days. */
data class Ascent(
    /** Chevrons in the current season, 0–2 (the third completes the mark and starts a new season). */
    val chevrons: Int,
    val season: Int,
    /** Full days so far this week. */
    val fullDaysThisWeek: Int,
    /** Last week earned a chevron and this week has just started, so it's shown as new. */
    val fresh: Boolean,
    /** Every week won so far, across seasons. */
    val weeksWon: Int = 0,
) {
    val metThisWeek: Boolean get() = fullDaysThisWeek >= TideMath.STRONG_WEEK_DAYS
}

/** How one habit day reads on the streak calendar. */
enum class FullDay {
    /** Every habit done. */
    FULL,
    /** Full, with at least one miss covered by that habit's weekly freeze. */
    FORGIVEN,
    MISSED,
    /** Today, or yesterday still in its grace period. */
    OPEN,
    /** Before the first habit existed (or after today). */
    NONE,
}

/** One Sunday–Saturday week toward the weekly goal. */
data class WeekResult(val start: LocalDate, val fullDays: Int, val won: Boolean)

/**
 * Tide, rise count and Ascent rules (pure, unit-tested).
 *
 * Today's tide counts each active habit, the user's sleep and step goals ([Targets], when Health
 * Connect is readable) and logging at least [MEALS_GOAL] meals. Past days only keep habit check-ins, so the
 * rise count and the Ascent count "full days": days on which every habit that existed was done or
 * covered by an anchor (streak freeze). Today never breaks a run and yesterday is in its grace
 * period, as in [HabitStreaks].
 */
object TideMath {
    const val MEALS_GOAL = 2
    const val STRONG_WEEK_DAYS = 5
    const val CHEVRONS_PER_SEASON = 3

    fun tide(
        habits: List<TodayHabit>,
        health: Boolean,
        sleep: Duration?,
        steps: Long?,
        mealsToday: Int,
        targets: Targets = Targets(),
    ): Tide = Tide(
        buildList {
            habits.forEach { add(TideSegment("habit_${it.habit.id}", it.habit.name, it.done)) }
            if (health) {
                val sleepGoal = Duration.ofMinutes(targets.sleepMinutes.toLong())
                val slept = sleep != null && sleep >= sleepGoal
                add(TideSegment("sleep", if (slept) "Slept ${Formatters.duration(sleep!!)}" else "Sleep ${Targets.formatSleep(targets.sleepMinutes)}", slept))
                val stepGoal = targets.steps.toLong()
                val walked = steps != null && steps >= stepGoal
                add(TideSegment("steps", if (walked) "${Formatters.count(steps!!)} steps" else "${Formatters.count(stepGoal)} steps", walked))
            }
            val fed = mealsToday >= MEALS_GOAL
            add(TideSegment("meals", if (fed) "$mealsToday meals logged" else "Log $MEALS_GOAL meals", fed))
        },
    )

    /** Every habit that existed on [date] was done or anchored; false when no habit existed yet. */
    fun fullDay(habits: List<TodayHabit>, date: LocalDate): Boolean {
        val existing = habits.filter { !it.habit.createdDate.isAfter(date) }
        return existing.isNotEmpty() && existing.all { it.stats.days[date] == DayState.DONE || it.stats.days[date] == DayState.FROZEN }
    }

    fun dayStatus(habits: List<TodayHabit>, date: LocalDate, today: LocalDate): FullDay {
        if (date.isAfter(today)) return FullDay.NONE
        val existing = habits.filter { !it.habit.createdDate.isAfter(date) }
        if (existing.isEmpty()) return FullDay.NONE
        val states = existing.map { it.stats.days[date] }
        return when {
            states.all { it == DayState.DONE } -> FullDay.FULL
            states.all { it == DayState.DONE || it == DayState.FROZEN } -> FullDay.FORGIVEN
            date == today || (date == today.minusDays(1) && states.any { it == DayState.PENDING }) -> FullDay.OPEN
            else -> FullDay.MISSED
        }
    }

    /** Longest run of full days ever (today counts only once it's full). */
    fun bestStreak(habits: List<TodayHabit>, today: LocalDate): Int {
        if (habits.isEmpty()) return 0
        var date = habits.minOf { it.habit.createdDate }
        var run = 0
        var best = 0
        while (!date.isAfter(today)) {
            when (dayStatus(habits, date, today)) {
                FullDay.FULL, FullDay.FORGIVEN -> { run++; best = maxOf(best, run) }
                FullDay.MISSED -> run = 0
                FullDay.OPEN, FullDay.NONE -> Unit
            }
            date = date.plusDays(1)
        }
        return maxOf(best, riseCount(habits, today))
    }

    /** The last [count] weeks, oldest first, ending with this (unfinished) week. */
    fun recentWeeks(habits: List<TodayHabit>, today: LocalDate, count: Int = 6): List<WeekResult> {
        val thisWeek = HabitStreaks.weekStart(today)
        return (count - 1 downTo 0).map { back ->
            val start = thisWeek.minusWeeks(back.toLong())
            val full = (0L until 7L).map { start.plusDays(it) }.count { !it.isAfter(today) && fullDay(habits, it) }
            WeekResult(start, full, won = full >= STRONG_WEEK_DAYS)
        }
    }

    fun riseCount(habits: List<TodayHabit>, today: LocalDate): Int {
        if (habits.isEmpty()) return 0
        val first = habits.minOf { it.habit.createdDate }
        var run = if (fullDay(habits, today)) 1 else 0
        var date = today.minusDays(1)
        while (!date.isBefore(first)) {
            when {
                fullDay(habits, date) -> run++
                // Yesterday can still be logged: it doesn't count yet, but doesn't break the run.
                date == today.minusDays(1) && habits.any { it.stats.days[date] == DayState.PENDING } -> Unit
                else -> break
            }
            date = date.minusDays(1)
        }
        return run
    }

    fun ascent(habits: List<TodayHabit>, today: LocalDate): Ascent {
        val thisWeek = HabitStreaks.weekStart(today)
        val fullThisWeek = generateSequence(thisWeek) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.count { fullDay(habits, it) }
        if (habits.isEmpty()) return Ascent(0, 1, fullThisWeek, fresh = false)
        var week = HabitStreaks.weekStart(habits.minOf { it.habit.createdDate })
        var earned = 0
        var lastWeekEarned = false
        while (week.isBefore(thisWeek)) {
            val strong = (0L until 7L).count { fullDay(habits, week.plusDays(it)) } >= STRONG_WEEK_DAYS
            if (strong) earned++
            lastWeekEarned = strong
            week = week.plusWeeks(1)
        }
        return Ascent(
            chevrons = earned % CHEVRONS_PER_SEASON,
            season = earned / CHEVRONS_PER_SEASON + 1,
            fullDaysThisWeek = fullThisWeek,
            fresh = lastWeekEarned && today == thisWeek && earned % CHEVRONS_PER_SEASON != 0,
            weeksWon = earned,
        )
    }
}
