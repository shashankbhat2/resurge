package com.resurgeai.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.MetricCell
import com.resurgeai.core.designsystem.components.MetricGroup
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.RadialBars
import com.resurgeai.core.designsystem.components.RangeDial
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.SkeletonCircle
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepStage
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Default daily goals until they're user-configurable. */
internal object DashboardGoals {
    const val STEPS = 10_000.0
    val SLEEP: Duration = Duration.ofHours(8)

    /** Heart-rate dial scale (bpm). */
    const val HR_MIN = 40f
    const val HR_MAX = 200f
}

// --- Insight ---

/**
 * Latest assistant insight: first row is the AI icon and when it was written, second row the text.
 * Placeholder copy until the assistant lands (Phase 4).
 */
@Composable
internal fun CoachInsight(text: String, time: LocalTime) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.CoachFilled, contentDescription = "Assistant insight", tint = Resurge.colors.accent, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Today, ${time.format(clockFormatter())}", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(text, style = Resurge.type.body.copy(fontSize = 17.sp, lineHeight = 27.sp), color = Resurge.colors.text)
    }
}

// --- Sections ---

/** Heart → Sleep | Steps → Calories | Distance → Weight | Exercise, one container per group. */
@Composable
internal fun TodayGrid(state: DashboardUiState, day: DailyHealth, onOpen: (HealthMetric) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
        HeartTile(day) { onOpen(HealthMetric.HEART_RATE) }
        MetricGroup(
            cells = listOf(
                { SleepTile(day, it) { onOpen(HealthMetric.SLEEP) } },
                { StepsTile(day, it) { onOpen(HealthMetric.STEPS) } },
            ),
        )
        MetricGroup(
            cells = listOf(
                { CaloriesTile(day, it) { onOpen(HealthMetric.CALORIES_BURNED) } },
                { DistanceTile(state, day, it) { onOpen(HealthMetric.DISTANCE) } },
            ),
        )
        MetricGroup(
            cells = listOf(
                { WeightTile(state, day, it) { onOpen(HealthMetric.WEIGHT) } },
                { ExerciseTile(state, day, it) { onOpen(HealthMetric.EXERCISE) } },
            ),
        )
    }
}

// --- Tiles ---

@Composable
private fun HeartTile(day: DailyHealth, onClick: () -> Unit) {
    val hr = day.heartRate
    val heart = Resurge.colors.heart
    val headline = hr?.restingBpm ?: hr?.avgBpm
    fun scale(bpm: Long) = (bpm - DashboardGoals.HR_MIN) / (DashboardGoals.HR_MAX - DashboardGoals.HR_MIN)

    Card(inverse = true, onClick = onClick, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RangeDial(
                low = hr?.minBpm?.let(::scale),
                high = hr?.maxBpm?.let(::scale),
                marker = headline?.let(::scale),
                color = heart,
                trackColor = Resurge.colors.onInverse.copy(alpha = 0.08f),
                markerColor = Resurge.colors.onInverse,
                markerRing = Resurge.colors.inverse,
                strokeWidth = 9.dp,
                modifier = Modifier.size(80.dp),
            ) { TileIcon(ResurgeIcons.Heartbeat, heart, size = 26) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hr?.restingBpm != null) "Resting heart rate" else "Heart rate",
                    style = Resurge.type.label,
                    color = Resurge.colors.onInverseMuted,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(headline?.toString() ?: "—", style = Resurge.type.title, color = Resurge.colors.onInverse)
                    Spacer(Modifier.width(4.dp))
                    Text("bpm", style = Resurge.type.label, color = Resurge.colors.onInverseMuted, modifier = Modifier.padding(bottom = 3.dp))
                }
                hr?.avgBpm?.takeIf { hr.restingBpm != null }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Average $it bpm", style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (hr?.minBpm != null && hr.maxBpm != null) "${hr.minBpm}–${hr.maxBpm}" else "—",
                    style = Resurge.type.title,
                    color = Resurge.colors.onInverse,
                )
                Spacer(Modifier.height(4.dp))
                Text("range today", style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
            }
        }
    }
}

@Composable
private fun SleepTile(day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val total = day.sleepDuration
    val stages = day.sleep.flatMap { it.stages.entries }.groupBy({ it.key }, { it.value })
        .mapValues { (_, d) -> d.fold(Duration.ZERO, Duration::plus) }
    val goal = DashboardGoals.SLEEP.toMinutes().toFloat()
    val segments = when {
        total == null -> emptyList()
        stages.isEmpty() -> listOf(RingSegment((total.toMinutes() / goal).coerceAtMost(1f), Resurge.colors.sleep))
        else -> listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.REM, SleepStage.AWAKE)
            .mapNotNull { st -> stages[st]?.let { RingSegment(it.toMinutes() / goal, sleepStageColor(st)) } }
            .let(::capToOne)
    }
    MetricCell(
        title = "Sleep", value = total?.let(Formatters::duration), onClick = onClick, modifier = modifier,
        subtitle = stages[SleepStage.DEEP]?.let { "Deep ${Formatters.duration(it)}" }
            ?: total?.let { "of ${DashboardGoals.SLEEP.toHours()}h goal" },
    ) { m ->
        Ring(segments, m, strokeWidth = 8.dp) { TileIcon(ResurgeIcons.Bed, Resurge.colors.sleep) }
    }
}

@Composable
private fun StepsTile(day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val steps = day.steps
    val progress = ((steps ?: 0L) / DashboardGoals.STEPS).toFloat()
    MetricCell(
        title = "Steps", value = steps?.let(Formatters::count), onClick = onClick, modifier = modifier,
        subtitle = when {
            steps == null -> null
            progress >= 1f -> "Goal reached"
            else -> "${(progress * 100).roundToInt()}% of 10k"
        },
    ) { m ->
        Ring(listOf(RingSegment(progress.coerceAtMost(1f), Resurge.colors.activity)), m, strokeWidth = 8.dp) {
            TileIcon(ResurgeIcons.Footprints, Resurge.colors.activity)
        }
    }
}

@Composable
private fun CaloriesTile(day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val energy = Resurge.colors.energy
    val total = day.caloriesTotal
    val active = day.caloriesActive
    val segments = when {
        total == null || total <= 0 -> emptyList()
        active == null -> listOf(RingSegment(1f, energy))
        else -> (active / total).toFloat().coerceIn(0f, 1f).let { a ->
            listOf(RingSegment(a, energy), RingSegment(1f - a, energy.copy(alpha = 0.3f)))
        }
    }
    MetricCell(
        title = "Calories", value = total?.let(Formatters::kcal), unit = "kcal", onClick = onClick, modifier = modifier,
        subtitle = active?.let { "${Formatters.kcal(it)} active" } ?: total?.let { "Total burned" },
    ) { m ->
        Ring(segments, m, strokeWidth = 8.dp) { TileIcon(ResurgeIcons.Flame, energy) }
    }
}

@Composable
private fun DistanceTile(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val water = Resurge.colors.water
    val week = state.sparklines[HealthMetric.DISTANCE].orEmpty()
    val avg = week.mapNotNull { it }.takeIf { it.isNotEmpty() }?.average()
    MetricCell(
        title = "Distance", value = day.distanceMeters?.let(Formatters::kilometres), unit = "km", onClick = onClick, modifier = modifier,
        subtitle = avg?.let { String.format(Locale.getDefault(), "7-day avg %.1f km", it) },
    ) { m ->
        RadialBars(week.ifEmpty { List(7) { null } }, water, m, innerFraction = 0.5f, gapDegrees = 8f) {
            TileIcon(ResurgeIcons.Distance, water, size = 18)
        }
    }
}

@Composable
private fun WeightTile(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val kg = day.weightKg
    val week = state.sparklines[HealthMetric.WEIGHT].orEmpty().mapNotNull { it }
    val lo = (week + listOfNotNull(kg)).minOrNull()?.minus(1.0)
    val hi = (week + listOfNotNull(kg)).maxOrNull()?.plus(1.0)
    val position = if (kg != null && lo != null && hi != null && hi > lo) ((kg - lo) / (hi - lo)).toFloat() else null
    val change = if (kg != null && week.size >= 2) kg - week.first() else null
    MetricCell(
        title = "Weight", value = kg?.let { String.format(Locale.getDefault(), "%.1f", it) }, unit = "kg", onClick = onClick, modifier = modifier,
        subtitle = when {
            kg == null -> null
            change != null && abs(change) >= 0.05 -> String.format(Locale.getDefault(), "%+.1f kg this week", change)
            else -> "Latest"
        },
    ) { m ->
        RangeDial(
            low = if (position != null) 0f else null, high = position, marker = position,
            color = Resurge.colors.text, strokeWidth = 8.dp, modifier = m,
        ) { TileIcon(ResurgeIcons.Scale, Resurge.colors.textMuted) }
    }
}

@Composable
private fun ExerciseTile(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val activity = Resurge.colors.activity
    val track = Resurge.colors.surfaceMuted
    MetricCell(
        title = "Exercise", value = "${state.exerciseWeek.count { it }}/7", unit = "days", onClick = onClick, modifier = modifier,
        subtitle = if (day.exerciseDuration > Duration.ZERO) "${Formatters.duration(day.exerciseDuration)} today" else "This week",
    ) { m ->
        Ring(
            state.exerciseWeek.map { RingSegment(1f / 7, if (it) activity else track) },
            m, strokeWidth = 8.dp, gapDegrees = 10f, showTrack = false,
        ) { TileIcon(ResurgeIcons.Dumbbell, activity) }
    }
}

@Composable
private fun TileIcon(icon: Int, tint: Color, size: Int = 22) =
    Icon(icon, contentDescription = null, tint = tint, size = size.dp)

/** Trims segments so they never exceed one full ring (sleeping past the goal). */
private fun capToOne(segments: List<RingSegment>): List<RingSegment> {
    var remaining = 1f
    return segments.map { s ->
        val f = s.fraction.coerceAtMost(remaining)
        remaining -= f
        s.copy(fraction = f)
    }.filter { it.fraction > 0f }
}


// --- Loading ---

/** Shimmering placeholders shaped like the Today cards, shown until the first Health Connect read lands. */
@Composable
internal fun TodaySkeleton() {
    SkeletonGroup {
        Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
            Card(contentPadding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonCircle(80.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonLine(80.dp)
                        SkeletonLine(110.dp, 22.dp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonLine(70.dp, 22.dp)
                        SkeletonLine(60.dp)
                    }
                }
            }
            repeat(3) {
                MetricGroup(cells = List(2) { { m: Modifier -> SkeletonCell(m) } })
            }
        }
    }
}

@Composable
private fun SkeletonCell(modifier: Modifier) {
    Column(modifier.padding(20.dp)) {
        SkeletonCircle(68.dp)
        Spacer(Modifier.height(18.dp))
        SkeletonLine(56.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(96.dp, 22.dp)
        Spacer(Modifier.height(10.dp))
        SkeletonLine(80.dp)
    }
}

/** Vertical gap between Today cards. */
internal val CARD_GAP = 16.dp
