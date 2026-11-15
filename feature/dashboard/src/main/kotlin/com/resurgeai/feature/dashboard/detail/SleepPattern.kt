package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.feature.dashboard.SLEEP_LEVELS
import com.resurgeai.feature.dashboard.level
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Which nights a [SleepPattern] covers, by the day the night ended. */
enum class NightSet(val label: String) {
    ALL("All nights"),
    WEEKNIGHTS("Weeknights"),
    WEEKENDS("Weekends");

    fun includes(wake: LocalDate): Boolean = when (this) {
        ALL -> true
        WEEKNIGHTS -> wake.dayOfWeek !in WEEKEND
        WEEKENDS -> wake.dayOfWeek in WEEKEND
    }

    private companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/**
 * Many nights folded onto one clock: for each stage row (awake, REM, light, deep, top to bottom) and
 * each [binMinutes] slot from [windowStart] to [windowEnd], the share of nights in that stage then.
 */
data class SleepPattern(
    val nights: Int,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val binMinutes: Int,
    /** Per row in [SLEEP_LEVELS] order, one value 0..1 per slot. */
    val frequency: List<List<Float>>,
    /** Average minutes per night for each row in [SLEEP_LEVELS] order. */
    val stageMinutes: List<Long>,
    /** Median bedtime / wake time, or null without nights. */
    val usualBed: LocalTime?,
    val usualWake: LocalTime?,
    /** Average time asleep per night. */
    val asleep: Duration?,
) {
    val bins: Int get() = frequency.firstOrNull()?.size ?: 0
    val windowMinutes: Int get() = bins * binMinutes

    /** 0..1 position of [time] across the window (clamped). */
    fun fractionOf(time: LocalTime): Float = (offset(time) - offset(windowStart)).toFloat().div(windowMinutes).coerceIn(0f, 1f)

    /** The clock time at [fraction] of the window, to the minute. */
    fun timeAt(fraction: Float): LocalTime = windowStart.plusMinutes((fraction.coerceIn(0f, 1f) * windowMinutes).toLong())

    /** The most common row at [fraction] and its share of nights; null when most nights weren't in bed then. */
    fun stageAt(fraction: Float): Pair<SleepStage, Float>? {
        if (bins == 0) return null
        val bin = (fraction.coerceIn(0f, 1f) * bins).toInt().coerceAtMost(bins - 1)
        val shares = frequency.map { it[bin] }
        if (shares.sum() < 0.5f) return null
        val row = shares.indices.maxBy { shares[it] }
        return SLEEP_LEVELS[row] to shares[row]
    }

    companion object {
        val DEFAULT_START: LocalTime = LocalTime.of(23, 0)
        val DEFAULT_END: LocalTime = LocalTime.of(9, 0)
        private const val MIN_WINDOW = 8 * 60

        /** Minutes after noon, so a night (evening → morning) is one increasing range. */
        internal fun offset(t: LocalTime): Int = ((t.toSecondOfDay() / 60) - 720 + 1440) % 1440

        /** Builds the pattern for the [set] of nights among [sessions] (each night = sessions ending on one day). */
        fun build(sessions: List<SleepSession>, zone: ZoneId, set: NightSet = NightSet.ALL, binMinutes: Int = 10): SleepPattern {
            val nights = sessions
                .groupBy { it.end.atZone(zone).toLocalDate() }
                .filterKeys(set::includes)
                .toSortedMap()
            val beds = nights.values.map { s -> offset(s.minOf { it.start }.atZone(zone).toLocalTime()) }.sorted()
            val wakes = nights.values.map { s -> offset(s.maxOf { it.end }.atZone(zone).toLocalTime()) }.sorted()

            // The window runs from the hour before most bedtimes to the hour after most wake-ups (at least 8 hours).
            var start = beds.percentile(0.1)?.let { it / 60 * 60 } ?: offset(DEFAULT_START)
            var end = wakes.percentile(0.9)?.let { (it + 59) / 60 * 60 } ?: offset(DEFAULT_END)
            if (end - start < MIN_WINDOW) {
                start -= (MIN_WINDOW - (end - start)) / 2 / 60 * 60
                end = start + MIN_WINDOW
            }
            start = start.coerceAtLeast(0)
            end = end.coerceIn(start + MIN_WINDOW.coerceAtMost(1440 - start), 1440)
            val bins = (end - start) / binMinutes

            val counts = List(SLEEP_LEVELS.size) { FloatArray(bins) }
            val minutes = LongArray(SLEEP_LEVELS.size)
            var asleep = Duration.ZERO
            nights.forEach { (wakeDay, night) ->
                // Noon the day before waking anchors the night on the shared clock.
                val anchor: Instant = wakeDay.minusDays(1).atTime(12, 0).atZone(zone).toInstant()
                for (b in 0 until bins) {
                    val t = anchor.plusSeconds(((start + b * binMinutes + binMinutes / 2) * 60).toLong())
                    stageAt(night, t)?.let { counts[it.level()][b] += 1f }
                }
                night.forEach { s ->
                    s.stages.forEach { (stage, d) -> minutes[stage.level()] += d.toMinutes() }
                    if (s.stages.isEmpty()) minutes[SleepStage.SLEEPING.level()] += s.duration.toMinutes()
                    asleep += s.asleep
                }
            }
            val n = nights.size
            return SleepPattern(
                nights = n,
                windowStart = LocalTime.NOON.plusMinutes(start.toLong()),
                windowEnd = LocalTime.NOON.plusMinutes(end.toLong()),
                binMinutes = binMinutes,
                frequency = counts.map { row -> row.map { if (n == 0) 0f else it / n } },
                stageMinutes = minutes.map { if (n == 0) 0L else it / n },
                usualBed = beds.percentile(0.5)?.let { LocalTime.NOON.plusMinutes(it.toLong()) },
                usualWake = wakes.percentile(0.5)?.let { LocalTime.NOON.plusMinutes(it.toLong()) },
                asleep = if (n == 0) null else asleep.dividedBy(n.toLong()),
            )
        }

        /** The stage at [t] in a night: its timeline segment, else plain "asleep" inside a stage-less session. */
        private fun stageAt(night: List<SleepSession>, t: Instant): SleepStage? {
            for (s in night) {
                if (t < s.start || t >= s.end) continue
                if (s.timeline.isEmpty()) return SleepStage.SLEEPING
                return s.timeline.firstOrNull { t >= it.start && t < it.end }?.stage
            }
            return null
        }

        private fun List<Int>.percentile(p: Double): Int? = if (isEmpty()) null else this[((size - 1) * p).toInt()]
    }
}
