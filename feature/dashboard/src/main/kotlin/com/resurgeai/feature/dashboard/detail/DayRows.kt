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
    /** Sleep clock runs 6 pm the evening before → noon on the day the night ended. */
    private const val SLEEP_AXIS_START_HOUR = 18
    private const val SLEEP_AXIS_MINUTES = 18 * 60
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val STAGE_ORDER = listOf(
        SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.SLEEPING, SleepStage.UNKNOWN, SleepStage.AWAKE,
    )

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
            HealthMetric.SLEEP -> sleepRows(periods, sleep, zone, weekly)
            HealthMetric.EXERCISE -> exerciseRows(periods, exercise, zone, weekly)
            // Heart rate rows are just date + number; ranges live in the selection card.
            HealthMetric.HEART_RATE -> periods.mapIndexed { i, p -> row(i, p, DayViz.Empty, null) }
            HealthMetric.WEIGHT -> weightRows(periods)
            else -> magnitudeRows(metric, periods)
        }
        val axis = when (metric) {
            HealthMetric.SLEEP -> DayAxis(
                listOf("6 pm" to 0f, "12 am" to 1f / 3, "6 am" to 2f / 3, "12 pm" to 1f),
                gridLines = listOf(1f / 3, 2f / 3),
            )
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

    private fun sleepRows(periods: List<PeriodValue>, sleep: List<SleepSession>, zone: ZoneId, weekly: Boolean) =
        periods.mapIndexed { i, p ->
            // Main (longest) night per day the night ended on.
            val nights = sleep
                .filter { it.end.atZone(zone).toLocalDate() in p }
                .groupBy { it.end.atZone(zone).toLocalDate() }
                .mapValues { (_, s) -> s.maxBy { it.duration } }
            if (nights.isEmpty()) return@mapIndexed row(i, p, DayViz.Empty, null)

            val starts = nights.map { (day, n) -> clockMinutes(day, n.start, zone) }
            val ends = nights.map { (day, n) -> clockMinutes(day, n.end, zone) }
            val stageMinutes = STAGE_ORDER.mapNotNull { stage ->
                nights.values.sumOf { it.stages[stage]?.toMinutes() ?: 0L }.takeIf { it > 0 }?.let { it.toFloat() to stage }
            }.ifEmpty { listOf(1f to SleepStage.SLEEPING) }
            val startAvg = starts.average()
            val endAvg = ends.average()
            val label = "${clockLabel(startAvg)} → ${clockLabel(endAvg)}"
            row(
                i, p,
                DayViz.Window((startAvg / SLEEP_AXIS_MINUTES).toFloat(), (endAvg / SLEEP_AXIS_MINUTES).toFloat(), stageMinutes),
                if (weekly) "avg $label" else label,
            )
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

    /** Minutes from 6 pm the evening before [day] to [t]. */
    private fun clockMinutes(day: LocalDate, t: Instant, zone: ZoneId): Double {
        val axisStart = day.minusDays(1).atTime(LocalTime.of(SLEEP_AXIS_START_HOUR, 0)).atZone(zone).toInstant()
        return Duration.between(axisStart, t).toMinutes().toDouble()
    }

    private fun clockLabel(minutesFromAxisStart: Double): String =
        LocalTime.of(SLEEP_AXIS_START_HOUR, 0).plusMinutes(minutesFromAxisStart.roundToLong()).format(TIME)

    private fun frac(v: Double, lo: Double, hi: Double): Float =
        if (hi <= lo) 0.5f else ((v - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)

    private operator fun PeriodValue.contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}
