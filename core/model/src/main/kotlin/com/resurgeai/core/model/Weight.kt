package com.resurgeai.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

enum class WeighInSource(val wire: String) {
    MANUAL("manual"),
    HEALTH_CONNECT("health_connect");

    companion object {
        fun fromWire(value: String?): WeighInSource = entries.firstOrNull { it.wire == value } ?: MANUAL
    }
}

/** One reading from the scale, always stored in kg. */
data class WeighIn(
    val id: String,
    val kg: Double,
    val at: Instant,
    val note: String? = null,
    val source: WeighInSource = WeighInSource.MANUAL,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = at.atZone(zone).toLocalDate()
}

/** Working toward a new weight, or holding one (usually after reaching a goal). */
enum class GoalMode(val wire: String) {
    CHANGE("change"),
    MAINTAIN("maintain");

    companion object {
        fun fromWire(value: String?): GoalMode = entries.firstOrNull { it.wire == value } ?: CHANGE
    }
}

/**
 * The weight goal: where it started, where it ends, and the chosen weekly pace. It also remembers
 * what's been celebrated, so each milestone gets one celebration ([milestonesCelebrated], counted in
 * [WeightMath.milestoneSteps]) and reaching the goal sticks ([reachedOn]) even if the trend drifts back.
 * In [GoalMode.MAINTAIN], [goalKg] is the weight being held, within [MAINTAIN_BAND_KG] either side.
 */
data class WeightGoal(
    val startKg: Double,
    val startDate: LocalDate,
    val goalKg: Double,
    val paceKgPerWeek: Double,
    val mode: GoalMode = GoalMode.CHANGE,
    val milestonesCelebrated: Int = 0,
    val reachedOn: LocalDate? = null,
) {
    val maintaining: Boolean get() = mode == GoalMode.MAINTAIN

    /** Reached: on the day the trend got there, and from then on. Never while maintaining. */
    fun reached(currentKg: Double): Boolean = !maintaining && (reachedOn != null || progress(currentKg) >= 1.0)

    val losing: Boolean get() = goalKg < startKg
    val totalKg: Double get() = abs(startKg - goalKg)

    /** Progress toward the goal from [currentKg], 0–1. */
    fun progress(currentKg: Double): Double =
        if (totalKg == 0.0) 1.0 else ((if (losing) startKg - currentKg else currentKg - startKg) / totalKg).coerceIn(0.0, 1.0)

    /** Kilos moved toward the goal so far (never negative). */
    fun doneKg(currentKg: Double): Double = (if (losing) startKg - currentKg else currentKg - startKg).coerceAtLeast(0.0)

    /** When the plan reaches the goal: the start date plus the distance at the chosen pace. */
    val planEndDate: LocalDate get() = startDate.plusDays(ceil(totalKg / paceKgPerWeek * 7).toLong())

    /** Where the plan expects the trend on [date]: from the start at the chosen pace, stopping at the goal. */
    fun planKgOn(date: LocalDate): Double {
        val weeks = ChronoUnit.DAYS.between(startDate, date).coerceAtLeast(0) / 7.0
        val moved = (paceKgPerWeek * weeks).coerceAtMost(totalKg)
        return if (losing) startKg - moved else startKg + moved
    }

    companion object {
        /** Pace choices for the goal sheet, kg a week. */
        val PACES = listOf(0.25 to "Gentle", 0.5 to "Steady", 0.75 to "Faster")
        const val MILESTONE_KG = 2.0
        /** Maintaining counts as on target within this much either side. */
        const val MAINTAIN_BAND_KG = 1.0

        /** Hold [kg] from [today]. */
        fun maintain(kg: Double, today: LocalDate) = WeightGoal(kg, today, kg, 0.5, GoalMode.MAINTAIN)
    }
}

/**
 * The next milestone on the way to the goal: the weight it's at, the weight the current stretch
 * started from, how far through the stretch the trend is (0–1) and how much is left.
 */
data class NextMilestone(val targetKg: Double, val fromKg: Double, val progress: Double, val remainingKg: Double)

/** Something to celebrate on the way to the goal. */
sealed interface GoalCelebration {
    /** Milestone [number] of [count] (the goal itself isn't counted), [kgDone] toward the goal. */
    data class Milestone(val number: Int, val count: Int, val kgDone: Double, val next: NextMilestone?) : GoalCelebration

    /** The goal, [totalKg] moved since [since], passing [milestones] milestones on the way. */
    data class Goal(val totalKg: Double, val since: LocalDate, val milestones: Int) : GoalCelebration
}

/** A day's trend value. */
data class TrendPoint(val date: LocalDate, val kg: Double)

/**
 * Weight trend maths. The trend is an exponential moving average of weigh-ins (several readings on
 * one day averaged first), so a single high or low reading barely moves it. Two corrections keep it
 * from lagging: with few weigh-ins it's their plain average (each new day counts at least 1/n), and
 * a reading after a gap counts as much as the missed days would have (1 − 0.9^days).
 */
object WeightMath {
    const val SMOOTHING = 0.1

    /** Daily trend from the first weigh-in to [through] (inclusive); empty without weigh-ins. */
    fun trend(weighIns: List<WeighIn>, through: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TrendPoint> {
        if (weighIns.isEmpty()) return emptyList()
        val byDay = weighIns.groupBy { it.date(zone) }.mapValues { (_, w) -> w.map { it.kg }.average() }
        val first = byDay.keys.min()
        val end = maxOf(through, byDay.keys.max())
        var t = byDay.getValue(first)
        var n = 1
        var lastDay = first
        val out = ArrayList<TrendPoint>()
        var d = first
        while (!d.isAfter(end)) {
            if (d != first) byDay[d]?.let { kg ->
                n++
                val gap = ChronoUnit.DAYS.between(lastDay, d).toInt()
                val alpha = maxOf(1.0 / n, 1 - (1 - SMOOTHING).pow(gap))
                t += alpha * (kg - t)
                lastDay = d
            }
            out += TrendPoint(d, t)
            d = d.plusDays(1)
        }
        return out
    }

    /** Trend on [date]: the last trend point on or before it, else the first one. */
    fun trendOn(trend: List<TrendPoint>, date: LocalDate): Double? =
        trend.lastOrNull { !it.date.isAfter(date) }?.kg ?: trend.firstOrNull()?.kg

    /** Change in trend between two days (negative = lost). */
    fun change(trend: List<TrendPoint>, from: LocalDate, to: LocalDate): Double? {
        val a = trendOn(trend, from) ?: return null
        val b = trendOn(trend, to) ?: return null
        return b - a
    }

    /** Change per week between two days, or null for a span under 3 days. */
    fun weeklyRate(trend: List<TrendPoint>, from: LocalDate, to: LocalDate): Double? {
        val days = ChronoUnit.DAYS.between(from, to)
        if (days < 3) return null
        return change(trend, from, to)?.let { it / days * 7 }
    }

    /**
     * When the trend reaches [goalKg] at [ratePerWeek] (signed, kg a week), or null when it's heading
     * the other way, too slowly to say (under 0.05 kg a week), or more than a year out.
     */
    fun projectedDate(currentKg: Double, goalKg: Double, ratePerWeek: Double?, today: LocalDate): LocalDate? {
        if (ratePerWeek == null || abs(ratePerWeek) < 0.05) return null
        val remaining = goalKg - currentKg
        if (remaining == 0.0) return today
        if (remaining * ratePerWeek < 0) return null
        val days = ceil(remaining / ratePerWeek * 7).toLong()
        return if (days > 365) null else today.plusDays(days)
    }

    /** Target date for a goal at a fixed [paceKgPerWeek] (unsigned). */
    fun dateAtPace(currentKg: Double, goalKg: Double, paceKgPerWeek: Double, today: LocalDate): LocalDate =
        today.plusDays(ceil(abs(goalKg - currentKg) / paceKgPerWeek * 7).toLong())

    /**
     * What to celebrate now, if anything: the goal the first time the trend reaches it, else the
     * highest milestone not celebrated yet (several crossed at once get one celebration). Nothing
     * while maintaining.
     */
    fun celebration(goal: WeightGoal, currentKg: Double): GoalCelebration? {
        if (goal.maintaining) return null
        val steps = milestoneSteps(goal)
        val milestones = steps.size - 1
        if (goal.reachedOn == null && goal.progress(currentKg) >= 1.0) return GoalCelebration.Goal(goal.totalKg, goal.startDate, milestones)
        if (goal.reachedOn != null) return null
        val reached = milestonesReached(goal, currentKg).coerceAtMost(milestones)
        if (reached <= goal.milestonesCelebrated) return null
        return GoalCelebration.Milestone(reached, milestones, steps[reached - 1].second, nextMilestone(goal, currentKg))
    }

    /** Milestone steps ([milestoneSteps]) finished toward the goal at [currentKg]; the last is the goal. */
    fun milestonesReached(goal: WeightGoal, currentKg: Double): Int {
        val done = goal.doneKg(currentKg)
        return milestoneSteps(goal).count { done >= it.second - 1e-9 }
    }

    /**
     * The goal cut into milestone steps, as kg-done ranges: every [WeightGoal.MILESTONE_KG], with a
     * leftover under half a step folded into the last one (so there's no 0.1 kg sliver at the end).
     */
    fun milestoneSteps(goal: WeightGoal): List<Pair<Double, Double>> {
        val step = WeightGoal.MILESTONE_KG
        val bounds = generateSequence(step) { it + step }.takeWhile { it < goal.totalKg - 1e-9 }.toMutableList()
        if (bounds.isNotEmpty() && goal.totalKg - bounds.last() < step / 2) bounds.removeAt(bounds.lastIndex)
        val ends = bounds + goal.totalKg
        return ends.mapIndexed { i, to -> (if (i == 0) 0.0 else ends[i - 1]) to to }
    }

    /** The next milestone from [currentKg] (the last one is the goal itself), or null once the goal is reached. */
    fun nextMilestone(goal: WeightGoal, currentKg: Double): NextMilestone? {
        val done = goal.doneKg(currentKg)
        val (from, to) = milestoneSteps(goal).firstOrNull { done < it.second - 1e-9 } ?: return null
        fun at(kg: Double) = if (goal.losing) goal.startKg - kg else goal.startKg + kg
        return NextMilestone(at(to), at(from), ((done - from) / (to - from)).coerceIn(0.0, 1.0), to - done)
    }

    /**
     * The milestone most recently crossed, in kg toward the goal, if the trend crossed it within the last [withinDays]
     * days (so it's news); else null.
     */
    fun newMilestone(goal: WeightGoal, trend: List<TrendPoint>, today: LocalDate, withinDays: Long = 7): Double? {
        val now = trendOn(trend, today) ?: return null
        val before = trendOn(trend, today.minusDays(withinDays)) ?: return null
        val reached = milestonesReached(goal, now)
        return if (reached > 0 && reached > milestonesReached(goal, before)) milestoneSteps(goal)[reached - 1].second else null
    }

    const val KG_PER_LB = 0.45359237
}
