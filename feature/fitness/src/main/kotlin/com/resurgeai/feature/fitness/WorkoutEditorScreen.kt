package com.resurgeai.feature.fitness

import androidx.compose.ui.text.input.KeyboardType
import com.resurgeai.core.designsystem.components.TypedNumbers
import com.resurgeai.core.designsystem.components.EditableValue
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.EffortScale
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.OptionalStepperRow
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.StepperRow
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.WorkoutSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
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
                    repeat(4) { SkeletonBox(Modifier.fillMaxWidth().height(72.dp), Resurge.shape.md) }
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
            ActivityCard(state, onChangeActivity)
            if (state.activity.id == "other" || state.title.isNotBlank()) {
                val title = syncedText(state.title, vm::setTitle)
                InputField(title, placeholder = "Name (optional)", maxLength = 60)
            }

            SectionLabel("When")
            DayChips(state.date, onSelect = vm::setDate)
            Spacer(Modifier.height(4.dp))
            val clock = clockFormatter()
            Group {
                StepperRow(
                    label = "Start", hint = "Tap the time to type it", icon = ResurgeIcons.Clock, iconTint = Resurge.colors.textMuted,
                    value = state.time.hour * 60 + state.time.minute,
                    onValueChange = { vm.setTime(LocalTime.of(it / 60, it % 60)) },
                    step = 1, range = 0..(24 * 60 - 1),
                    format = { LocalTime.of(it / 60, it % 60).format(clock) },
                    toEdit = { "%02d:%02d".format(it / 60, it % 60) },
                    fromEdit = { t -> TypedNumbers.parseClock(t)?.let { it.hour * 60 + it.minute } },
                )
                RowDivider()
                StepperRow(
                    label = "Duration", hint = "Minutes", icon = ResurgeIcons.Stopwatch, iconTint = Resurge.colors.textMuted,
                    value = state.durationMin, onValueChange = vm::setDuration,
                    step = 1, range = 1..600,
                    format = { UnitFormat.duration(Duration.ofMinutes(it.toLong())) },
                )
            }

            SectionLabel("Details")
            Group {
                CategoryRows(state, vm, f)
                OptionalStepperRow(
                    label = "Calories", hint = if (state.kcalAuto) "Estimated from duration" else "Your number",
                    icon = ResurgeIcons.Flame, iconTint = Resurge.colors.energy,
                    value = state.metrics.kcal, onValueChange = vm::setKcal,
                    step = 1, range = 0..5000, start = 100, format = { "%,d".format(it) },
                )
                RowDivider()
                OptionalStepperRow(
                    label = "Avg heart rate", hint = "bpm",
                    icon = ResurgeIcons.Heart, iconTint = Resurge.colors.heart,
                    value = state.metrics.avgHr, onValueChange = { v -> vm.updateMetrics { it.copy(avgHr = v) } },
                    step = 1, range = 40..220, start = 120, format = { it.toString() },
                )
            }
            Group {
                EffortScale(state.metrics.rpe, onValueChange = { v -> vm.updateMetrics { it.copy(rpe = v) } }, band = ::effortLabel)
            }

            if (state.activity.category == ActivityCategory.STRENGTH) {
                val volume = state.exercises.sumOf { e -> e.sets.sumOf { (it.weightKg ?: 0.0) * it.reps } }
                SectionLabel("Exercises", trailing = if (volume > 0) "Volume ${f.volume(volume)}" else null)
                state.exercises.forEachIndexed { index, exercise ->
                    key(exercise.key) { SetLogger(index, exercise, state.lastTime[LastSession.key(exercise.name)], vm, f) }
                }
                Button("Add exercise", onClick = vm::addExercise, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Plus, modifier = Modifier.fillMaxWidth())
            }

            if (state.duplicates.isNotEmpty()) {
                SectionLabel("Also recorded by")
                Group {
                    state.duplicates.forEachIndexed { i, d ->
                        if (i > 0) RowDivider()
                        DuplicateRow(d, f) { vm.useInstead(d) }
                    }
                }
                Text(
                    "Both devices logged this session, so it's shown once. Use the other recording if it's more accurate.",
                    style = Resurge.type.caption,
                    color = Resurge.colors.textSubtle,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            SectionLabel("Notes")
            val notes = syncedText(state.notes, vm::setNotes)
            InputField(notes, placeholder = "How did it feel?", maxLength = 300)

            if (!state.isNew) {
                Spacer(Modifier.height(16.dp))
                if (state.source == WorkoutSource.HEALTH_CONNECT && state.edited) {
                    Button("Reset to device data", onClick = vm::resetToDevice, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    if (state.source == WorkoutSource.HEALTH_CONNECT) "Hide from feed" else "Delete workout",
                    onClick = vm::delete,
                    variant = ButtonVariant.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        // Docked, so saving is always one tap away however long the form gets.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Resurge.colors.bg)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(if (state.isNew) "Log workout" else "Save", onClick = vm::save, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Another device's recording of this session, with what it measured. */
@Composable
private fun DuplicateRow(d: com.resurgeai.core.model.Workout, f: UnitFormat, onUse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(ResurgeIcons.Watch, contentDescription = null, tint = Resurge.colors.textMuted, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.sourceName ?: "Health Connect", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize))
            val facts = listOfNotNull(
                UnitFormat.duration(d.duration),
                d.metrics.distanceM?.let(f::distance),
                d.metrics.avgHr?.let { "$it bpm" },
            )
            Text(facts.joinToString(" · "), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Button("Use this one", onClick = onUse, variant = ButtonVariant.Secondary)
    }
}

/** A TextFieldState that starts from [initial] and reports edits. */
@Composable
private fun syncedText(initial: String, onChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(initial)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect(onChange) }
    return state
}

/** Rounded `surface` card holding rows split by inset hairlines. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface), content = content)
}

@Composable
private fun RowDivider() = Divider(Modifier.padding(start = 50.dp))

@Composable
private fun ActivityCard(state: WorkoutEditorState, onChangeActivity: () -> Unit) {
    val activity = state.activity
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Icon(ActivityVisuals.icon(activity.icon), contentDescription = null, tint = Resurge.colors.activity, size = 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(activity.name, style = Resurge.type.headline)
            val caption = if (state.source == WorkoutSource.HEALTH_CONNECT) "From ${state.sourceName ?: "Health Connect"}" else activity.category.label
            Text(caption, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Button("Change", onClick = onChangeActivity, variant = ButtonVariant.Secondary)
    }
}

@Composable
private fun DayChips(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    .background(if (isSelected) Resurge.colors.accentMuted else Resurge.colors.surface)
                    .semantics { this.selected = isSelected },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                indication = LocalIndication.current,
            ) {
                Text(
                    label,
                    style = Resurge.type.label.copy(fontWeight = if (isSelected) Resurge.type.headline.fontWeight else Resurge.type.label.fontWeight),
                    color = if (isSelected) Resurge.colors.brandInk else Resurge.colors.text,
                )
            }
        }
    }
}

/** The fields this kind of activity has, as compact rows; each ends with a divider. */
@Composable
private fun CategoryRows(state: WorkoutEditorState, vm: WorkoutEditorViewModel, f: UnitFormat) {
    val m = state.metrics
    val duration = Duration.ofMinutes(state.durationMin.toLong())
    when (state.activity.category) {
        ActivityCategory.DISTANCE -> {
            // Tenths of a km (or mile), so the stepper moves 0.1 at a time.
            val derived = if (state.activity.showSpeed) FitnessMath.speedKmh(m.distanceM, duration)?.let { "Avg speed ${f.speed(it)}" }
            else FitnessMath.paceSecPerKm(m.distanceM, duration)?.let { "Avg pace ${f.pace(it)}" }
            OptionalStepperRow(
                label = "Distance", hint = derived ?: "0.1 ${f.distanceUnit} steps", icon = ResurgeIcons.Distance, iconTint = Resurge.colors.activity,
                value = m.distanceM?.let { (f.toDistanceUnit(it) * 10).roundToInt() },
                onValueChange = { v -> vm.updateMetrics { it.copy(distanceM = v?.let { t -> f.fromDistanceUnit(t / 10.0) }) } },
                step = 1, range = 1..5000, start = if (state.activity.showSpeed) 100 else 30,
                format = { f.distanceValue(it / 10.0) },
                toEdit = TypedNumbers::tenths, fromEdit = TypedNumbers::parseTenths, keyboardType = KeyboardType.Decimal,
            )
            RowDivider()
            OptionalStepperRow(
                label = "Elevation gain", hint = f.elevationUnit, icon = ResurgeIcons.Mountain, iconTint = Resurge.colors.textMuted,
                value = m.elevationM?.let { f.toElevationUnit(it).roundToInt() },
                onValueChange = { v -> vm.updateMetrics { it.copy(elevationM = v?.let { e -> f.fromElevationUnit(e.toDouble()) }) } },
                step = 1, range = 10..9000, start = 50, format = { it.toString() },
            )
            RowDivider()
        }
        ActivityCategory.SWIM -> {
            Box(Modifier.padding(16.dp)) {
                SegmentedControl(
                    options = listOf(SegmentOption(25, "25 m pool"), SegmentOption(50, "50 m pool"), SegmentOption(23, "25 yd pool")),
                    selected = m.poolLengthM ?: 25,
                    onSelect = { len -> vm.updateMetrics { it.copy(poolLengthM = len) } },
                )
            }
            RowDivider()
            OptionalStepperRow(
                label = "Laps",
                hint = FitnessMath.swimDistanceM(m.copy(distanceM = null))?.let { "Distance ${f.distance(it)}" } ?: "Lengths of the pool",
                icon = ResurgeIcons.Repeat, iconTint = Resurge.colors.activity,
                value = m.laps, onValueChange = { v -> vm.updateMetrics { it.copy(laps = v) } },
                step = 1, range = 1..400, start = 20, format = { it.toString() },
            )
            RowDivider()
        }
        ActivityCategory.COUNT -> {
            OptionalStepperRow(
                label = "Reps", hint = null, icon = ResurgeIcons.Repeat, iconTint = Resurge.colors.activity,
                value = m.reps, onValueChange = { v -> vm.updateMetrics { it.copy(reps = v) } },
                step = 1, range = 1..10000, start = 50, format = { "%,d".format(it) },
            )
            RowDivider()
        }
        ActivityCategory.TIMED, ActivityCategory.STRENGTH -> Unit
    }
}

internal fun effortLabel(rpe: Int) = when (rpe) {
    in 1..3 -> "Easy"
    in 4..6 -> "Moderate"
    in 7..8 -> "Hard"
    else -> "Max"
}

/**
 * One strength exercise: its name, what it was last time, then numbered sets of reps × weight.
 * "Add set" copies the previous set. When the volume beats last time, the earned pill says by how much.
 */
@Composable
private fun SetLogger(index: Int, exercise: EditableExercise, last: List<StrengthSet>?, vm: WorkoutEditorViewModel, f: UnitFormat) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = syncedText(exercise.name) { vm.renameExercise(exercise.key, it) }
            Box(Modifier.weight(1f)) { InputField(name, placeholder = "Exercise ${index + 1} (e.g. Bench press)", maxLength = 40) }
            Spacer(Modifier.width(4.dp))
            SmallIcon(ResurgeIcons.Close, "Remove exercise") { vm.removeExercise(exercise.key) }
        }
        last?.let {
            Text("Last time: ${LastSession.describe(it, f::weight)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(36.dp))
            Text("Reps", style = Resurge.type.caption, color = Resurge.colors.textSubtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("Weight (${f.weightUnit})", style = Resurge.type.caption, color = Resurge.colors.textSubtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(32.dp))
        }
        exercise.sets.forEachIndexed { i, set ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
                Spacer(Modifier.width(8.dp))
                MiniStepper(
                    value = set.reps.toString(), label = "Reps, set ${i + 1}",
                    editText = set.reps.toString(),
                    onSubmit = { t -> TypedNumbers.parseInt(t)?.let { vm.updateSet(exercise.key, i, set.copy(reps = it.coerceIn(1, 200))) } },
                    keyboardType = KeyboardType.Number,
                    canDecrease = set.reps > 1,
                    onDecrease = { vm.updateSet(exercise.key, i, set.copy(reps = set.reps - 1)) },
                    onIncrease = { vm.updateSet(exercise.key, i, set.copy(reps = (set.reps + 1).coerceAtMost(200))) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val w = set.weightKg?.let(f::toWeightUnit)
                MiniStepper(
                    value = w?.let { UnitFormat.decimal(it, 1) } ?: "—", label = "Weight, set ${i + 1}",
                    editText = w?.let { "%.1f".format(it) } ?: "",
                    // Empty means bodyweight.
                    onSubmit = { t ->
                        if (t.isBlank()) vm.updateSet(exercise.key, i, set.copy(weightKg = null))
                        else TypedNumbers.parse(t)?.let { vm.updateSet(exercise.key, i, set.copy(weightKg = f.fromWeightUnit(it.coerceIn(0.0, 1000.0)).takeIf { kg -> kg > 0 })) }
                    },
                    keyboardType = KeyboardType.Decimal,
                    canDecrease = w != null,
                    onDecrease = {
                        val next = (((w ?: 0.0) - f.weightStep) * 10).roundToInt() / 10.0
                        vm.updateSet(exercise.key, i, set.copy(weightKg = if (next < f.weightStep) null else f.fromWeightUnit(next)))
                    },
                    onIncrease = {
                        val next = if (w == null) (if (f.weightUnit == "kg") 20.0 else 45.0) else ((w + f.weightStep) * 10).roundToInt() / 10.0
                        vm.updateSet(exercise.key, i, set.copy(weightKg = f.fromWeightUnit(next.coerceAtMost(1000.0))))
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                SmallIcon(ResurgeIcons.Close, "Remove set ${i + 1}") { vm.removeSet(exercise.key, i) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button("Add set", onClick = { vm.addSet(exercise.key) }, variant = ButtonVariant.Ghost, leadingIcon = ResurgeIcons.Plus)
            Spacer(Modifier.weight(1f))
            val volume = exercise.sets.sumOf { (it.weightKg ?: 0.0) * it.reps }
            val lastVolume = last?.sumOf { (it.weightKg ?: 0.0) * it.reps } ?: 0.0
            if (lastVolume > 0 && volume > lastVolume) EarnedPill("+${f.volume(volume - lastVolume)} vs last time")
        }
    }
}

/** − value + in one `surface-muted` pill, for the dense set rows. */
@Composable
private fun MiniStepper(
    value: String,
    label: String,
    editText: String,
    onSubmit: (String) -> Unit,
    keyboardType: KeyboardType,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier.height(40.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniButton("−", "Decrease $label", canDecrease, onDecrease)
        EditableValue(
            display = value,
            editText = { editText },
            onSubmit = onSubmit,
            label = label,
            style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"),
            keyboardType = keyboardType,
            modifier = Modifier.weight(1f),
        )
        MiniButton("+", "Increase $label", true, onIncrease)
    }
}

@Composable
private fun MiniButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).semantics { contentDescription = description },
        indication = LocalIndication.current,
    ) {
        Text(symbol, style = Resurge.type.headline, color = if (enabled) Resurge.colors.text else Resurge.colors.textSubtle)
    }
}

@Composable
private fun SmallIcon(icon: Int, description: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp).clip(Resurge.shape.pill).semantics { contentDescription = description },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp) }
}
