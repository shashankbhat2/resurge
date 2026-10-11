package com.resurgeai.feature.habits

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.Stat
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitStreaks
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Streak history (12-week heatmap), stats, edit and archive for one habit. */
@Composable
fun HabitDetailRoute(
    habitId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item(habitId)
    // Archived (or deleted elsewhere): leave once data has loaded.
    LaunchedEffect(state.loading, item == null) { if (!state.loading && item == null) onBack() }
    if (item == null) return
    val tint = HabitVisuals.color(item.habit.color)
    var confirmArchive by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = item.habit.name,
            onBack = onBack,
            trailing = { IconButton(ResurgeIcons.Pencil, contentDescription = "Edit habit", onClick = { onEdit(habitId) }) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.stats.current > 0) ResurgeIcons.FlameFilled else ResurgeIcons.Flame,
                        contentDescription = null,
                        tint = if (item.stats.current > 0) Resurge.colors.energy else Resurge.colors.textSubtle,
                        size = 32.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("${item.stats.current}", style = Resurge.type.display)
                    Spacer(Modifier.width(6.dp))
                    Text("day streak", style = Resurge.type.label, color = Resurge.colors.textMuted)
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    Stat("Best streak", "${item.stats.best} days", Modifier.weight(1f))
                    Stat("Last 30 days", "${(item.stats.completion30 * 100).roundToInt()}%", Modifier.weight(1f))
                    Stat("Freeze", if (item.stats.freezesLeftThisWeek > 0) "Ready" else "Used", Modifier.weight(1f))
                }
            }
            SectionHeader("Last 12 weeks")
            Card(Modifier.fillMaxWidth()) {
                Heatmap(item.stats.days, state.today, tint)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend(Modifier.background(tint), "Done")
                    Legend(Modifier.background(Resurge.colors.water.copy(alpha = 0.5f)), "Freeze")
                    Legend(Modifier.background(Resurge.colors.surfaceMuted).border(1.dp, Resurge.colors.border, Resurge.shape.sm), "Not done")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Freezes: if you miss a day, one freeze a week (Sunday–Saturday) keeps your streak alive automatically.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
            )
            Spacer(Modifier.height(20.dp))
            val canArchive = state.items.size > 1
            Button(
                text = when {
                    !canArchive -> "Keep at least one habit"
                    confirmArchive -> "Tap again to archive"
                    else -> "Archive habit"
                },
                onClick = {
                    if (confirmArchive) {
                        if (viewModel.archive(habitId)) onBack()
                    } else {
                        confirmArchive = true
                    }
                },
                enabled = canArchive,
                variant = ButtonVariant.Danger,
                leadingIcon = ResurgeIcons.Archive,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 12 Sunday-start weeks as columns, Sun…Sat rows; the current week is the rightmost column. */
@Composable
private fun Heatmap(days: Map<LocalDate, DayState>, today: LocalDate, tint: Color) {
    val lastWeek = HabitStreaks.weekStart(today)
    val firstWeek = lastWeek.minusWeeks(11)
    Row {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until 7).forEach { d ->
                Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        firstWeek.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = Resurge.type.caption,
                        color = Resurge.colors.textSubtle,
                    )
                }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until 12).forEach { w ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until 7).forEach { d ->
                        val date = firstWeek.plusWeeks(w.toLong()).plusDays(d.toLong())
                        val state = if (date.isAfter(today)) null else days[date]
                        val cell = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(Resurge.shape.sm)
                        Box(
                            when (state) {
                                DayState.DONE -> cell.background(tint)
                                DayState.FROZEN -> cell.background(Resurge.colors.water.copy(alpha = 0.5f))
                                DayState.MISSED -> cell.background(Resurge.colors.surfaceMuted).border(1.dp, Resurge.colors.border, Resurge.shape.sm)
                                DayState.PENDING -> cell.border(1.dp, Resurge.colors.border, Resurge.shape.sm)
                                DayState.NONE, null -> cell
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(swatch: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(Resurge.shape.sm).then(swatch))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

/** Add (habitId = null) or edit a habit: name, icon and colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditorRoute(
    habitId: String?,
    onDone: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val existing = habitId?.let { state.item(it)?.habit }
    val name = rememberTextFieldState()
    var icon by remember { mutableStateOf("flame") }
    var color by remember { mutableStateOf("accent") }
    var initialised by remember { mutableStateOf(habitId == null) }
    LaunchedEffect(existing) {
        if (!initialised && existing != null) {
            name.edit { replace(0, length, existing.name) }
            icon = existing.icon
            color = existing.color
            initialised = true
        }
    }
    val canSave = name.text.isNotBlank() && (habitId != null || state.canAdd)
    val save = {
        if (canSave) {
            val draft = HabitDraft(name.text.toString().trim(), icon, color)
            if (habitId == null) viewModel.add(draft) else viewModel.update(habitId, draft)
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = if (habitId == null) "New habit" else "Edit habit", onBack = onDone)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            InputField(name, placeholder = "e.g. Walk after dinner", onDone = save)
            SectionHeader("Icon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HabitVisuals.icons.forEach { (key, res) ->
                    val selected = key == icon
                    UnstyledButton(
                        onClick = { icon = key },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(Resurge.shape.pill)
                            .background(if (selected) HabitVisuals.color(color).copy(alpha = 0.16f) else Resurge.colors.surface)
                            .border(if (selected) 2.dp else 1.dp, if (selected) HabitVisuals.color(color) else Resurge.colors.border, Resurge.shape.pill)
                            .semantics { contentDescription = key.replace('_', ' '); this.selected = selected },
                        indication = LocalIndication.current,
                    ) { Icon(res, contentDescription = null, tint = if (selected) HabitVisuals.color(color) else Resurge.colors.textMuted, size = 22.dp) }
                }
            }
            SectionHeader("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitVisuals.colorKeys.forEach { key ->
                    val selected = key == color
                    UnstyledButton(
                        onClick = { color = key },
                        modifier = Modifier
                            .size(40.dp)
                            .aspectRatio(1f)
                            .clip(Resurge.shape.pill)
                            .border(if (selected) 3.dp else 0.dp, Resurge.colors.text, Resurge.shape.pill)
                            .padding(if (selected) 5.dp else 0.dp)
                            .clip(Resurge.shape.pill)
                            .background(HabitVisuals.color(key))
                            .semantics { contentDescription = key; this.selected = selected },
                        indication = LocalIndication.current,
                    ) {}
                }
            }
            Spacer(Modifier.height(28.dp))
            Button(
                text = if (habitId == null) "Add habit" else "Save",
                onClick = save,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}
