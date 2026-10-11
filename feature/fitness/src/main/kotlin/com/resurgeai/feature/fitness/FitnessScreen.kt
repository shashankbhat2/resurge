package com.resurgeai.feature.fitness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MetricChip
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SwipeToDelete
import com.resurgeai.core.designsystem.components.UndoBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FitnessRoute(
    onOpenWorkout: (String) -> Unit,
    onLogWorkout: () -> Unit,
    viewModel: FitnessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    FitnessScreen(
        state = state,
        onOpenWorkout = onOpenWorkout,
        onLogWorkout = onLogWorkout,
        onDelete = viewModel::delete,
        onUndo = viewModel::undoDelete,
        onUndoTimeout = viewModel::clearDeleted,
    )
}

/** Fitness tab: this week's totals, then workouts by day. Cards open the editor; swipe left deletes. */
@Composable
fun FitnessScreen(
    state: FitnessUiState,
    onOpenWorkout: (String) -> Unit,
    onLogWorkout: () -> Unit,
    onDelete: (Workout) -> Unit,
    onUndo: () -> Unit,
    onUndoTimeout: () -> Unit,
) {
    val format = UnitFormat(state.units)
    val top = LocalHeaderInset.current + 12.dp
    val bottom = LocalFloatingBarInset.current + 24.dp
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> SkeletonGroup {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = top), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(96.dp), Resurge.shape.lg)
                    repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(110.dp), Resurge.shape.lg) }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week") { WeekCard(state.week) }
                if (state.days.isEmpty()) {
                    item(key = "empty") { EmptyFeed(onLogWorkout) }
                }
                state.days.forEach { (date, workouts) ->
                    item(key = "h$date") { SectionHeader(dayLabel(date, state.today), Modifier.padding(top = 0.dp)) }
                    items(workouts, key = { it.id }) { w ->
                        SwipeToDelete(onDelete = { onDelete(w) }) {
                            WorkoutCard(w, format, onClick = { onOpenWorkout(w.id) })
                        }
                    }
                }
            }
        }
        UndoBar(
            message = state.deleted?.let { "${it.displayName} deleted" },
            onUndo = onUndo,
            onTimeout = onUndoTimeout,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalFloatingBarInset.current + 8.dp),
        )
    }
}

@Composable
private fun WeekCard(week: WeekSummary) {
    Card(Modifier.fillMaxWidth()) {
        Text("This week", style = Resurge.type.caption, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            WeekStat("${week.workouts}", if (week.workouts == 1) "workout" else "workouts", Modifier.weight(1f))
            WeekStat("${week.activeMinutes}", "active min", Modifier.weight(1f))
            WeekStat(if (week.kcal > 0) "%,d".format(week.kcal) else "—", "kcal", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekStat(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = Resurge.type.display.copy(fontFeatureSettings = "tnum"))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun EmptyFeed(onLogWorkout: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(ResurgeIcons.Dumbbell, tint = Resurge.colors.activity, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text("No workouts yet", style = Resurge.type.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Workouts from your watch appear here automatically. Log anything else by hand.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button("Log a workout", onClick = onLogWorkout, leadingIcon = ResurgeIcons.Plus)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutCard(w: Workout, format: UnitFormat, onClick: () -> Unit) {
    val activity = w.activity
    val tint = ActivityVisuals.color(activity.category)
    val zone = ZoneId.systemDefault()
    Card(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ActivityVisuals.icon(activity.icon), tint = tint, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.displayName, style = Resurge.type.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val time = w.start.atZone(zone).toLocalTime().format(clockFormatter())
                val origin = when {
                    w.source == WorkoutSource.MANUAL -> "Logged"
                    w.edited -> "${w.sourceName ?: "Health Connect"} · Edited"
                    else -> w.sourceName ?: "Health Connect"
                }
                Text("$time · $origin", style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (w.source == WorkoutSource.HEALTH_CONNECT) {
                Icon(ResurgeIcons.Watch, contentDescription = "From your device", tint = Resurge.colors.textSubtle, size = 18.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            summaryChips(w, format).forEach { (icon, text) -> MetricChip(text, icon = icon) }
        }
        if (activity.category == ActivityCategory.STRENGTH && w.metrics.exercises.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Button("Add sets", onClick = onClick, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Plus)
        }
    }
}

/** The 2–4 numbers worth showing on a card, by category. */
internal fun summaryChips(w: Workout, f: UnitFormat): List<Pair<Int, String>> = buildList {
    val m = w.metrics
    val duration = UnitFormat.duration(w.duration)
    when (w.activity.category) {
        ActivityCategory.DISTANCE -> {
            m.distanceM?.let { add(ResurgeIcons.Distance to f.distance(it)) }
            add(ResurgeIcons.Stopwatch to duration)
            if (w.activity.showSpeed) FitnessMath.speedKmh(m.distanceM, w.duration)?.let { add(ResurgeIcons.Activity to f.speed(it)) }
            else FitnessMath.paceSecPerKm(m.distanceM, w.duration)?.let { add(ResurgeIcons.Activity to f.pace(it)) }
            m.elevationM?.let { add(ResurgeIcons.Mountain to f.elevation(it)) }
        }
        ActivityCategory.STRENGTH -> {
            if (m.exercises.isNotEmpty()) {
                val sets = m.exercises.sumOf { it.sets.size }
                add(ResurgeIcons.Dumbbell to "${m.exercises.size} ex · $sets sets")
                val volume = m.exercises.sumOf { it.volumeKg }
                if (volume > 0) add(ResurgeIcons.Scale to f.volume(volume))
            }
            add(ResurgeIcons.Stopwatch to duration)
        }
        ActivityCategory.SWIM -> {
            FitnessMath.swimDistanceM(m)?.let { add(ResurgeIcons.Distance to f.distance(it)) }
            m.laps?.let { add(ResurgeIcons.Repeat to "$it laps") }
            add(ResurgeIcons.Stopwatch to duration)
        }
        ActivityCategory.TIMED -> {
            add(ResurgeIcons.Stopwatch to duration)
            m.rpe?.let { add(ResurgeIcons.Activity to "Effort $it/10") }
        }
        ActivityCategory.COUNT -> {
            m.reps?.let { add(ResurgeIcons.Repeat to "%,d reps".format(it)) }
            add(ResurgeIcons.Stopwatch to duration)
        }
    }
    m.kcal?.let { add(ResurgeIcons.Flame to "$it kcal") }
    m.avgHr?.let { add(ResurgeIcons.Heart to "$it bpm") }
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
}
