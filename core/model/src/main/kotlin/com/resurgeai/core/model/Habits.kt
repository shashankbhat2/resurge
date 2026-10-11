package com.resurgeai.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A daily habit. [icon] and [color] are keys resolved by the design system (HabitVisuals). */
data class Habit(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val order: Int,
    val archived: Boolean,
    /** Habit day the habit was created; earlier days don't count against it. */
    val createdDate: LocalDate,
)

/** A habit before it's saved (onboarding, add sheet). */
data class HabitDraft(val name: String, val icon: String, val color: String)

/** What the user logged for a habit on a habit day. Unlogged days have no entry. */
enum class HabitStatus(val wire: String) {
    DONE("done"),
    MISSED("missed");

    companion object {
        fun fromWire(value: Any?): HabitStatus? = entries.firstOrNull { it.wire == value }
    }
}

data class HabitSettings(
    /** A habit day runs from this local time until the same time the next day. */
    val resetTime: LocalTime = DEFAULT_RESET,
    val reminderEnabled: Boolean = true,
    val reminderTime: LocalTime = DEFAULT_REMINDER,
) {
    companion object {
        val DEFAULT_RESET: LocalTime = LocalTime.of(4, 0)
        val DEFAULT_REMINDER: LocalTime = LocalTime.of(21, 0)
        const val MAX_ACTIVE = 8
    }
}

/** How a single day reads on a habit's streak strip. */
enum class DayState { DONE, MISSED, FROZEN, PENDING, NONE }

data class HabitStats(
    val current: Int,
    val best: Int,
    /** Today's (current habit day) state: DONE, MISSED or PENDING. */
    val today: DayState,
    /** State per habit day from creation up to today. */
    val days: Map<LocalDate, DayState>,
    val freezesLeftThisWeek: Int,
    /** Done days ÷ elapsed days over the last 30 habit days (or since creation). */
    val completion30: Float,
)

/**
 * Streak rules (pure, unit-tested):
 * - A habit day runs from the reset time to the next reset; [habitDay] maps an instant to it.
 * - Today never breaks a streak. Yesterday is in its grace period: unlogged = pending, still editable.
 * - Older unlogged days, and any day explicitly logged as missed, count as missed.
 * - Each habit has one freeze per Sunday–Saturday week. A missed day that would break a running
 *   streak uses that week's freeze instead: the day shows as FROZEN and the streak carries on
 *   (without growing).
 */
object HabitStreaks {
    const val FREEZES_PER_WEEK = 1

    fun habitDay(at: Instant, zone: ZoneId, reset: LocalTime): LocalDate {
        val local = at.atZone(zone).toLocalDateTime()
        return if (local.toLocalTime() < reset) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    /** Next instant the habit day rolls over. */
    fun nextReset(at: Instant, zone: ZoneId, reset: LocalTime): Instant =
        habitDay(at, zone, reset).plusDays(1).atTime(reset).atZone(zone).toInstant()

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun compute(
        createdDate: LocalDate,
        today: LocalDate,
        log: Map<LocalDate, HabitStatus>,
    ): HabitStats {
        val start = minOf(createdDate, today)
        val days = LinkedHashMap<LocalDate, DayState>()
        val freezesUsed = HashMap<LocalDate, Int>() // week start -> count
        var run = 0
        var best = 0
        var date = start
        while (!date.isAfter(today)) {
            val logged = log[date]
            val graceDay = date == today || date == today.minusDays(1)
            val state = when {
                logged == HabitStatus.DONE -> DayState.DONE
                logged == null && graceDay -> DayState.PENDING
                // Today can still change, so a "not done" today never spends a freeze or breaks the streak.
                date == today -> DayState.MISSED
                else -> {
                    // Missed (explicitly, or unlogged past its grace period).
                    val week = weekStart(date)
                    val used = freezesUsed[week] ?: 0
                    if (run > 0 && used < FREEZES_PER_WEEK) {
                        freezesUsed[week] = used + 1
                        DayState.FROZEN
                    } else {
                        DayState.MISSED
                    }
                }
            }
            when (state) {
                DayState.DONE -> { run++; best = maxOf(best, run) }
                DayState.MISSED -> if (date != today) run = 0
                else -> Unit // FROZEN / PENDING keep the streak as is
            }
            days[date] = state
            date = date.plusDays(1)
        }
        // Today logged as missed doesn't break the streak until the day is over (it can still change).
        val current = run

        val windowStart = maxOf(start, today.minusDays(29))
        val elapsed = ChronoUnit.DAYS.between(windowStart, today).toInt() + 1
        val done = days.filterKeys { !it.isBefore(windowStart) }.count { it.value == DayState.DONE }

        return HabitStats(
            current = current,
            best = best,
            today = days[today] ?: DayState.PENDING,
            days = days,
            freezesLeftThisWeek = (FREEZES_PER_WEEK - (freezesUsed[weekStart(today)] ?: 0)).coerceAtLeast(0),
            completion30 = if (elapsed > 0) done.toFloat() / elapsed else 0f,
        )
    }
}

/** Starter habits suggested in onboarding, keyed by the goals the user picked. */
object HabitSuggestions {
    private val byGoal: Map<Goal, List<HabitDraft>> = mapOf(
        Goal.SLEEP_BETTER to listOf(
            HabitDraft("In bed by 23:00", "bed", "sleep"),
            HabitDraft("No screens 30 min before bed", "phone_off", "sleep"),
        ),
        Goal.MOVE_MORE to listOf(
            HabitDraft("Walk 10,000 steps", "walk", "activity"),
            HabitDraft("Stretch for 10 minutes", "yoga", "activity"),
        ),
        Goal.BUILD_MUSCLE to listOf(
            HabitDraft("Strength workout", "barbell", "heart"),
            HabitDraft("Hit my protein target", "apple", "energy"),
        ),
        Goal.LOSE_WEIGHT to listOf(
            HabitDraft("No late-night snacking", "moon", "energy"),
            HabitDraft("Workout or brisk walk", "run", "activity"),
        ),
        Goal.EAT_BETTER to listOf(
            HabitDraft("Eat a portion of greens", "salad", "activity"),
            HabitDraft("Cook at home", "apple", "energy"),
        ),
        Goal.HYDRATE to listOf(
            HabitDraft("Drink 2.5 L of water", "droplet", "water"),
        ),
    )

    private val general = listOf(
        HabitDraft("Read for 15 minutes", "book", "accent"),
        HabitDraft("Meditate for 5 minutes", "brain", "accent"),
        HabitDraft("Take my vitamins", "pill", "heart"),
    )

    fun forGoals(goals: Set<Goal>): List<HabitDraft> =
        (Goal.entries.filter { it in goals }.flatMap { byGoal[it].orEmpty() } + general).distinctBy { it.name }
}
