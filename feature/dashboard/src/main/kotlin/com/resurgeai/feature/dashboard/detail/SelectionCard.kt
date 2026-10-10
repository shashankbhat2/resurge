package com.resurgeai.feature.dashboard.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.HypnoBlock
import com.resurgeai.core.designsystem.components.Hypnogram
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.feature.dashboard.Formatters
import com.resurgeai.feature.dashboard.SLEEP_LEVELS
import com.resurgeai.feature.dashboard.label
import com.resurgeai.feature.dashboard.level
import com.resurgeai.feature.dashboard.sleepStageColor
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Highlight for the selected day (Week tab) or week (Month tab): headline value, a few comparison
 * stats, and metric-specific detail (sleep hypnogram, heart-rate range, workouts).
 */
@Composable
internal fun SelectionCard(state: MetricDetailUiState, zone: ZoneId, modifier: Modifier = Modifier) {
    val period = state.periods.getOrNull(state.selected) ?: return
    val metric = state.metric
    val weekly = state.range == DetailRange.MONTH
    val previous = state.periods.getOrNull(state.selected - 1)?.value
    val average = state.periods.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()

    // For a single night, bedtime → wake sits at the right end of the date heading.
    val clock = clockFormatter()
    val nightTimes = if (metric == HealthMetric.SLEEP && !weekly) {
        state.sleepSessions
            .filter { it.end.atZone(zone).toLocalDate() == period.start }
            .maxByOrNull { it.duration }
            ?.let { "${it.start.atZone(zone).format(clock)} → ${it.end.atZone(zone).format(clock)}" }
    } else null

    Card(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(periodHeading(period, weekly), style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
            nightTimes?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
        }
        Spacer(Modifier.height(16.dp))
        when (metric) {
            HealthMetric.SLEEP -> SleepSelection(period, weekly, previous, state.sleepSessions, zone)
            HealthMetric.HEART_RATE -> HeartSelection(period, average, state.heartRanges)
            HealthMetric.EXERCISE -> ExerciseSelection(period, weekly, state.exerciseSessions, zone)
            else -> MagnitudeSelection(metric, period, weekly, previous, average)
        }
    }
}

// --- Per metric ---

@Composable
private fun MagnitudeSelection(metric: HealthMetric, p: PeriodValue, weekly: Boolean, previous: Double?, average: Double?) {
    val value = p.value
    StatRow(
        BigStat(value?.let { Formatters.seriesValue(metric, it) } ?: "—", if (weekly) "Avg per day" else metric.label),
        if (weekly) BigStat(p.total?.let { Formatters.seriesValue(metric, it) } ?: "${p.daysWithData}/${p.daysInPeriod}", if (p.total != null) "Total" else "Days with data")
        else deltaStat(value, average, "vs period avg"),
        deltaStat(value, previous, if (weekly) "vs previous week" else "vs previous day"),
    )
}

@Composable
private fun HeartSelection(p: PeriodValue, average: Double?, ranges: Map<LocalDate, Pair<Long, Long>>) {
    val inPeriod = ranges.filterKeys { !it.isBefore(p.start) && !it.isAfter(p.end) }.values
    val low = inPeriod.minOfOrNull { it.first }
    val high = inPeriod.maxOfOrNull { it.second }
    StatRow(
        BigStat(p.value?.let { "${it.roundToInt()}" } ?: "—", "Avg bpm"),
        BigStat(low?.toString() ?: "—", "Lowest"),
        BigStat(high?.toString() ?: "—", "Highest"),
    )
    if (p.value != null && average != null) {
        Spacer(Modifier.height(10.dp))
        val diff = p.value - average
        Text(
            when {
                abs(diff) < 1 -> "In line with your usual average."
                diff > 0 -> "${diff.roundToInt()} bpm above your average for this period."
                else -> "${(-diff).roundToInt()} bpm below your average for this period."
            },
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
        )
    }
}

@Composable
private fun SleepSelection(p: PeriodValue, weekly: Boolean, previous: Double?, sessions: List<SleepSession>, zone: ZoneId) {
    val nights = sessions
        .filter { val d = it.end.atZone(zone).toLocalDate(); !d.isBefore(p.start) && !d.isAfter(p.end) }
        .groupBy { it.end.atZone(zone).toLocalDate() }
        .map { (_, s) -> s.maxBy { it.duration } }
    val main = nights.maxByOrNull { it.duration }
    val asleepHours = if (nights.isEmpty()) null else nights.map { it.asleep.toMinutes() / 60.0 }.average()
    val quality = nights.mapNotNull { it.quality }.takeIf { it.isNotEmpty() }?.average()
    val clock = clockFormatter()

    StatRow(
        BigStat(asleepHours?.let(Formatters::hours) ?: "—", if (weekly) "Avg asleep" else "Time asleep"),
        BigStat(quality?.let { "${(it * 100).roundToInt()}%" } ?: "—", "Quality"),
        deltaStat(p.value, previous, if (weekly) "vs previous week" else "vs previous night"),
    )

    if (!weekly && main != null && main.timeline.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Hypnogram(
            blocks = hypnoBlocks(main),
            levelLabels = SLEEP_LEVELS.map { it.label },
        )
    } else if (weekly && nights.isNotEmpty()) {
        // A week: the typical bedtime and wake time. (A single night shows its times in the heading.)
        Spacer(Modifier.height(14.dp))
        val bed = averageClock(nights.map { it.start.atZone(zone).toLocalTime() })
        val wake = averageClock(nights.map { it.end.atZone(zone).toLocalTime() })
        Text(
            "Avg bedtime ${bed.format(clock)} · wake ${wake.format(clock)}",
            style = Resurge.type.label,
            color = Resurge.colors.textMuted,
        )
    }
}

@Composable
private fun ExerciseSelection(p: PeriodValue, weekly: Boolean, sessions: List<ExerciseSession>, zone: ZoneId) {
    val inPeriod = sessions
        .filter { val d = it.start.atZone(zone).toLocalDate(); !d.isBefore(p.start) && !d.isAfter(p.end) }
        .sortedBy { it.start }
    val total = inPeriod.fold(Duration.ZERO) { acc, s -> acc + s.duration }
    val activeDays = inPeriod.map { it.start.atZone(zone).toLocalDate() }.distinct().size
    val clock = clockFormatter()
    StatRow(
        BigStat(Formatters.duration(total), "Total time"),
        BigStat("${inPeriod.size}", if (inPeriod.size == 1) "Workout" else "Workouts"),
        if (weekly) BigStat("$activeDays/${p.daysInPeriod}", "Active days")
        else BigStat(inPeriod.maxByOrNull { it.duration }?.let { Formatters.duration(it.duration) } ?: "—", "Longest"),
    )
    if (inPeriod.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Divider()
        inPeriod.takeLast(5).forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title ?: s.type, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        s.start.atZone(zone).let { t -> if (weekly) "${t.format(DAY)}, ${t.format(clock)}" else t.format(clock) },
                        style = Resurge.type.caption,
                        color = Resurge.colors.textMuted,
                    )
                }
                Text(Formatters.duration(s.duration), style = Resurge.type.label)
            }
        }
    }
}

// --- Building blocks ---

private data class StatData(val value: String, val label: String, val valueColor: Color? = null, val arrow: String? = null)

private fun BigStat(value: String, label: String) = StatData(value, label)

/** Percentage change of [value] against [reference], coloured and with an arrow. */
@Composable
private fun deltaStat(value: Double?, reference: Double?, label: String): StatData {
    if (value == null || reference == null || reference == 0.0) return StatData("—", label)
    val pct = ((value - reference) / reference * 100).roundToInt()
    return when {
        pct > 0 -> StatData("$pct%", label, Resurge.colors.success, "↑")
        pct < 0 -> StatData("${-pct}%", label, Resurge.colors.danger, "↓")
        else -> StatData("0%", label)
    }
}

@Composable
private fun StatRow(vararg stats: StatData) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { s ->
            Column(Modifier.weight(1f)) {
                Text(
                    (s.arrow ?: "") + s.value,
                    style = Resurge.type.title,
                    color = s.valueColor ?: Resurge.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(s.label, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Average of clock times around midnight (e.g. 23:30 and 00:30 average to 00:00, not 12:00). */
private fun averageClock(times: List<java.time.LocalTime>): java.time.LocalTime {
    // Shift so the day "starts" at noon; bedtimes and wake times then average without wrapping.
    val minutes = times.map { ((it.toSecondOfDay() / 60) + 12 * 60) % (24 * 60) }.average()
    return java.time.LocalTime.ofSecondOfDay((((minutes.roundToInt() - 12 * 60 + 24 * 60) % (24 * 60)) * 60).toLong())
}

@Composable
private fun hypnoBlocks(night: SleepSession): List<HypnoBlock> {
    val total = Duration.between(night.start, night.end).toMillis().toFloat().coerceAtLeast(1f)
    fun frac(t: java.time.Instant) = Duration.between(night.start, t).toMillis() / total
    return night.timeline.map { seg ->
        HypnoBlock(frac(seg.start), frac(seg.end), seg.stage.level(), sleepStageColor(seg.stage))
    }
}

private fun periodHeading(p: PeriodValue, weekly: Boolean): String {
    val today = LocalDate.now()
    return when {
        !weekly && p.start == today -> "TODAY · ${p.start.format(DAY_LONG).uppercase()}"
        !weekly -> p.start.format(DAY_LONG).uppercase()
        !today.isBefore(p.start) && !today.isAfter(p.end) -> "THIS WEEK · ${p.start.format(SHORT)} – ${p.end.format(SHORT)}".uppercase()
        else -> "WEEK OF ${p.start.format(SHORT)} – ${p.end.format(SHORT)}".uppercase()
    }
}

private val DAY = DateTimeFormatter.ofPattern("EEE d")
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val SHORT = DateTimeFormatter.ofPattern("d MMM")
