package com.resurgeai.feature.fitness

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.BottomSheet
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.OptionChips
import com.resurgeai.core.designsystem.components.OptionRow
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.ToggleSquares
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.Experience
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.RunMode
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.TrainingLocation
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.WorkoutPlan
import com.resurgeai.core.model.WorkoutStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlanRoute(onBack: () -> Unit, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlanScreen(
        state = state,
        onBack = onBack,
        onRebuild = viewModel::rebuild,
        onAccept = viewModel::accept,
        onDiscard = viewModel::discard,
    )
}

/**
 * The plan in full: what it's for, each day (tap a session for its exercises), how it progresses,
 * the schedule it was built for, and two ways to rebuild it. A rebuild shows as a preview with
 * "Use this plan" and "Keep current".
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onBack: () -> Unit,
    onRebuild: (change: String?, training: TrainingPrefs) -> Unit,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var editSchedule by rememberSaveable { mutableStateOf(false) }
    val shown = state.draft ?: state.plan
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        TopBar(if (state.draft != null) "New plan" else "Your plan", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when {
                !state.loaded -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Spinner(color = Resurge.colors.text) }
                shown == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No plan yet", style = Resurge.type.title)
                    Text("Build one from your goal and schedule.", style = Resurge.type.body, color = Resurge.colors.textMuted)
                    Button("Build my plan", onClick = { onRebuild(null, state.training) }, enabled = state.goal != null && !state.building, loading = state.building)
                }
                else -> {
                    PlanHeader(shown, state.goal, isDraft = state.draft != null)
                    state.error?.let { Text(it, style = Resurge.type.label, color = Resurge.colors.down) }
                    if (state.draft != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Use this plan", onClick = onAccept, loading = state.saving, modifier = Modifier.weight(1f))
                            Button("Keep current", onClick = onDiscard, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
                        }
                    }
                    WeekDays(shown, today)
                    if (shown.progression.isNotBlank()) Section("How it progresses", shown.progression)
                    if (shown.notes.isNotBlank()) Section("Notes", shown.notes)
                    if (state.draft == null) {
                        ScheduleCard(state.training, onEdit = { editSchedule = true })
                        ChangeSomething(building = state.building, onSubmit = { onRebuild(it, state.training) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (editSchedule) {
        ScheduleSheet(
            initial = state.training,
            showStyle = state.goal?.type != TrainingGoal.RUN_FURTHER,
            onSave = { prefs -> editSchedule = false; onRebuild(null, prefs) },
            onDismiss = { editSchedule = false },
        )
    }
}

@Composable
private fun PlanHeader(plan: WorkoutPlan, goal: MajorGoal?, isDraft: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The name says the goal and days; the overline adds how long, and marks a preview.
        Overline(
            listOfNotNull(
                if (isDraft) "Preview" else null,
                goal?.weeks?.let { "${MajorGoal.durationLabel(it)} plan" },
            ).joinToString(" · "),
            color = if (isDraft) Resurge.colors.accent else Resurge.colors.textSubtle,
        )
        Text(plan.name, style = Resurge.type.display)
        if (plan.summary.isNotBlank()) Text(plan.summary, style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
}

/** Monday to Sunday; a session opens to its exercises (or its effort, for cardio). Today is marked. */
@Composable
private fun WeekDays(plan: WorkoutPlan, today: LocalDate) {
    var open by remember(plan.id) { mutableStateOf(plan.days.firstOrNull { it.day == today.dayOfWeek && !it.rest }?.day) }
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
        plan.days.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
            DayEntry(d, isToday = d.day == today.dayOfWeek, expanded = open == d.day) { open = if (open == d.day) null else d.day }
        }
    }
}

@Composable
private fun DayEntry(day: PlanDay, isToday: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = Resurge.type.label,
                color = if (isToday) Resurge.colors.accent else Resurge.colors.textSubtle,
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(day.title, style = if (day.rest) Resurge.type.body else Resurge.type.headline, color = if (day.rest) Resurge.colors.textMuted else Resurge.colors.text)
                val meta = if (day.rest) day.note else listOfNotNull(
                    day.durationMin?.let { "$it min" },
                    day.focus.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (!day.rest) {
                Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp, modifier = if (expanded) Modifier.rotate(90f) else Modifier)
            }
        }
    }
    if (day.rest) {
        header()
        return
    }
    Column {
        UnstyledButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${day.title}, ${if (expanded) "hide" else "show"} details" },
            indication = LocalIndication.current,
            contentAlignment = Alignment.CenterStart,
        ) { header() }
        if (expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                day.exercises.forEach { e ->
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = Resurge.type.label)
                            Text(
                                listOfNotNull(e.load.takeIf { it.isNotBlank() }, e.restSec.takeIf { it > 0 }?.let { "rest ${it}s" }).joinToString(" · "),
                                style = Resurge.type.caption,
                                color = Resurge.colors.textSubtle,
                            )
                        }
                        Text("${e.sets} × ${e.reps}", style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
                    }
                }
                if (day.target.isNotBlank()) Text(day.target, style = Resurge.type.body, color = Resurge.colors.textMuted)
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(title, color = Resurge.colors.textSubtle)
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
}

/** The schedule the plan was built for, with Edit (which rebuilds the plan). */
@Composable
private fun ScheduleCard(training: TrainingPrefs, onEdit: () -> Unit) {
    val days = training.days.sorted().joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Your schedule", color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
            UnstyledButton(
                onClick = onEdit,
                modifier = Modifier.height(32.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
                contentPadding = PaddingValues(horizontal = 14.dp),
                indication = LocalIndication.current,
            ) { Text("Edit", style = Resurge.type.label) }
        }
        ScheduleLine("Days", days)
        ScheduleLine("Where", training.location.label)
        ScheduleLine("Length", "${training.sessionMin} min")
        ScheduleLine("Experience", training.experience.label)
        ScheduleLine("Split", training.style.label)
        ScheduleLine("Runs", training.runs.label)
    }
}

@Composable
private fun ScheduleLine(label: String, value: String) {
    Row {
        Text(label, style = Resurge.type.label, color = Resurge.colors.textSubtle, modifier = Modifier.width(96.dp))
        Text(value, style = Resurge.type.label)
    }
}

/** "Change something": a short request the plan service applies to this week. */
@Composable
private fun ChangeSomething(building: Boolean, onSubmit: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Change something", color = Resurge.colors.textSubtle)
        if (building) {
            Row(
                Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(color = Resurge.colors.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Rebuilding your week. About 20 seconds.", style = Resurge.type.label, color = Resurge.colors.textMuted)
            }
            return@Column
        }
        val text = rememberTextFieldState()
        val submit = {
            val value = text.text.toString().trim()
            if (value.isNotEmpty()) {
                onSubmit(value)
                text.clearText()
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InputField(text, placeholder = "e.g. Cycling instead of Saturday's run", maxLength = 200, modifier = Modifier.weight(1f), onDone = submit)
            Spacer(Modifier.width(8.dp))
            IconButton(ResurgeIcons.ArrowUp, contentDescription = "Rebuild with this change", onClick = submit)
        }
    }
}

/** Edit the schedule (days, kit, length, experience, split, runs); saving rebuilds the plan. */
@Composable
private fun ScheduleSheet(initial: TrainingPrefs, showStyle: Boolean, onSave: (TrainingPrefs) -> Unit, onDismiss: () -> Unit) {
    var prefs by remember { mutableStateOf(initial) }
    BottomSheet(onDismiss) {
        Text("Your schedule", style = Resurge.type.title)
        val days = DayOfWeek.entries
        ToggleSquares(
            labels = days.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
            descriptions = days.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
            selected = prefs.days.map { it.ordinal }.toSet(),
            onToggle = { i ->
                val d = days[i]
                val next = if (d in prefs.days) prefs.days - d else prefs.days + d
                if (next.size <= TrainingPrefs.DAYS_RANGE.last) prefs = prefs.copy(days = next)
            },
        )
        if (!prefs.validDays) Text("Pick two to six days.", style = Resurge.type.caption, color = Resurge.colors.down)
        Overline("Where", color = Resurge.colors.textSubtle)
        OptionChips(TrainingLocation.entries, prefs.location, { prefs = prefs.copy(location = it) }, { it.label })
        Overline("Session length", color = Resurge.colors.textSubtle)
        OptionChips(TrainingPrefs.SESSION_CHOICES, prefs.sessionMin, { prefs = prefs.copy(sessionMin = it) }, { "$it min" })
        Overline("Experience", color = Resurge.colors.textSubtle)
        OptionChips(Experience.entries, prefs.experience, { prefs = prefs.copy(experience = it) }, { it.label })
        if (showStyle) {
            Overline("Workout style", color = Resurge.colors.textSubtle)
            WorkoutStyle.entries.forEach { s -> OptionRow(s.label, s.description, s == prefs.style, onClick = { prefs = prefs.copy(style = s) }) }
            Overline("Running", color = Resurge.colors.textSubtle)
            OptionChips(RunMode.entries, prefs.runs, { prefs = prefs.copy(runs = it) }, { it.label })
        }
        Button("Rebuild my plan", onClick = { onSave(prefs) }, enabled = prefs.validDays && prefs != initial, modifier = Modifier.fillMaxWidth())
    }
}
