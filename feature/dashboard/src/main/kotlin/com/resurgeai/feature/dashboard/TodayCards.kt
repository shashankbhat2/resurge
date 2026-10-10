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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.RadialBars
import com.resurgeai.core.designsystem.components.RadialCard
import com.resurgeai.core.designsystem.components.RangeDial
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingLabel
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepStage
import java.time.Duration
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/** Default daily goals until they're user-configurable. */
internal object DashboardGoals {
    const val STEPS = 10_000.0
    val SLEEP: Duration = Duration.ofHours(8)

    /** Heart-rate dial scale (bpm). */
    const val HR_MIN = 40f
    const val HR_MAX = 200f
}

@Composable
internal fun TodayHeader(state: DashboardUiState) {
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = Resurge.type.body, color = Resurge.colors.textMuted)
            Text(state.profile?.firstName ?: "Today", style = Resurge.type.display)
        }
        if (state.loading) Spinner(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/** Heart rate hero on top, then Sleep|Steps, Calories|Distance, Exercise|Weight. */
@Composable
internal fun TodayGrid(state: DashboardUiState, day: DailyHealth, onOpen: (HealthMetric) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeartRateHero(day) { onOpen(HealthMetric.HEART_RATE) }
        CardRow({ SleepCard(state, day, it) { onOpen(HealthMetric.SLEEP) } }, { StepsCard(day, it) { onOpen(HealthMetric.STEPS) } })
        CardRow({ CaloriesCard(day, it) { onOpen(HealthMetric.CALORIES_BURNED) } }, { DistanceCard(state, day, it) { onOpen(HealthMetric.DISTANCE) } })
        CardRow({ ExerciseCard(state, day, it) { onOpen(HealthMetric.EXERCISE) } }, { WeightCard(state, day, it) { onOpen(HealthMetric.WEIGHT) } })
    }
}

@Composable
private fun CardRow(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        left(Modifier.weight(1f).fillMaxHeight())
        right(Modifier.weight(1f).fillMaxHeight())
    }
}

// --- Hero ---

@Composable
private fun HeartRateHero(day: DailyHealth, onClick: () -> Unit) {
    val hr = day.heartRate
    val heart = Resurge.colors.heart
    val headline = hr?.restingBpm ?: hr?.avgBpm
    fun scale(bpm: Long) = (bpm - DashboardGoals.HR_MIN) / (DashboardGoals.HR_MAX - DashboardGoals.HR_MIN)

    Card(inverse = true, onClick = onClick, contentPadding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(ResurgeIcons.Heartbeat, tint = heart, size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Heart rate", style = Resurge.type.label, color = Resurge.colors.onInverseMuted)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(headline?.toString() ?: "—", style = Resurge.type.display, color = Resurge.colors.onInverse)
                    Spacer(Modifier.width(6.dp))
                    Text("bpm", style = Resurge.type.label, color = Resurge.colors.onInverseMuted)
                }
                Text(
                    when {
                        hr == null -> "No data today"
                        hr.restingBpm != null -> "Resting${hr.avgBpm?.let { " · avg $it" }.orEmpty()}"
                        else -> "Average today"
                    },
                    style = Resurge.type.caption,
                    color = Resurge.colors.onInverseMuted,
                )
            }
            RangeDial(
                low = hr?.minBpm?.let(::scale),
                high = hr?.maxBpm?.let(::scale),
                marker = headline?.let(::scale),
                color = heart,
                trackColor = Resurge.colors.onInverse.copy(alpha = 0.08f),
                markerColor = Resurge.colors.onInverse,
                markerRing = Resurge.colors.inverse,
                strokeWidth = 11.dp,
                modifier = Modifier.width(128.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hr?.minBpm != null && hr.maxBpm != null) "${hr.minBpm}–${hr.maxBpm}" else "—",
                        style = Resurge.type.headline,
                        color = Resurge.colors.onInverse,
                    )
                    Text("range", style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
                }
            }
        }
    }
}

// --- Cards ---

@Composable
private fun SleepCard(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val sleep = Resurge.colors.sleep
    val total = day.sleepDuration
    val stages = day.sleep.flatMap { it.stages.entries }.groupBy({ it.key }, { it.value })
        .mapValues { (_, d) -> d.fold(Duration.ZERO, Duration::plus) }
    val goal = DashboardGoals.SLEEP.toMinutes().toFloat()
    val segments = if (total == null) emptyList() else if (stages.isEmpty()) {
        listOf(RingSegment((total.toMinutes() / goal).coerceAtMost(1f), sleep))
    } else {
        // Stage arcs in order, scaled so the full ring = sleep goal.
        listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.REM, SleepStage.AWAKE)
            .mapNotNull { st -> stages[st]?.let { RingSegment(it.toMinutes() / goal, sleepStageColor(st)) } }
            .let { capToOne(it) }
    }
    RadialCard(
        title = "Sleep", icon = ResurgeIcons.Bed, tint = sleep, modifier = modifier, onClick = onClick,
        subtitle = stages[SleepStage.DEEP]?.let { d -> "Deep ${Formatters.duration(d)}" + (stages[SleepStage.REM]?.let { " · REM ${Formatters.duration(it)}" } ?: "") }
            ?: if (total != null) "Last night" else "No sleep recorded",
    ) { m ->
        Ring(segments, m, strokeWidth = 12.dp) {
            RingLabel(total?.let(Formatters::duration), "of ${DashboardGoals.SLEEP.toHours()}h")
        }
    }
}

@Composable
private fun StepsCard(day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val steps = day.steps
    val progress = ((steps ?: 0L) / DashboardGoals.STEPS).toFloat()
    RadialCard(
        title = "Steps", icon = ResurgeIcons.Footprints, tint = Resurge.colors.activity, modifier = modifier, onClick = onClick,
        subtitle = when {
            steps == null -> "No steps recorded"
            progress >= 1f -> "Goal reached"
            else -> "${(progress * 100).roundToInt()}% of goal"
        },
    ) { m ->
        Ring(listOf(RingSegment(progress.coerceAtMost(1f), Resurge.colors.activity)), m) {
            RingLabel(steps?.let(Formatters::count), "of 10k")
        }
    }
}

@Composable
private fun CaloriesCard(day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val energy = Resurge.colors.energy
    val total = day.caloriesTotal
    val active = day.caloriesActive
    val segments = if (total == null || total <= 0) emptyList()
    else if (active == null) listOf(RingSegment(1f, energy))
    else {
        val a = ((active ?: 0.0) / total).toFloat().coerceIn(0f, 1f)
        listOf(RingSegment(a, energy), RingSegment(1f - a, energy.copy(alpha = 0.3f)))
    }
    RadialCard(
        title = "Calories", icon = ResurgeIcons.Flame, tint = energy, modifier = modifier, onClick = onClick,
        subtitle = active?.let { "${Formatters.kcal(it)} active" } ?: if (total == null) "No data" else "Total burned",
    ) { m ->
        Ring(segments, m) { RingLabel(total?.let(Formatters::kcal), "kcal") }
    }
}

@Composable
private fun DistanceCard(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val water = Resurge.colors.water
    val week = state.sparklines[HealthMetric.DISTANCE].orEmpty()
    val avg = week.mapNotNull { it }.takeIf { it.isNotEmpty() }?.average()
    RadialCard(
        title = "Distance", icon = ResurgeIcons.Distance, tint = water, modifier = modifier, onClick = onClick,
        subtitle = avg?.let { String.format(Locale.getDefault(), "7-day avg %.1f km", it) } ?: "Last 7 days",
    ) { m ->
        RadialBars(week.ifEmpty { List(7) { null } }, water, m) {
            RingLabel(day.distanceMeters?.let(Formatters::kilometres), "km")
        }
    }
}

@Composable
private fun ExerciseCard(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val activity = Resurge.colors.activity
    val track = Resurge.colors.surfaceMuted
    // One segment per weekday; active days filled.
    val segments = state.exerciseWeek.map { RingSegment(1f / 7, if (it) activity else track) }
    RadialCard(
        title = "Exercise", icon = ResurgeIcons.Dumbbell, tint = activity, modifier = modifier, onClick = onClick,
        subtitle = if (day.exerciseDuration > Duration.ZERO) "${Formatters.duration(day.exerciseDuration)} today" else "Active days this week",
    ) { m ->
        Ring(segments, m, gapDegrees = 8f, showTrack = false) {
            RingLabel("${state.exerciseWeek.count { it }}", "of 7 days")
        }
    }
}

@Composable
private fun WeightCard(state: DashboardUiState, day: DailyHealth, modifier: Modifier, onClick: () -> Unit) {
    val tint = Resurge.colors.textMuted
    val kg = day.weightKg
    val week = state.sparklines[HealthMetric.WEIGHT].orEmpty().mapNotNull { it }
    val lo = (week + listOfNotNull(kg)).minOrNull()?.minus(1.0)
    val hi = (week + listOfNotNull(kg)).maxOrNull()?.plus(1.0)
    val position = if (kg != null && lo != null && hi != null && hi > lo) ((kg - lo) / (hi - lo)).toFloat() else null
    val change = if (kg != null && week.size >= 2) kg - week.first() else null
    RadialCard(
        title = "Weight", icon = ResurgeIcons.Scale, tint = tint, modifier = modifier, onClick = onClick,
        subtitle = when {
            kg == null -> "No weight logged"
            change != null && kotlin.math.abs(change) >= 0.05 -> String.format(Locale.getDefault(), "%+.1f kg this week", change)
            else -> "Latest"
        },
    ) { m ->
        RangeDial(
            low = if (position != null) 0f else null,
            high = position,
            marker = position,
            color = Resurge.colors.text,
            markerColor = Resurge.colors.text,
            modifier = m,
        ) {
            RingLabel(kg?.let { String.format(Locale.getDefault(), "%.1f", it) }, "kg")
        }
    }
}

// --- helpers ---

/** Trims segments so they never exceed one full ring (sleeping past the goal). */
private fun capToOne(segments: List<RingSegment>): List<RingSegment> {
    var remaining = 1f
    return segments.map { s ->
        val f = s.fraction.coerceAtMost(remaining)
        remaining -= f
        s.copy(fraction = f)
    }.filter { it.fraction > 0f }
}
