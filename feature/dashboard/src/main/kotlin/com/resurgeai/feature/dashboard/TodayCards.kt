package com.resurgeai.feature.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import com.resurgeai.core.designsystem.components.Divider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.model.UnitSystem
import java.time.Duration
import java.time.ZoneId
import java.util.Locale

/**
 * Today's health numbers as compact tiles, two per row: the value Health Connect reports for today
 * and a one-line note saying what the number is. Tapping a tile opens its detail.
 */
@Composable
internal fun HealthTiles(day: DailyHealth, targets: Targets, weight: WeightGlance?, units: UnitSystem, onOpen: (HealthMetric) -> Unit) {
    val zone = ZoneId.systemDefault()
    val clock = clockFormatter()
    val night = day.sleep.takeIf { it.isNotEmpty() }
    val hr = day.heartRate
    val stepGoal = targets.steps.toLong()
    val sleepGoal = Duration.ofMinutes(targets.sleepMinutes.toLong())
    val rows = buildList {
        add(
            HealthRow(
                HealthMetric.SLEEP, "Sleep", ResurgeIcons.Bed, Resurge.colors.sleep,
                value = day.asleepDuration?.let(Formatters::duration),
                note = night?.let {
                    val asleep = day.asleepDuration
                    if (asleep != null && asleep >= sleepGoal) return@let "Goal ${Targets.formatSleep(targets.sleepMinutes)} met"
                    val bed = it.minOf { s -> s.start }.atZone(zone).toLocalTime().format(clock)
                    val wake = it.maxOf { s -> s.end }.atZone(zone).toLocalTime().format(clock)
                    "$bed – $wake"
                } ?: "None recorded last night",
            ),
        )
        add(
            HealthRow(
                HealthMetric.STEPS, "Steps", ResurgeIcons.Footprints, Resurge.colors.activity,
                value = day.steps?.let(Formatters::count),
                note = day.steps?.let { steps ->
                    if (steps >= stepGoal) "Goal ${Formatters.count(stepGoal)} met"
                    else "${Formatters.count(stepGoal - steps)} to go"
                } ?: "None yet today",
            ),
        )
        add(
            HealthRow(
                HealthMetric.CALORIES_BURNED, "Burned", ResurgeIcons.Flame, Resurge.colors.heart,
                value = day.caloriesTotal?.let { "${Formatters.kcal(it)} kcal" },
                note = day.caloriesActive?.let { "${Formatters.kcal(it)} from activity" } ?: "So far today",
            ),
        )
        add(
            HealthRow(
                HealthMetric.HEART_RATE, if (hr?.restingBpm != null) "Resting heart rate" else "Avg heart rate", ResurgeIcons.Heartbeat, Resurge.colors.heart,
                value = (hr?.restingBpm ?: hr?.avgBpm)?.let { "$it bpm" },
                note = if (hr?.minBpm != null && hr.maxBpm != null) "Range ${hr.minBpm}–${hr.maxBpm} today" else "No readings yet",
            ),
        )
        day.distanceMeters?.let { m ->
            add(HealthRow(HealthMetric.DISTANCE, "Distance", ResurgeIcons.Distance, Resurge.colors.water, "${Formatters.kilometres(m)} km", "Walked and run today"))
        }
        add(weightRow(weight, units))
    }
    // One quiet panel of table rows (like an instrument readout) rather than a grid of tiles.
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Divider(Modifier.padding(start = 16.dp))
            HealthLine(row) { onOpen(row.metric) }
        }
    }
}

/** One metric as a table row: status dot in the metric's colour, caps label, value, and a dim note. */
@Composable
private fun HealthLine(row: HealthRow, onClick: () -> Unit) {
    UnstyledButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), indication = LocalIndication.current, contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(Resurge.shape.pill).background(row.color))
            Spacer(Modifier.width(10.dp))
            Text(row.label.uppercase(), style = Resurge.type.overline, color = Resurge.colors.textMuted, maxLines = 1, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(row.value ?: "—", style = Resurge.type.headline, maxLines = 1)
                Text(row.note.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
        }
    }
}


/** Weight is logged in Resurge, so its tile shows without Health Connect too. */
@Composable
internal fun WeightTileRow(weight: WeightGlance?, units: UnitSystem, onOpen: (HealthMetric) -> Unit) {
    val row = weightRow(weight, units)
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
        HealthLine(row) { onOpen(row.metric) }
    }
}

/** The trend and its change this week, or a nudge to log the first weigh-in. */
@Composable
private fun weightRow(weight: WeightGlance?, units: UnitSystem): HealthRow {
    val imperial = units == UnitSystem.IMPERIAL
    fun conv(kg: Double) = if (imperial) kg / WeightMath.KG_PER_LB else kg
    val unit = if (imperial) "lb" else "kg"
    val note = when {
        weight == null -> "Tap to log a weigh-in"
        weight.weekChangeKg == null -> "Trend weight"
        kotlin.math.abs(conv(weight.weekChangeKg)) < 0.05 -> "Steady this week"
        else -> String.format(Locale.getDefault(), "%s%.1f %s this week", if (weight.weekChangeKg < 0) "−" else "+", kotlin.math.abs(conv(weight.weekChangeKg)), unit)
    }
    return HealthRow(
        HealthMetric.WEIGHT, "Weight", ResurgeIcons.Scale, Resurge.colors.accent,
        value = weight?.let { String.format(Locale.getDefault(), "%.1f %s", conv(it.kg), unit) },
        note = note,
    )
}

private class HealthRow(
    val metric: HealthMetric,
    val label: String,
    @DrawableRes val icon: Int,
    val color: Color,
    val value: String?,
    val note: String,
)


// --- Loading ---

private val TILE_GAP = 12.dp

/** Shimmering tiles shaped like [HealthTiles], shown until the first Health Connect read lands. */
@Composable
internal fun TodaySkeleton() {
    SkeletonGroup {
        Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                    repeat(2) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(Resurge.shape.md)
                                .background(Resurge.colors.surface)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            SkeletonLine(56.dp, 10.dp)
                            Spacer(Modifier.height(10.dp))
                            SkeletonLine(84.dp, 20.dp)
                            Spacer(Modifier.height(8.dp))
                            SkeletonLine(96.dp, 10.dp)
                        }
                    }
                }
            }
        }
    }
}
