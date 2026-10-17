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

/** The weight goal: where it started, where it ends, and the chosen weekly pace. */
data class WeightGoal(
    val startKg: Double,
    val startDate: LocalDate,
    val goalKg: Double,
    val paceKgPerWeek: Double,
) {
    val losing: Boolean get() = goalKg < startKg
    val totalKg: Double get() = abs(startKg - goalKg)

    /** Progress toward the goal from [currentKg], 0–1. */
    fun progress(currentKg: Double): Double =
        if (totalKg == 0.0) 1.0 else ((if (losing) startKg - currentKg else currentKg - startKg) / totalKg).coerceIn(0.0, 1.0)

    /** Kilos moved toward the goal so far (never negative). */
    fun doneKg(currentKg: Double): Double = (if (losing) startKg - currentKg else currentKg - startKg).coerceAtLeast(0.0)

    companion object {
        /** Pace choices for the goal sheet, kg a week. */
        val PACES = listOf(0.25 to "Gentle", 0.5 to "Steady", 0.75 to "Faster")
        const val MILESTONE_KG = 2.0
    }
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

    /** Whole milestones (every [WeightGoal.MILESTONE_KG]) reached toward the goal at [currentKg]. */
    fun milestonesReached(goal: WeightGoal, currentKg: Double): Int =
        floor(goal.doneKg(currentKg) / WeightGoal.MILESTONE_KG).toInt()
            .coerceAtMost(floor(goal.totalKg / WeightGoal.MILESTONE_KG).toInt())

    /**
     * The milestone most recently crossed, in kg, if the trend crossed it within the last [withinDays]
     * days (so it's news); else null.
     */
    fun newMilestone(goal: WeightGoal, trend: List<TrendPoint>, today: LocalDate, withinDays: Long = 7): Double? {
        val now = trendOn(trend, today) ?: return null
        val before = trendOn(trend, today.minusDays(withinDays)) ?: return null
        val reached = milestonesReached(goal, now)
        return if (reached > 0 && reached > milestonesReached(goal, before)) reached * WeightGoal.MILESTONE_KG else null
    }

    const val KG_PER_LB = 0.45359237
}
