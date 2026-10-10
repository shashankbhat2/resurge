package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.feature.dashboard.Formatters
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

/** Per-metric visual for one row. All positions are fractions of an axis shared by every row. */
sealed interface DayViz {
    data class Magnitude(val fraction: Float, val marker: Float?) : DayViz
    data class Window(val start: Float, val end: Float, val stages: List<Pair<Float, SleepStage>>) : DayViz
    data class Range(val low: Float, val high: Float, val mid: Float?) : DayViz
    data class Dot(val position: Float) : DayViz
    data class Segments(val fractions: List<Float>) : DayViz
    data object Empty : DayViz
}

/** One row of the detail list: a day (Week tab) or a calendar week (Month tab). */
data class DayRow(
    val periodIndex: Int,
    val start: LocalDate,
    val end: LocalDate,
    val value: Double?,
    val viz: DayViz,
    /** Small caption under the visual, e.g. "23:44 → 07:18" or "2 workouts". */
    val detail: String?,
) {
    val date: LocalDate get() = start
}

/** Axis labels for the list header as (text, position 0..1), or empty when the axis needs no labels. */
data class DayAxis(val labels: List<Pair<String, Float>>, val gridLines: List<Float> = emptyList())

object DayRows {

    /**
     * Builds rows for [periods] (days or weeks), newest first. For weeks, sleep windows are the
     * average bedtime/wake time, heart rate spans the week's lowest–highest bpm, and exercise
     * shows one segment per active day.
     */
    fun build(
        metric: HealthMetric,
        periods: List<PeriodValue>,
        sleep: List<SleepSession>,
        exercise: List<ExerciseSession>,
        heartRanges: Map<LocalDate, Pair<Long, Long>>,
        zone: ZoneId,
    ): Pair<DayAxis, List<DayRow>> {
        val weekly = periods.any { it.daysInPeriod > 1 }
        val rows = when (metric) {
            HealthMetric.SLEEP -> sleepRows(periods, sleep, zone)
            HealthMetric.EXERCISE -> exerciseRows(periods, exercise, zone, weekly)
            // Heart rate rows are just date + number; ranges live in the selection card.
            HealthMetric.HEART_RATE -> periods.mapIndexed { i, p -> row(i, p, DayViz.Empty, null) }
            HealthMetric.WEIGHT -> weightRows(periods)
            else -> magnitudeRows(metric, periods)
        }
        val axis = when (metric) {
            else -> DayAxis(emptyList())
        }
        return axis to rows.sortedByDescending { it.start }
    }

    private fun row(i: Int, p: PeriodValue, viz: DayViz, detail: String?) =
        DayRow(i, p.start, p.end, p.value, viz, detail)

    private fun magnitudeRows(metric: HealthMetric, periods: List<PeriodValue>): List<DayRow> {
        val values = periods.mapNotNull { it.value }
        val max = values.maxOrNull()?.takeIf { it > 0 }
        val avg = values.takeIf { it.isNotEmpty() }?.average()
        return periods.mapIndexed { i, p ->
            val viz = if (p.value != null && max != null) {
                DayViz.Magnitude((p.value / max).toFloat(), avg?.let { (it / max).toFloat() })
            } else DayViz.Empty
            row(i, p, viz, weekDetail(metric, p))
        }
    }

    private fun weightRows(periods: List<PeriodValue>): List<DayRow> {
        val values = periods.mapNotNull { it.value }
        val lo = (values.minOrNull() ?: 0.0) - 0.5
        val hi = (values.maxOrNull() ?: 0.0) + 0.5
        return periods.mapIndexed { i, p ->
            row(i, p, p.value?.let { DayViz.Dot(frac(it, lo, hi)) } ?: DayViz.Empty, weekDetail(HealthMetric.WEIGHT, p))
        }
    }

    /** Sleep rows show only duration (the row value) and a sleep-quality bar; times live in the detail card. */
    private fun sleepRows(periods: List<PeriodValue>, sleep: List<SleepSession>, zone: ZoneId) =
        periods.mapIndexed { i, p ->
            val quality = sleep
                .filter { it.end.atZone(zone).toLocalDate() in p }
                .mapNotNull { it.quality }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat()
            row(i, p, quality?.let { DayViz.Magnitude(it, null) } ?: DayViz.Empty, null)
        }

    private fun exerciseRows(periods: List<PeriodValue>, exercise: List<ExerciseSession>, zone: ZoneId, weekly: Boolean): List<DayRow> {
        val byPeriod = periods.map { p -> exercise.filter { it.start.atZone(zone).toLocalDate() in p }.sortedBy { it.start } }
        // In week rows each segment is a workout; in month rows each segment is an active day.
        val segmentsPerPeriod = byPeriod.map { sessions ->
            if (!weekly) sessions.map { it.duration.toMinutes() }
            else sessions.groupBy { it.start.atZone(zone).toLocalDate() }.toSortedMap().values.map { d -> d.sumOf { it.duration.toMinutes() } }
        }
        val maxTotal = segmentsPerPeriod.maxOfOrNull { it.sum() }?.coerceAtLeast(30) ?: 60
        return periods.mapIndexed { i, p ->
            val sessions = byPeriod[i]
            val segments = segmentsPerPeriod[i]
            row(
                i, p,
                if (segments.isEmpty()) DayViz.Empty else DayViz.Segments(segments.map { it.toFloat() / maxTotal }),
                when {
                    sessions.isEmpty() -> null
                    weekly -> "${segments.size} ${if (segments.size == 1) "day" else "days"} · ${sessions.size} ${if (sessions.size == 1) "workout" else "workouts"}"
                    sessions.size == 1 -> sessions.first().title ?: sessions.first().type
                    else -> "${sessions.size} workouts"
                },
            )
        }
    }

    /** Month rows: weekly total for additive metrics, otherwise how many days had data. */
    private fun weekDetail(metric: HealthMetric, p: PeriodValue): String? {
        if (p.daysInPeriod == 1) return null
        return p.total?.let { "Total ${Formatters.seriesValue(metric, it)}" }
            ?: "${p.daysWithData}/${p.daysInPeriod} days"
    }

    private fun frac(v: Double, lo: Double, hi: Double): Float =
        if (hi <= lo) 0.5f else ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)

    private operator fun PeriodValue.contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}
