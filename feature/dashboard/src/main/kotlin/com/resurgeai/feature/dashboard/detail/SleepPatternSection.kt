package com.resurgeai.feature.dashboard.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.DotRow
import com.resurgeai.core.designsystem.components.StippleChart
import com.resurgeai.core.designsystem.components.StippleRow
import com.resurgeai.core.designsystem.components.StippleTick
import com.resurgeai.core.designsystem.theme.LocalUse24HourClock
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.SleepSession
import com.resurgeai.feature.dashboard.SLEEP_LEVELS
import com.resurgeai.feature.dashboard.label
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The sleep screen's hero: the last 30 nights as a stage-by-time stipple, a scrub line that reads out
 * the usual stage at any time, and a row per set of nights (all, weeknights, weekends) to switch the
 * chart between them.
 */
@Composable
internal fun SleepPatternSection(nights: List<SleepSession>, zone: ZoneId, modifier: Modifier = Modifier) {
    val patterns = remember(nights, zone) { NightSet.entries.associateWith { SleepPattern.build(nights, zone, it) } }
    var set by rememberSaveable { mutableStateOf(NightSet.ALL) }
    val pattern = patterns.getValue(set).takeIf { it.nights > 0 } ?: patterns.getValue(NightSet.ALL)
    var marker by rememberSaveable(set) { mutableStateOf(pattern.usualWake?.let(pattern::fractionOf) ?: 0.8f) }
    val clock = clockFormatter()
    val hour = if (LocalUse24HourClock.current) HOUR_24 else HOUR_12

    Column(modifier) {
        Text("Sleep · last ${pattern.nights} nights".uppercase(), style = Resurge.type.overline, color = Resurge.colors.text)
        Spacer(Modifier.height(4.dp))
        val usual = if (pattern.usualBed != null && pattern.usualWake != null) "Usually ${pattern.usualBed.format(clock)} — ${pattern.usualWake.format(clock)}" else "Not enough nights yet"
        Text(usual.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        Spacer(Modifier.height(28.dp))

        val at = pattern.timeAt(marker)
        val stage = pattern.stageAt(marker)
        StippleChart(
            rows = SLEEP_LEVELS.mapIndexed { i, s -> StippleRow(s.label, minutes(pattern.stageMinutes[i]), pattern.frequency[i]) },
            ticks = hourTicks(pattern).map { (t, f) -> StippleTick(f, if (t.hour % 2 == 0) t.format(hour) else "") },
            marker = marker,
            onMarkerChange = { marker = it },
            markerLabel = {
                Row {
                    Text(at.format(clock).uppercase(), style = Resurge.type.overline, color = Resurge.colors.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (stage?.let { (s, share) -> "Usually ${s.label} · ${(share * 100).toInt()}%" } ?: "Usually up").uppercase(),
                        style = Resurge.type.overline,
                        color = Resurge.colors.text,
                    )
                }
            },
            description = "Sleep stages over the last ${pattern.nights} nights",
        )
        Spacer(Modifier.height(20.dp))
        NightSet.entries.forEach { s ->
            val p = patterns.getValue(s)
            if (p.nights == 0) return@forEach
            DotRow(
                label = s.label,
                cells = listOf(
                    "Bed ${p.usualBed?.format(clock) ?: "—"}",
                    "Up ${p.usualWake?.format(clock) ?: "—"}",
                    p.asleep?.let(::duration) ?: "—",
                ),
                highlight = 1,
                trailing = "${p.nights}N",
                selected = s == set,
                onClick = { set = s },
            )
        }
    }
}

/** Hour marks across the window, with their position. */
private fun hourTicks(p: SleepPattern): List<Pair<LocalTime, Float>> {
    if (p.windowMinutes == 0) return emptyList()
    val first = p.windowStart.let { if (it.minute == 0) it else it.plusHours(1).withMinute(0) }
    return generateSequence(first) { it.plusHours(1) }
        .map { it to p.fractionOf(it) }
        .takeWhile { (t, f) -> f < 1f || t == p.windowEnd }
        .take(25)
        .toList()
}

private fun minutes(m: Long): String = duration(Duration.ofMinutes(m))

private fun duration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutesPart()
    return if (h == 0L) "${m}M" else "${h}H %02dM".format(m)
}

private val HOUR_24 = DateTimeFormatter.ofPattern("HH:mm")
private val HOUR_12 = DateTimeFormatter.ofPattern("ha")
