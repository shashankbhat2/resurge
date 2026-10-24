package com.resurgeai.feature.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.MiniBars
import com.resurgeai.core.designsystem.components.signalColor
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.PaceSignal
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.model.UnitSystem
import java.util.Locale
import kotlin.math.abs

/**
 * Today's vitals as tiles, two per row: the value, a week of bars (today in white), and one line on
 * how it stands, coloured up / mid / down when it bears on a goal. Tapping a tile opens its detail.
 */
@Composable
internal fun VitalTiles(
    day: DailyHealth,
    targets: Targets,
    sparklines: Map<HealthMetric, List<Double?>>,
    weight: WeightGlance?,
    eatenKcal: Double,
    units: UnitSystem,
    losingWeight: Boolean?,
    onOpen: (HealthMetric) -> Unit,
) {
    val hr = day.heartRate
    val stepGoal = targets.steps.toLong()
    val tiles = buildList {
        val asleep = day.asleepDuration
        add(
            Vital(
                HealthMetric.SLEEP, "Sleep", ResurgeIcons.Bed,
                value = asleep?.let(Formatters::duration),
                bars = sparklines[HealthMetric.SLEEP],
                trend = asleep?.let { a ->
                    val short = targets.sleepMinutes - a.toMinutes()
                    when {
                        short <= 0 -> "Goal ${Targets.formatSleep(targets.sleepMinutes)} met" to PaceSignal.UP
                        short <= 30 -> "${short} min short" to PaceSignal.MID
                        else -> "${Targets.formatSleep(short.toInt())} short" to PaceSignal.DOWN
                    }
                } ?: ("None recorded last night" to null),
            ),
        )
        add(
            Vital(
                HealthMetric.STEPS, "Steps", ResurgeIcons.Footprints,
                value = day.steps?.let(Formatters::count),
                bars = sparklines[HealthMetric.STEPS],
                // The day isn't over, so short of the goal is "still to go", not a miss.
                trend = day.steps?.let { steps ->
                    if (steps >= stepGoal) "Goal ${Formatters.count(stepGoal)} met" to PaceSignal.UP
                    else "${Formatters.count(stepGoal - steps)} to go" to PaceSignal.MID
                } ?: ("None yet today" to null),
            ),
        )
        add(
            Vital(
                HealthMetric.HEART_RATE, if (hr?.restingBpm != null) "Resting HR" else "Heart rate", ResurgeIcons.Heartbeat,
                value = (hr?.restingBpm ?: hr?.avgBpm)?.let { "$it" }, unit = " bpm",
                bars = sparklines[HealthMetric.HEART_RATE],
                trend = (if (hr?.minBpm != null && hr.maxBpm != null) "Range ${hr.minBpm}–${hr.maxBpm} today" else "No readings yet") to null,
            ),
        )
        add(weightVital(weight, units, losingWeight))
        add(
            Vital(
                HealthMetric.CALORIES_BURNED, "Burned", ResurgeIcons.Flame,
                value = day.caloriesTotal?.let(Formatters::kcal), unit = " kcal",
                bars = sparklines[HealthMetric.CALORIES_BURNED],
                trend = (day.caloriesActive?.let { "${Formatters.kcal(it)} from activity" } ?: "So far today") to null,
            ),
        )
        add(
            Vital(
                null, "Eaten", ResurgeIcons.Bowl,
                value = eatenKcal.takeIf { it > 0 }?.let(Formatters::kcal), unit = " kcal",
                bars = null,
                trend = (targets.calories - eatenKcal).let { left ->
                    when {
                        eatenKcal <= 0 -> "Nothing logged yet" to null
                        left >= 0 -> "${Formatters.kcal(left)} left of ${Formatters.count(targets.calories.toLong())}" to null
                        -left <= targets.calories * 0.1 -> "${Formatters.kcal(-left)} over" to PaceSignal.MID
                        else -> "${Formatters.kcal(-left)} over" to PaceSignal.DOWN
                    }
                },
            ),
        )
    }
    VitalGrid(tiles, onOpen)
}

/** Weight is logged in Resurge, so its tile shows without Health Connect too. */
@Composable
internal fun WeightVitalRow(weight: WeightGlance?, units: UnitSystem, losingWeight: Boolean?, onOpen: (HealthMetric) -> Unit) {
    VitalGrid(listOf(weightVital(weight, units, losingWeight)), onOpen)
}

@Composable
private fun VitalGrid(tiles: List<Vital>, onOpen: (HealthMetric) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                pair.forEach { tile ->
                    VitalTile(tile, Modifier.weight(1f).fillMaxHeight()) { tile.metric?.let(onOpen) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val TILE_GAP = 10.dp

/**
 * The trend weight and its change this week. With a weight goal, moving its way is up and the
 * other way down ([losingWeight] null: no goal, no colour).
 */
private fun weightVital(weight: WeightGlance?, units: UnitSystem, losingWeight: Boolean?): Vital {
    val imperial = units == UnitSystem.IMPERIAL
    fun conv(kg: Double) = if (imperial) kg / WeightMath.KG_PER_LB else kg
    val unit = if (imperial) "lb" else "kg"
    val change = weight?.weekChangeKg
    val trend = when {
        weight == null -> "Tap to log a weigh-in" to null
        change == null -> "Trend weight" to null
        abs(conv(change)) < 0.05 -> "Steady this week" to null
        else -> {
            val text = String.format(Locale.getDefault(), "%s%.1f %s this week", if (change < 0) "−" else "+", abs(conv(change)), unit)
            text to losingWeight?.let { losing -> if ((change < 0) == losing) PaceSignal.UP else PaceSignal.DOWN }
        }
    }
    return Vital(
        HealthMetric.WEIGHT, "Weight", ResurgeIcons.Scale,
        value = weight?.let { String.format(Locale.getDefault(), "%.1f", conv(it.kg)) }, unit = " $unit",
        bars = weight?.spark?.takeLast(7)?.let { s ->
            // Weight barely moves day to day: show it relative to the week's low so the shape reads.
            val low = s.minOrNull() ?: 0.0
            s.map { v -> (v - low) + (s.maxOrNull()!! - low).coerceAtLeast(0.1) * 0.25 }
        },
        trend = trend,
    )
}

private class Vital(
    /** Null: no detail screen. */
    val metric: HealthMetric?,
    val label: String,
    @DrawableRes val icon: Int,
    val value: String?,
    val bars: List<Double?>?,
    val trend: Pair<String, PaceSignal?>,
    val unit: String = "",
)

@Composable
private fun VitalTile(tile: Vital, modifier: Modifier, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        enabled = tile.metric != null,
        modifier = modifier
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tile.icon, contentDescription = null, tint = Resurge.colors.textMuted, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(tile.label, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    tile.value ?: "—",
                    style = Resurge.type.metric.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = if (tile.value != null) Resurge.colors.text else Resurge.colors.textSubtle,
                    maxLines = 1,
                )
                if (tile.value != null && tile.unit.isNotEmpty()) {
                    Text(tile.unit, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            tile.bars?.takeIf { b -> b.any { it != null && it > 0 } }?.let {
                Spacer(Modifier.height(10.dp))
                MiniBars(it.takeLast(7))
            }
            Spacer(Modifier.height(8.dp))
            val (text, signal) = tile.trend
            Text(
                text,
                style = Resurge.type.caption.copy(fontWeight = if (signal != null) FontWeight.SemiBold else FontWeight.Medium),
                color = signal?.let { signalColor(it) } ?: Resurge.colors.textSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --- Loading ---

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
