package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitStats
import com.resurgeai.core.model.HabitStreaks
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
)

/**
 * Tide, rise count and Ascent rules (pure, unit-tested).
 *
 * Today's tide counts each active habit, the sleep and step goals (when Health Connect is
 * readable) and logging at least [MEALS_GOAL] meals. Past days only keep habit check-ins, so the
 * rise count and the Ascent count "full days": days on which every habit that existed was done or
 * covered by an anchor (streak freeze). Today never breaks a run and yesterday is in its grace
 * period, as in [HabitStreaks].
 */
object TideMath {
    val SLEEP_GOAL: Duration = Duration.ofHours(7)
    const val MEALS_GOAL = 2
    const val STRONG_WEEK_DAYS = 5
    const val CHEVRONS_PER_SEASON = 3

    fun tide(
        habits: List<TodayHabit>,
        health: Boolean,
        sleep: Duration?,
        steps: Long?,
        mealsToday: Int,
        stepGoal: Long = DashboardGoals.STEPS.toLong(),
    ): Tide = Tide(
        buildList {
            habits.forEach { add(TideSegment("habit_${it.habit.id}", it.habit.name, it.done)) }
            if (health) {
                val slept = sleep != null && sleep >= SLEEP_GOAL
                add(TideSegment("sleep", if (slept) "Slept ${Formatters.duration(sleep!!)}" else "Sleep ${SLEEP_GOAL.toHours()}h", slept))
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
        )
    }
}
