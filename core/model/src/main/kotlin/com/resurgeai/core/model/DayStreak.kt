package com.resurgeai.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * What a day needs for the day streak, chosen by the user. Every chosen goal must be met for the
 * day to count. Sleep and steps only count while Health Connect can be read.
 */
data class DayGoals(
    /** Habits left out of the streak (all active habits count by default). */
    val excludedHabits: Set<String> = emptySet(),
    val sleep: Boolean = true,
    val steps: Boolean = true,
    /** Meals to log, 0 to leave meals out. */
    val meals: Int = 2,
    val water: Boolean = false,
    /** Days a week the weekly goal asks for. */
    val weeklyDays: Int = 5,
) {
    companion object {
        val MEALS_RANGE = 0..4
        val WEEKLY_RANGE = 1..7
    }
}

/** One goal on one day. [done] null: the day isn't settled for it yet (yesterday's unlogged habit). */
data class DayItem(val key: String, val label: String, val done: Boolean?)

/** Everything a day had to go on: the habits that existed that day and the day's numbers. */
data class DayInputs(
    val date: LocalDate,
    /** Habits that count that day: state per habit id, with its name. */
    val habits: List<Pair<Habit, DayState>>,
    /** Null when there's no reading for the day (not synced, or Health Connect unavailable). */
    val sleepMinutes: Int?,
    val steps: Long?,
    val meals: Int,
    val waterMl: Int,
    /** Health Connect is readable; without it sleep and steps don't count. */
    val health: Boolean,
)

/** How a day reads for the streak. */
enum class DayStatus {
    /** Every chosen goal met. */
    MET,

    /** Missed, but the week's freeze kept the streak going (it doesn't grow). */
    FORGIVEN,
    MISSED,

    /** Today, or yesterday while a habit can still be logged. Never breaks the streak. */
    OPEN,

    /** Before the streak began, or nothing chosen. */
    NONE,
}

/** A settled day, as saved: whether it counted, and how much of it was done. */
data class DayResult(val date: LocalDate, val met: Boolean, val done: Int, val total: Int)

data class StreakSummary(
    val current: Int,
    val best: Int,
    /** Status per day, oldest first, from the first known day to today. */
    val days: Map<LocalDate, DayStatus>,
    val freezeLeftThisWeek: Boolean,
)

data class WeekSummary(
    val start: LocalDate,
    /** Met days so far (forgiven days don't count toward the week). */
    val metDays: Int,
    val target: Int,
    /** Finished weeks that reached the target, not counting a first week that started part way. */
    val weeksWon: Int,
) {
    val reached: Boolean get() = metDays >= target
}

/**
 * Day streak rules (pure, unit-tested). One definition of a counted day feeds the streak, the week
 * and the calendars, so they can't disagree:
 * - A day counts when every chosen goal is met: each habit that existed that day (and isn't left
 *   out), sleep and steps (with Health Connect and a reading for the day), meals logged, water.
 * - Today is open until it's over: it adds to the streak once met, and never breaks it.
 * - Yesterday stays open while a habit is still unlogged and nothing else failed. Once anything
 *   failed, or the grace period is over, it's settled.
 * - One freeze a week: a missed day after a running streak is forgiven (the streak carries on
 *   without growing). Forgiven days don't count toward the weekly goal.
 * - Weeks start on the locale's first day of the week.
 */
object DayStreakMath {
    const val FREEZES_PER_WEEK = 1

    fun weekStart(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate {
        val first: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        return date.with(TemporalAdjusters.previousOrSame(first))
    }

    /** Today's (or yesterday's) goals and how each stands. */
    fun items(inputs: DayInputs, goals: DayGoals, targets: Targets, isToday: Boolean): List<DayItem> = buildList {
        inputs.habits.filter { (h, _) -> h.id !in goals.excludedHabits }.forEach { (h, state) ->
            add(
                DayItem(
                    "habit_${h.id}",
                    h.name,
                    when (state) {
                        DayState.DONE -> true
                        DayState.PENDING -> null
                        // Today's habits aren't failed until the day ends.
                        else -> if (isToday) null else false
                    },
                ),
            )
        }
        if (goals.sleep && inputs.health) {
            val min = inputs.sleepMinutes
            add(
                DayItem(
                    "sleep",
                    if (min != null && min >= targets.sleepMinutes) "Slept ${Targets.formatSleep(min)}" else "Sleep ${Targets.formatSleep(targets.sleepMinutes)}",
                    when {
                        min != null && min >= targets.sleepMinutes -> true
                        // No reading yet today might still come in; a past day without one doesn't count against you.
                        min == null -> null
                        else -> false
                    },
                ),
            )
        }
        if (goals.steps && inputs.health) {
            val n = inputs.steps
            add(
                DayItem(
                    "steps",
                    if (n != null && n >= targets.steps) "${"%,d".format(n)} steps" else "${"%,d".format(targets.steps)} steps",
                    when {
                        n != null && n >= targets.steps -> true
                        isToday || n == null -> null
                        else -> false
                    },
                ),
            )
        }
        if (goals.meals > 0) {
            val met = inputs.meals >= goals.meals
            add(DayItem("meals", if (met) "${inputs.meals} meals logged" else "Log ${goals.meals} ${if (goals.meals == 1) "meal" else "meals"}", if (met) true else if (isToday) null else false))
        }
        if (goals.water) {
            val met = inputs.waterMl >= targets.waterMl
            add(DayItem("water", if (met) "Water done" else "Drink ${"%,d".format(targets.waterMl)} ml", if (met) true else if (isToday) null else false))
        }
    }

    /**
     * Settles a day: met when every item is done. Items still unsettled (null) on a past day: a
     * missing sleep or steps reading is left out; an unlogged habit is a miss. Null when nothing
     * was chosen.
     */
    fun settle(date: LocalDate, items: List<DayItem>): DayResult? {
        val counted = items.filterNot { it.done == null && (it.key == "sleep" || it.key == "steps") }
        if (counted.isEmpty()) return null
        val done = counted.count { it.done == true }
        return DayResult(date, met = done == counted.size, done = done, total = counted.size)
    }

    /**
     * Yesterday is still open: some habit is unlogged and nothing has failed yet.
     */
    fun yesterdayOpen(items: List<DayItem>): Boolean =
        items.any { it.done == null && it.key.startsWith("habit_") } && items.none { it.done == false }

    /**
     * The streak from settled [results] (any order, gaps allowed) plus today and yesterday as they
     * stand. A gap between settled days (a day with nothing chosen) neither counts nor breaks.
     */
    fun streak(
        results: List<DayResult>,
        today: LocalDate,
        todayMet: Boolean,
        yesterday: LocalDate = today.minusDays(1),
        yesterdayOpen: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): StreakSummary {
        val byDate = results.filter { it.date.isBefore(today) && !(yesterdayOpen && it.date == yesterday) }.associateBy { it.date }
        val days = LinkedHashMap<LocalDate, DayStatus>()
        val freezes = HashMap<LocalDate, Int>()
        var run = 0
        var best = 0
        val first = (byDate.keys + today).min()
        var d = first
        while (d.isBefore(today)) {
            val r = byDate[d]
            val status = when {
                d == yesterday && yesterdayOpen -> DayStatus.OPEN
                r == null -> DayStatus.NONE
                r.met -> DayStatus.MET
                else -> {
                    val week = weekStart(d, locale)
                    val used = freezes[week] ?: 0
                    if (run > 0 && used < FREEZES_PER_WEEK) {
                        freezes[week] = used + 1
                        DayStatus.FORGIVEN
                    } else DayStatus.MISSED
                }
            }
            when (status) {
                DayStatus.MET -> { run++; best = maxOf(best, run) }
                DayStatus.MISSED -> run = 0
                else -> Unit
            }
            days[d] = status
            d = d.plusDays(1)
        }
        if (todayMet) {
            run++
            best = maxOf(best, run)
        }
        days[today] = if (todayMet) DayStatus.MET else DayStatus.OPEN
        return StreakSummary(run, best, days, freezeLeftThisWeek = (freezes[weekStart(today, locale)] ?: 0) < FREEZES_PER_WEEK)
    }

    /** This week against the weekly goal, and past weeks won. */
    fun week(streak: StreakSummary, today: LocalDate, target: Int, locale: Locale = Locale.getDefault()): WeekSummary {
        val start = weekStart(today, locale)
        val met = streak.days.filter { (d, s) -> !d.isBefore(start) && !d.isAfter(today) && s == DayStatus.MET }.size
        val known = streak.days.filterValues { it != DayStatus.NONE }.keys
        var won = 0
        val firstKnown = known.minOrNull()
        if (firstKnown != null) {
            // A first week that began part way through can't be judged fairly, so it's skipped.
            var w = weekStart(firstKnown, locale).let { if (it == firstKnown) it else it.plusWeeks(1) }
            while (w.isBefore(start)) {
                val days = (0L until 7L).map { w.plusDays(it) }
                if (days.count { streak.days[it] == DayStatus.MET } >= target) won++
                w = w.plusWeeks(1)
            }
        }
        return WeekSummary(start, met, target, won)
    }
}
