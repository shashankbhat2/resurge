package com.resurgeai.feature.fitness

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.TimeStepper
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.components.ValueStepper
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.WorkoutSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Log or edit a workout. [pickedActivity] carries a result from the activity picker when the user
 * taps "Change"; [onChangeActivity] opens it.
 */
@Composable
fun WorkoutEditorRoute(
    onDone: () -> Unit,
    onChangeActivity: () -> Unit,
    pickedActivity: String?,
    onPickedActivityConsumed: () -> Unit,
    viewModel: WorkoutEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(pickedActivity) {
        if (pickedActivity != null) {
            viewModel.setActivity(pickedActivity)
            onPickedActivityConsumed()
        }
    }
    LaunchedEffect(state.done) { if (state.done) onDone() }
    WorkoutEditorScreen(state, viewModel, onBack = onDone, onChangeActivity = onChangeActivity)
}

@Composable
internal fun WorkoutEditorScreen(
    state: WorkoutEditorState,
    vm: WorkoutEditorViewModel,
    onBack: () -> Unit,
    onChangeActivity: () -> Unit,
) {
    val f = UnitFormat(state.units)
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (state.isNew) "Log workout" else "Edit workout", onBack = onBack)
        if (!state.loaded) {
            SkeletonGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), Resurge.shape.lg) }
                }
            }
            return
        }
        if (state.notFound) {
            Text(
                "This workout is no longer on your device.",
                style = Resurge.type.body,
                color = Resurge.colors.textMuted,
                modifier = Modifier.padding(24.dp),
            )
            return
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityHeader(state, onChangeActivity)
            if (state.activity.id == "other" || state.title.isNotBlank()) {
                val title = syncedText(state.title, vm::setTitle)
                InputField(title, placeholder = "Name (optional)", maxLength = 60)
            }

            SectionHeader("When")
            DayChips(state.date, onSelect = vm::setDate)
            Spacer(Modifier.height(4.dp))
            TimeStepper(state.time, onTimeChange = vm::setTime, caption = "Start time", stepMinutes = 15)

            SectionHeader("Details")
            ValueStepper(
                label = "Duration",
                value = state.durationMin.toDouble(),
                onValueChange = { vm.setDuration((it ?: 1.0).roundToInt()) },
                step = 5.0,
                range = 1.0..600.0,
                format = { UnitFormat.duration(java.time.Duration.ofMinutes(it.toLong())) },
                icon = ResurgeIcons.Stopwatch,
                optional = false,
            )
            CategoryFields(state, vm, f)
            ValueStepper(
                label = if (state.kcalAuto) "Calories (est.)" else "Calories",
                value = state.metrics.kcal?.toDouble(),
                onValueChange = { v -> vm.setKcal(v?.roundToInt()) },
                step = 10.0,
                range = 0.0..5000.0,
                format = { "${it.roundToInt()} kcal" },
                icon = ResurgeIcons.Flame,
                start = 100.0,
            )
            ValueStepper(
                label = "Avg heart rate",
                value = state.metrics.avgHr?.toDouble(),
                onValueChange = { v -> vm.updateMetrics { it.copy(avgHr = v?.roundToInt()) } },
                step = 1.0,
                range = 40.0..220.0,
                format = { "${it.roundToInt()} bpm" },
                icon = ResurgeIcons.Heart,
                start = 120.0,
            )

            if (state.activity.category == ActivityCategory.STRENGTH) {
                SectionHeader("Exercises")
                StrengthEditor(state, vm, f)
            }

            SectionHeader("Notes")
            val notes = syncedText(state.notes, vm::setNotes)
            InputField(notes, placeholder = "How did it feel?", maxLength = 300)

            Spacer(Modifier.height(16.dp))
            Button(if (state.isNew) "Log workout" else "Save", onClick = vm::save, modifier = Modifier.fillMaxWidth())
            if (state.source == WorkoutSource.HEALTH_CONNECT && state.edited) {
                Button("Reset to device data", onClick = vm::resetToDevice, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
            }
            if (!state.isNew) {
                Button(
                    if (state.source == WorkoutSource.HEALTH_CONNECT) "Hide from feed" else "Delete workout",
                    onClick = vm::delete,
                    variant = ButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A TextFieldState that starts from [initial] and reports edits. */
@Composable
private fun syncedText(initial: String, onChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(initial)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect(onChange) }
    return state
}

@Composable
private fun ActivityHeader(state: WorkoutEditorState, onChangeActivity: () -> Unit) {
    val activity = state.activity
    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ActivityVisuals.icon(activity.icon), tint = ActivityVisuals.color(activity.category), size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(activity.name, style = Resurge.type.title)
                val caption = if (state.source == WorkoutSource.HEALTH_CONNECT) "From ${state.sourceName ?: "Health Connect"}" else activity.category.label
                Text(caption, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Button("Change", onClick = onChangeActivity, variant = ButtonVariant.Secondary)
        }
    }
}

@Composable
private fun DayChips(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (0L..6L).forEach { back ->
            val date = today.minusDays(back)
            val label = when (back) {
                0L -> "Today"
                1L -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
            }
            val isSelected = date == selected
            UnstyledButton(
                onClick = { onSelect(date) },
                role = Role.Tab,
                modifier = Modifier
                    .clip(Resurge.shape.pill)
                    .background(if (isSelected) Resurge.colors.strong else Resurge.colors.surface)
                    .border(1.dp, Resurge.colors.border, Resurge.shape.pill)
                    .semantics { this.selected = isSelected },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                indication = LocalIndication.current,
            ) {
                Text(label, style = Resurge.type.label, color = if (isSelected) Resurge.colors.bg else Resurge.colors.text)
            }
        }
    }
}

@Composable
private fun CategoryFields(state: WorkoutEditorState, vm: WorkoutEditorViewModel, f: UnitFormat) {
    val m = state.metrics
    when (state.activity.category) {
        ActivityCategory.DISTANCE -> {
            ValueStepper(
                label = "Distance",
                value = m.distanceM?.let(f::toDistanceUnit),
                onValueChange = { v -> vm.updateMetrics { it.copy(distanceM = v?.let(f::fromDistanceUnit)) } },
                step = 0.1,
                range = 0.1..500.0,
                format = f::distanceValue,
                icon = ResurgeIcons.Distance,
                start = if (state.activity.showSpeed) 10.0 else 3.0,
            )
            val pace = FitnessMath.paceSecPerKm(m.distanceM, java.time.Duration.ofMinutes(state.durationMin.toLong()))
            val speed = FitnessMath.speedKmh(m.distanceM, java.time.Duration.ofMinutes(state.durationMin.toLong()))
            val derived = if (state.activity.showSpeed) speed?.let(f::speed) else pace?.let(f::pace)
            if (derived != null) {
                Text(
                    "${if (state.activity.showSpeed) "Average speed" else "Average pace"}: $derived",
                    style = Resurge.type.caption,
                    color = Resurge.colors.textMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            ValueStepper(
                label = "Elevation gain",
                value = m.elevationM?.let(f::toElevationUnit),
                onValueChange = { v -> vm.updateMetrics { it.copy(elevationM = v?.let(f::fromElevationUnit)) } },
                step = 10.0,
                range = 10.0..9000.0,
                format = { "${it.roundToInt()} ${f.elevationUnit}" },
                icon = ResurgeIcons.Mountain,
            )
        }
        ActivityCategory.SWIM -> {
            SegmentedControl(
                options = listOf(SegmentOption(25, "25 m pool"), SegmentOption(50, "50 m pool"), SegmentOption(23, "25 yd pool")),
                selected = m.poolLengthM ?: 25,
                onSelect = { len -> vm.updateMetrics { it.copy(poolLengthM = len) } },
            )
            ValueStepper(
                label = "Laps",
                value = m.laps?.toDouble(),
                onValueChange = { v -> vm.updateMetrics { it.copy(laps = v?.roundToInt()) } },
                step = 2.0,
                range = 1.0..400.0,
                format = { "${it.roundToInt()}" },
                icon = ResurgeIcons.Repeat,
                start = 20.0,
            )
            FitnessMath.swimDistanceM(m.copy(distanceM = null))?.let {
                Text("Distance: ${f.distance(it)}", style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(start = 4.dp))
            }
        }
        ActivityCategory.TIMED -> EffortField(m.rpe) { v -> vm.updateMetrics { it.copy(rpe = v) } }
        ActivityCategory.COUNT -> {
            ValueStepper(
                label = "Reps",
                value = m.reps?.toDouble(),
                onValueChange = { v -> vm.updateMetrics { it.copy(reps = v?.roundToInt()) } },
                step = 5.0,
                range = 1.0..10000.0,
                format = { "${it.roundToInt()}" },
                icon = ResurgeIcons.Repeat,
                start = 50.0,
            )
            EffortField(m.rpe) { v -> vm.updateMetrics { it.copy(rpe = v) } }
        }
        ActivityCategory.STRENGTH -> EffortField(m.rpe) { v -> vm.updateMetrics { it.copy(rpe = v) } }
    }
}

@Composable
private fun EffortField(rpe: Int?, onChange: (Int?) -> Unit) {
    ValueStepper(
        label = "Effort",
        value = rpe?.toDouble(),
        onValueChange = { onChange(it?.roundToInt()) },
        step = 1.0,
        range = 1.0..10.0,
        format = { v -> "${v.roundToInt()}/10 · ${effortLabel(v.roundToInt())}" },
        icon = ResurgeIcons.Activity,
        start = 6.0,
    )
}

private fun effortLabel(rpe: Int) = when (rpe) {
    in 1..3 -> "Easy"
    in 4..6 -> "Moderate"
    in 7..8 -> "Hard"
    else -> "Max"
}

@Composable
private fun StrengthEditor(state: WorkoutEditorState, vm: WorkoutEditorViewModel, f: UnitFormat) {
    state.exercises.forEachIndexed { index, exercise ->
        androidx.compose.runtime.key(exercise.key) {
            Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = syncedText(exercise.name) { vm.renameExercise(exercise.key, it) }
                    Box(Modifier.weight(1f)) { InputField(name, placeholder = "Exercise ${index + 1} (e.g. Bench press)", maxLength = 40) }
                    Spacer(Modifier.width(8.dp))
                    IconButton(ResurgeIcons.Close, contentDescription = "Remove exercise", onClick = { vm.removeExercise(exercise.key) })
                }
                Spacer(Modifier.height(10.dp))
                exercise.sets.forEachIndexed { i, set ->
                    SetRow(i, set, f, onChange = { vm.updateSet(exercise.key, i, it) }, onRemove = { vm.removeSet(exercise.key, i) })
                    Spacer(Modifier.height(6.dp))
                }
                Button("Add set", onClick = { vm.addSet(exercise.key) }, variant = ButtonVariant.Ghost, leadingIcon = ResurgeIcons.Plus)
            }
        }
    }
    Button("Add exercise", onClick = vm::addExercise, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Plus, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SetRow(index: Int, set: StrengthSet, f: UnitFormat, onChange: (StrengthSet) -> Unit, onRemove: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Set ${index + 1}", style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f).padding(start = 4.dp))
            UnstyledButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp).clip(Resurge.shape.pill).semantics { contentDescription = "Remove set ${index + 1}" },
                indication = LocalIndication.current,
            ) { com.resurgeai.core.designsystem.components.Icon(ResurgeIcons.Close, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp) }
        }
        ValueStepper(
            label = "Reps",
            value = set.reps.toDouble(),
            onValueChange = { onChange(set.copy(reps = (it ?: 1.0).roundToInt())) },
            step = 1.0,
            range = 1.0..200.0,
            format = { "${it.roundToInt()}" },
            optional = false,
        )
        Spacer(Modifier.height(4.dp))
        ValueStepper(
            label = "Weight",
            value = set.weightKg?.let(f::toWeightUnit),
            onValueChange = { v -> onChange(set.copy(weightKg = v?.let(f::fromWeightUnit))) },
            step = f.weightStep,
            range = f.weightStep..1000.0,
            format = f::weightValue,
            start = if (f.weightUnit == "kg") 20.0 else 45.0,
        )
    }
}
