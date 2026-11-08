package com.resurgeai.feature.fitness

import com.resurgeai.core.designsystem.components.OverviewFooterHeight
import com.resurgeai.core.designsystem.components.OverviewBodyHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.StatDivider
import com.resurgeai.core.designsystem.components.StatCell
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
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
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
        onRetryCardio = viewModel::retryCardio,
    )
}

/**
 * Fitness tab: the move week (active minutes toward 150) on top, then workouts grouped by day.
 * Cards open the editor; swipe left deletes with undo.
 */
@Composable
fun FitnessScreen(
    state: FitnessUiState,
    onOpenWorkout: (String) -> Unit,
    onLogWorkout: () -> Unit,
    onDelete: (Workout) -> Unit,
    onUndo: () -> Unit,
    onUndoTimeout: () -> Unit,
    onRetryCardio: () -> Unit = {},
) {
    val format = UnitFormat(state.units)
    val top = LocalHeaderInset.current + 12.dp
    val bottom = LocalFloatingBarInset.current + 24.dp
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> SkeletonGroup {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = top), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(260.dp), Resurge.shape.lg)
                    CardioPointsCard(state.cardio, state.today, onRetryCardio)
                    SkeletonBox(Modifier.fillMaxWidth().height(150.dp), Resurge.shape.md)
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "week") { MoveWeek(state.week, state.today) }
                item(key = "cardio") { CardioPointsCard(state.cardio, state.today, onRetryCardio) }
                if (state.days.isEmpty()) {
                    item(key = "empty") { EmptyFeed(onLogWorkout) }
                }
                state.days.forEach { (date, workouts) ->
                    item(key = "h$date") {
                        val minutes = workouts.fold(Duration.ZERO) { acc, w -> acc + w.duration }
                        SectionLabel(
                            dayLabel(date, state.today),
                            Modifier.padding(top = 0.dp),
                            trailing = "${workouts.size} ${if (workouts.size == 1) "workout" else "workouts"} · ${totalTime(minutes)}",
                        )
                    }
                    items(workouts, key = { it.id }) { w ->
                        SwipeToDelete(onDelete = { onDelete(w) }) {
                            WorkoutCard(w, format, best = state.bests[w.id], onClick = { onOpenWorkout(w.id) })
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

// --- Move week ---

/** Daily columns top out at an hour, so one long session doesn't flatten the rest of the week. */
private const val COLUMN_MAX_MIN = 60f

/**
 * The tab's hero: this week's active minutes rising toward [WeekSummary.MOVE_WEEK_TARGET], a
 * column per day (Sunday to Saturday) with a dashed daily-pace line, and a two-cell footer.
 */
@Composable
internal fun MoveWeek(week: WeekSummary, today: LocalDate) {
    val target = WeekSummary.MOVE_WEEK_TARGET
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface),
    ) {
        Column(Modifier.fillMaxWidth().height(OverviewBodyHeight).padding(20.dp)) {
            Overline("Move week")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    week.activeMinutes.toString(),
                    style = Resurge.type.tide.copy(fontSize = 44.sp, lineHeight = 46.sp),
                    color = if (week.reached) Resurge.colors.glowInk else Resurge.colors.text,
                )
                Spacer(Modifier.width(6.dp))
                Text("/ $target active min", style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            DayColumns(week, today)
        }
        // Same footer as the tide card: two cells end to end.
        Divider()
        Row(Modifier.fillMaxWidth().height(OverviewFooterHeight)) {
            StatCell(
                icon = ResurgeIcons.Dumbbell,
                iconTint = Resurge.colors.activity,
                iconBackground = Resurge.colors.surfaceMuted,
                value = week.workouts.toString(),
                label = if (week.workouts == 1) "Workout this week" else "Workouts this week",
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            StatCell(
                icon = ResurgeIcons.Flame,
                iconTint = Resurge.colors.energy,
                iconBackground = Resurge.colors.surfaceMuted,
                value = if (week.kcal > 0) "%,d".format(week.kcal) else "—",
                label = "kcal from workouts",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayColumns(week: WeekSummary, today: LocalDate) {
    val weekStart = today.minusDays(week.todayIndex.toLong())
    val pace = WeekSummary.MOVE_WEEK_TARGET / 7f / COLUMN_MAX_MIN
    val move = Resurge.colors.activity
    val line = Resurge.colors.textSubtle
    val description = week.dailyMinutes.withIndex().filter { it.index <= week.todayIndex }.joinToString { (i, m) ->
        "${weekStart.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $m min"
    }
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "Active minutes: $description" }) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                week.dailyMinutes.forEachIndexed { i, minutes ->
                    val future = i > week.todayIndex
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .background(if (future) Color.Transparent else Resurge.colors.surfaceMuted)
                            .then(
                                when {
                                    i == week.todayIndex -> Modifier.border(1.5.dp, Resurge.colors.textSubtle, shape)
                                    future -> Modifier.border(1.dp, Resurge.colors.border, shape)
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val f = (minutes / COLUMN_MAX_MIN).coerceIn(0f, 1f)
                        if (f > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(f).background(move))
                    }
                }
            }
            // Daily pace (target ÷ 7): which days carried the week.
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height * (1 - pace)
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until 7).forEach { i ->
                Text(
                    weekStart.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption,
                    color = if (i == week.todayIndex) Resurge.colors.text else Resurge.colors.textSubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// --- Workouts ---

/** One stat on a workout card: the value, an optional small unit, and what it is. */
internal data class WorkoutStat(val value: String, val unit: String?, val label: String)

/**
 * A workout in the feed: icon tile, name and source, three stats chosen by activity type, then
 * tags (personal best, heart rate, effort). A strength session with no sets offers "Add sets".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutCard(w: Workout, format: UnitFormat, best: PersonalBest?, onClick: () -> Unit) {
    val activity = w.activity
    val stats = workoutStats(w, format)
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted),
                    contentAlignment = Alignment.Center,
                ) { Icon(ActivityVisuals.icon(activity.icon), contentDescription = null, tint = Resurge.colors.activity, size = 24.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.displayName, style = Resurge.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val time = w.start.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter())
                        val origin = when {
                            w.source == WorkoutSource.MANUAL -> "Logged"
                            w.edited -> "${w.sourceName ?: "Health Connect"} · Edited"
                            else -> w.sourceName ?: "Health Connect"
                        }
                        Text("$time · $origin", style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (w.source == WorkoutSource.HEALTH_CONNECT) {
                            Spacer(Modifier.width(4.dp))
                            Icon(ResurgeIcons.Watch, contentDescription = "From your device", tint = Resurge.colors.textSubtle, size = 14.dp)
                        }
                    }
                }
                Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
            }
            Row(Modifier.fillMaxWidth()) {
                stats.forEach { StatColumn(it, Modifier.weight(1f)) }
                repeat(3 - stats.size) { Spacer(Modifier.weight(1f)) }
            }
            val shown = stats.map { it.label }
            val heart = w.metrics.avgHr?.takeIf { "Avg heart rate" !in shown }
            val effort = w.metrics.rpe?.takeIf { "Effort" !in shown }
            val alsoFrom = w.duplicates.mapNotNull { it.sourceName }.distinct().takeIf { it.isNotEmpty() }
            if (best != null || heart != null || effort != null || alsoFrom != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    best?.let { BestPill(it, format) }
                    // Shown once though two devices logged it; the editor lets you pick the other.
                    alsoFrom?.let { Tag("Also recorded by ${it.joinToString()}", icon = ResurgeIcons.Devices) }
                    heart?.let { Tag("$it bpm", icon = ResurgeIcons.Heart, tint = Resurge.colors.heart) }
                    effort?.let { Tag("Effort $it · ${effortLabel(it)}") }
                }
            }
            if (activity.category == ActivityCategory.STRENGTH && w.metrics.exercises.isEmpty()) {
                Button("Add sets", onClick = onClick, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Plus)
            }
        }
    }
}

@Composable
private fun StatColumn(stat: WorkoutStat, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stat.value, style = Resurge.type.metric.copy(fontSize = 22.sp, lineHeight = 26.sp), maxLines = 1)
            stat.unit?.let {
                Spacer(Modifier.width(2.dp))
                Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp), maxLines = 1)
            }
        }
        Text(stat.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
    }
}

/** An earned moment: the only amber on Fitness. */
@Composable
private fun BestPill(best: PersonalBest, format: UnitFormat) {
    val text = when (best) {
        is PersonalBest.FastestPace -> "Best pace this month · ${format.pace(best.secPerKm)}"
        is PersonalBest.MoreVolume -> "+${format.volume(best.deltaKg)} vs last time"
    }
    EarnedPill(text)
}

/** A medal pill on `glow-tint`: the only amber in Fitness, for beating your own numbers. */
@Composable
internal fun EarnedPill(text: String) {
    Row(
        Modifier.clip(Resurge.shape.pill).background(Resurge.colors.glowTint).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).clip(Resurge.shape.pill).background(Resurge.colors.glow), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Medal, contentDescription = null, tint = Resurge.colors.onGlow, size = 14.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = Resurge.type.caption.copy(fontSize = 13.sp), color = Resurge.colors.glowInk, maxLines = 1)
    }
}

@Composable
private fun Tag(text: String, icon: Int? = null, tint: Color = Resurge.colors.textMuted) {
    Row(
        Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = tint, size = 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Resurge.type.caption.copy(fontSize = 13.sp), color = Resurge.colors.textMuted, maxLines = 1)
    }
}

/** The three numbers worth showing for this kind of workout, with fallbacks when some are missing. */
internal fun workoutStats(w: Workout, f: UnitFormat): List<WorkoutStat> {
    val m = w.metrics
    val time = durationStat(w.duration)
    val kcal = m.kcal?.let { WorkoutStat("%,d".format(it), "kcal", if (w.source == WorkoutSource.MANUAL) "Calories (est.)" else "Calories") }
    val heart = m.avgHr?.let { WorkoutStat(it.toString(), "bpm", "Avg heart rate") }
    val effort = m.rpe?.let { WorkoutStat(it.toString(), "/10", "Effort") }
    val primary: List<WorkoutStat?> = when (w.activity.category) {
        ActivityCategory.DISTANCE -> listOf(
            time,
            m.distanceM?.let { split(f.distance(it), "Distance") },
            if (w.activity.showSpeed) FitnessMath.speedKmh(m.distanceM, w.duration)?.let { split(f.speed(it), "Avg speed") }
            else FitnessMath.paceSecPerKm(m.distanceM, w.duration)?.let { split(f.pace(it), "Avg pace") },
        )
        ActivityCategory.STRENGTH -> if (m.exercises.isEmpty()) listOf(time) else listOf(
            time,
            m.exercises.sumOf { it.volumeKg }.takeIf { it > 0 }?.let { split(f.volume(it), "Volume") },
            WorkoutStat(m.exercises.sumOf { it.sets.size }.toString(), null, "Sets"),
        )
        ActivityCategory.SWIM -> listOf(
            time,
            FitnessMath.swimDistanceM(m)?.let { split(f.distance(it), "Distance") },
            m.laps?.let { WorkoutStat(it.toString(), null, "Laps") },
        )
        ActivityCategory.COUNT -> listOf(time, m.reps?.let { WorkoutStat("%,d".format(it), null, "Reps") })
        ActivityCategory.TIMED -> listOf(time)
    }
    return (primary + listOf(kcal, heart, effort)).filterNotNull().distinctBy { it.label }.take(3)
}

/** "7.4 km" → 7.4 + km; "5:43 /km" → 5:43 + /km. */
private fun split(text: String, label: String): WorkoutStat {
    val i = text.lastIndexOf(' ')
    return if (i > 0) WorkoutStat(text.substring(0, i), text.substring(i + 1), label) else WorkoutStat(text, null, label)
}

private fun durationStat(d: Duration): WorkoutStat {
    val minutes = d.toMinutes()
    return if (minutes < 60) WorkoutStat(minutes.toString(), "min", "Duration")
    else WorkoutStat(hoursMinutes(minutes), null, "Duration")
}

private fun totalTime(d: Duration): String {
    val minutes = d.toMinutes()
    return if (minutes < 60) "$minutes min" else hoursMinutes(minutes)
}

/** "1h", "1h 26m". */
private fun hoursMinutes(minutes: Long): String = if (minutes % 60 == 0L) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"

@Composable
private fun EmptyFeed(onLogWorkout: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("No workouts yet", style = Resurge.type.headline)
        Text(
            "Workouts from your watch appear here automatically. Log anything else by hand.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
        )
        Button("Log a workout", onClick = onLogWorkout, variant = ButtonVariant.Accent, leadingIcon = ResurgeIcons.Plus)
    }
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}
