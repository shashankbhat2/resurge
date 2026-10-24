package com.resurgeai.feature.fitness

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.resurgeai.core.model.Backfill
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.BottomSheet
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.OptionChips
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.Activity
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.SessionKind
import java.time.format.TextStyle
import java.util.Locale

/**
 * This week of the plan, session by session: done (with what did it), today's still to do (Start,
 * or Done as planned in one tap), missed, or coming up. Runs say they log themselves; rest days
 * aren't listed.
 */
@Composable
internal fun PlanWeekSection(
    state: FitnessUiState,
    format: UnitFormat,
    onStart: (PlanDay) -> Unit,
    onDoneAsPlanned: (PlanDay, java.time.LocalDate) -> Unit,
    onOpenWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit,
) {
    val plan = state.plan ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The plan's name opens the whole plan.
        UnstyledButton(
            onClick = onOpenPlan,
            modifier = Modifier.fillMaxWidth().clip(Resurge.shape.sm).semantics { contentDescription = "This week: ${state.sessionsDone} of ${state.sessionsPlanned} sessions. Open ${plan.name}" },
            indication = LocalIndication.current,
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Overline(plan.name, color = Resurge.colors.textSubtle)
                        Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 14.dp)
                    }
                    Text("This week", style = Resurge.type.title)
                }
                Text("${state.sessionsDone}", style = Resurge.type.headline)
                Text(" / ${state.sessionsPlanned} sessions", style = Resurge.type.label, color = Resurge.colors.textSubtle)
            }
        }
        state.planWeek.filterNot { it.day.rest }.forEach { p ->
            val dayName = p.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            when {
                p.workout != null -> PlanRow(
                    title = p.day.title,
                    meta = workoutMeta(p.workout, format),
                    day = dayName,
                    mark = Mark.DONE,
                    onClick = { onOpenWorkout(p.workout.id) },
                )
                p.date == state.today -> TodaySession(p.day, onStart = { onStart(p.day) }, onDoneAsPlanned = { onDoneAsPlanned(p.day, p.date) })
                // Missed, but still in the backfill week: it can be marked done after the fact.
                p.date.isBefore(state.today) -> PlanRow(
                    p.day.title, "Missed", dayName, Mark.MISSED, onClick = null,
                    action = if (Backfill.allows(p.date, state.today)) ("Mark done" to { onDoneAsPlanned(p.day, p.date) }) else null,
                )
                else -> PlanRow(p.day.title, sessionMeta(p.day), dayName, Mark.UPCOMING, onClick = null)
            }
        }
    }
}

private enum class Mark { DONE, MISSED, UPCOMING }

@Composable
private fun PlanRow(title: String, meta: String, day: String, mark: Mark, onClick: (() -> Unit)?, action: Pair<String, () -> Unit>? = null) {
    val shape = Resurge.shape.md
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .then(if (mark == Mark.UPCOMING) Modifier.border(1.dp, Resurge.colors.border, shape) else Modifier.background(Resurge.colors.surface))
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            MarkDot(mark)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Resurge.type.headline, color = if (mark == Mark.UPCOMING) Resurge.colors.textMuted else Resurge.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    meta,
                    style = Resurge.type.caption,
                    color = if (mark == Mark.MISSED) Resurge.colors.down else Resurge.colors.textSubtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (action != null) {
                UnstyledButton(
                    onClick = action.second,
                    modifier = Modifier.height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    indication = LocalIndication.current,
                ) { Text(action.first, style = Resurge.type.label) }
            } else {
                Text(day, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
    }
    if (onClick != null) {
        UnstyledButton(onClick = onClick, modifier = base, indication = LocalIndication.current, contentAlignment = Alignment.CenterStart) { content() }
    } else {
        Box(base) { content() }
    }
}

@Composable
private fun MarkDot(mark: Mark) {
    val shape = Resurge.shape.pill
    Box(
        Modifier
            .size(26.dp)
            .clip(shape)
            .then(
                when (mark) {
                    Mark.DONE -> Modifier.background(Resurge.colors.up)
                    Mark.MISSED -> Modifier.border(2.dp, Resurge.colors.down, shape)
                    Mark.UPCOMING -> Modifier.border(2.dp, Resurge.colors.borderStrong, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (mark == Mark.DONE) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.bg, size = 15.dp)
    }
}

/** Today's session, outlined in the accent: what it is, then Start and Done as planned. */
@Composable
private fun TodaySession(day: PlanDay, onStart: () -> Unit, onDoneAsPlanned: () -> Unit) {
    val shape = Resurge.shape.md
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Resurge.colors.surface)
            .border(1.5.dp, Resurge.colors.accent, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkDot(Mark.UPCOMING)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(day.title, style = Resurge.type.headline)
                Text("Today · ${sessionMeta(day)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val detail = when {
            day.exercises.isNotEmpty() -> day.exercises.joinToString(" · ") { "${it.name} ${it.sets}×${it.reps}" }
            day.target.isNotBlank() -> day.target
            else -> null
        }
        detail?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
        if (day.kind == SessionKind.RUN) {
            Text("Runs log themselves from Health Connect. Log it here only if your watch didn't.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(if (day.exercises.isNotEmpty()) "Start" else "Log it", onClick = onStart, modifier = Modifier.weight(1f))
            Button("Done as planned", onClick = onDoneAsPlanned, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
        }
    }
}

private fun sessionMeta(day: PlanDay): String = listOfNotNull(
    day.exercises.size.takeIf { it > 0 }?.let { "$it exercises" },
    day.durationMin?.let { "$it min" },
    day.focus.takeIf { day.exercises.isEmpty() && it.isNotBlank() },
).joinToString(" · ")

private fun workoutMeta(w: com.resurgeai.core.model.Workout, format: UnitFormat): String = listOfNotNull(
    if (w.source == com.resurgeai.core.model.WorkoutSource.HEALTH_CONNECT) w.sourceName ?: "Health Connect" else "Logged",
    w.metrics.distanceM?.let(format::distance),
    "${w.duration.toMinutes()} min",
).joinToString(" · ")

/** The dashed "Log something else" button under the plan: opens quick add. */
@Composable
internal fun LogSomethingElse(onClick: () -> Unit) {
    val shape = Resurge.shape.md
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.fillMaxWidth().height(52.dp).clip(shape).border(1.dp, Resurge.colors.borderStrong, shape),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.textMuted, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Log something else", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
    }
}

/**
 * Quick add: pick a recent activity and a duration (two taps), optionally when it ended, and
 * save. "Add details" opens the full editor with the activity chosen; "All activities" the picker.
 */
@Composable
internal fun QuickLogSheet(
    recents: List<Activity>,
    onSave: (activityId: String, minutes: Int, end: java.time.Instant) -> Unit,
    onAddDetails: (activityId: String) -> Unit,
    onAllActivities: () -> Unit,
    onDismiss: () -> Unit,
) {
    var activity by remember { mutableStateOf(recents.first()) }
    var minutes by remember { mutableIntStateOf(30) }
    val today = java.time.LocalDate.now()
    var day by remember { mutableStateOf(today) }
    var agoMinutes by remember { mutableIntStateOf(0) }
    var pastEnd by remember { mutableStateOf(QuickLog.EVENING) }
    BottomSheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Log a workout", style = Resurge.type.title, modifier = Modifier.weight(1f))
            UnstyledButton(
                onClick = onAllActivities,
                modifier = Modifier.clip(Resurge.shape.sm),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                indication = LocalIndication.current,
            ) { Text("All activities", style = Resurge.type.label, color = Resurge.colors.textMuted) }
        }
        Overline("Recent", color = Resurge.colors.textSubtle)
        recents.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a -> ActivityTile(a, a.id == activity.id, Modifier.weight(1f)) { activity = a } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Overline("How long", color = Resurge.colors.textSubtle)
        OptionChips(QuickLog.MINUTES, minutes, { minutes = it }, { "$it min" })
        Overline("When", color = Resurge.colors.textSubtle)
        // The backfill week, newest first.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Backfill.days(today).reversed().forEach { d ->
                DayChip(
                    when (d) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
                    },
                    selected = d == day,
                ) { day = d }
            }
        }
        if (day == today) {
            OptionChips(QuickLog.TODAY_ENDS.map { it.first }, agoMinutes, { agoMinutes = it }, { v -> QuickLog.TODAY_ENDS.first { it.first == v }.second })
        } else {
            OptionChips(QuickLog.PAST_ENDS.map { it.first }, pastEnd, { pastEnd = it }, { v -> QuickLog.PAST_ENDS.first { it.first == v }.second })
        }
        Spacer(Modifier.height(4.dp))
        val end = if (day == today) java.time.Instant.now().minusSeconds(agoMinutes * 60L)
        else day.atTime(pastEnd).atZone(java.time.ZoneId.systemDefault()).toInstant()
        Button("Save · ${activity.name}, $minutes min", onClick = { onSave(activity.id, minutes, end) }, modifier = Modifier.fillMaxWidth())
        Button("Add details", onClick = { onAddDetails(activity.id) }, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ActivityTile(activity: Activity, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = Resurge.shape.sm
    UnstyledButton(
        onClick = onClick,
        role = Role.RadioButton,
        modifier = modifier
            .height(76.dp)
            .clip(shape)
            .background(if (selected) Resurge.colors.accentTint else Resurge.colors.surfaceMuted)
            .then(if (selected) Modifier.border(1.5.dp, Resurge.colors.accent, shape) else Modifier)
            .semantics { this.selected = selected; contentDescription = activity.name },
        indication = LocalIndication.current,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(ActivityVisuals.icon(activity.icon), contentDescription = null, tint = Resurge.colors.text, size = 22.dp)
            Text(
                activity.name,
                style = Resurge.type.caption,
                color = if (selected) Resurge.colors.text else Resurge.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = Resurge.shape.sm
    UnstyledButton(
        onClick = onClick,
        role = Role.RadioButton,
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) Resurge.colors.accentTint else Resurge.colors.surfaceMuted)
            .then(if (selected) Modifier.border(1.5.dp, Resurge.colors.accent, shape) else Modifier)
            .semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 14.dp),
        indication = LocalIndication.current,
    ) { Text(label, style = Resurge.type.label, color = if (selected) Resurge.colors.text else Resurge.colors.textMuted) }
}

/** Shown on Fitness without a plan: one tap to build it from the goal and schedule. */
@Composable
internal fun BuildPlanCard(onOpenPlan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("No training plan yet", style = Resurge.type.headline)
        Text("Get a week of sessions built around your goal, days and kit.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        Button("Set up a plan", onClick = onOpenPlan, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
}
