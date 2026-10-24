package com.resurgeai.feature.fitness

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.EditableValue
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TypedNumbers
import com.resurgeai.core.designsystem.theme.Resurge
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun SessionLoggerRoute(onClose: () -> Unit, viewModel: SessionLoggerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onClose() }
    SessionLoggerScreen(
        state = state,
        onClose = onClose,
        onToggle = viewModel::toggle,
        onReps = viewModel::setReps,
        onWeight = viewModel::setWeight,
        onAddSet = viewModel::addSet,
        onAllDone = viewModel::allDone,
        onFinish = viewModel::finish,
    )
}

/**
 * Today's planned session as a checklist of sets: each is filled in from the plan and last time,
 * so most sets are one tap. Numbers can be typed over. "Done as planned" ticks everything.
 */
@Composable
fun SessionLoggerScreen(
    state: SessionLoggerState,
    onClose: () -> Unit,
    onToggle: (Int, Int) -> Unit,
    onReps: (Int, Int, Int) -> Unit,
    onWeight: (Int, Int, Double?) -> Unit,
    onAddSet: (Int) -> Unit,
    onAllDone: () -> Unit,
    onFinish: () -> Unit,
) {
    val format = UnitFormat(state.units)
    Screen {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(ResurgeIcons.Close, contentDescription = "Close without saving", onClick = onClose, background = Resurge.colors.bg)
                Elapsed(state.startedAt, Modifier.weight(1f))
                if (state.total > 0 && state.doneCount < state.total) {
                    UnstyledButton(
                        onClick = onAllDone,
                        modifier = Modifier.padding(end = 8.dp).height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        indication = LocalIndication.current,
                    ) { Text("Done as planned", style = Resurge.type.label) }
                } else {
                    Spacer(Modifier.width(44.dp))
                }
            }
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(color = Resurge.colors.text) }
                state.notFound -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("No planned session today", style = Resurge.type.title)
                    Spacer(Modifier.height(8.dp))
                    Text("Log it from the Fitness tab instead.", style = Resurge.type.body, color = Resurge.colors.textMuted)
                }
                else -> {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(state.day?.title.orEmpty(), style = Resurge.type.title, modifier = Modifier.weight(1f))
                            Text("${state.doneCount}", style = Resurge.type.headline)
                            Text(" / ${state.total} sets", style = Resurge.type.label, color = Resurge.colors.textSubtle)
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.border)) {
                            val f = if (state.total == 0) 0f else state.doneCount / state.total.toFloat()
                            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.text))
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(state.exercises) { ei, e ->
                            ExerciseBlock(ei, e, format, onToggle, onReps, onWeight, onAddSet)
                        }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Resurge.colors.bg)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Tap the box to finish a set. Tap a number to change it.",
                            style = Resurge.type.caption,
                            color = Resurge.colors.textSubtle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            text = if (state.doneCount == 0) "Finish a set to save" else "Finish workout",
                            onClick = onFinish,
                            enabled = state.doneCount > 0,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** Minutes and seconds since the logger opened, ticking. */
@Composable
private fun Elapsed(since: Instant, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val secs = Duration.ofMillis(now - since.toEpochMilli()).seconds.coerceAtLeast(0)
    Text(
        "%d:%02d".format(secs / 60, secs % 60),
        style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { contentDescription = "${secs / 60} minutes in" },
    )
}

@Composable
private fun ExerciseBlock(
    index: Int,
    exercise: LoggedExercise,
    format: UnitFormat,
    onToggle: (Int, Int) -> Unit,
    onReps: (Int, Int, Int) -> Unit,
    onWeight: (Int, Int, Double?) -> Unit,
    onAddSet: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(exercise.name, style = Resurge.type.headline, modifier = Modifier.weight(1f))
            exercise.last?.let { Text("Last: ${LastSession.describe(it, format::weight)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle) }
        }
        exercise.sets.forEachIndexed { si, set ->
            SetRow(si, set, format, exercise.name, onToggle = { onToggle(index, si) }, onReps = { onReps(index, si, it) }, onWeight = { onWeight(index, si, it) })
        }
        UnstyledButton(
            onClick = { onAddSet(index) },
            modifier = Modifier.clip(Resurge.shape.sm),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            indication = LocalIndication.current,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text("Add set", style = Resurge.type.label, color = Resurge.colors.textMuted)
            }
        }
    }
}

/** "2   60 kg × 8   ☐": weight and reps typed in place, the box finishes the set. */
@Composable
private fun SetRow(
    number: Int,
    set: LoggedSet,
    format: UnitFormat,
    name: String,
    onToggle: () -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Double?) -> Unit,
) {
    val shape = Resurge.shape.sm
    val textColor = if (set.done) Resurge.colors.textSubtle else Resurge.colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (set.done) Resurge.colors.surface else Resurge.colors.surfaceMuted)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${number + 1}", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.width(22.dp))
        val w = set.weightKg?.let(format::toWeightUnit)
        EditableValue(
            display = w?.let { "${UnitFormat.decimal(it, 1)} ${format.weightUnit}" } ?: "Bodyweight",
            editText = { w?.let { UnitFormat.decimal(it, 1) } ?: "" },
            // Empty means bodyweight.
            onSubmit = { t -> if (t.isBlank()) onWeight(null) else TypedNumbers.parse(t)?.let { onWeight(format.fromWeightUnit(it)) } },
            label = "$name, set ${number + 1} weight",
            style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"),
            color = textColor,
            textAlign = TextAlign.Start,
            // Hugs the value so "× reps" sits right after it; wide enough to type into.
            modifier = Modifier.widthIn(min = 56.dp),
        )
        Text("×", style = Resurge.type.body, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 6.dp))
        EditableValue(
            display = "${set.reps}",
            editText = { "${set.reps}" },
            onSubmit = { t -> TypedNumbers.parseInt(t)?.let(onReps) },
            label = "$name, set ${number + 1} reps",
            style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"),
            color = textColor,
            keyboardType = KeyboardType.Number,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
        UnstyledButton(
            onClick = onToggle,
            role = Role.Checkbox,
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .then(if (set.done) Modifier.background(Resurge.colors.up) else Modifier.border(2.dp, Resurge.colors.borderStrong, shape))
                .semantics { contentDescription = "${if (set.done) "Undo" else "Finish"} set ${number + 1} of $name" },
            indication = LocalIndication.current,
        ) {
            Icon(ResurgeIcons.Check, contentDescription = null, tint = if (set.done) Resurge.colors.bg else Resurge.colors.borderStrong, size = 20.dp)
        }
    }
}
