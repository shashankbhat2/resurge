package com.resurgeai.feature.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.DayState
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStatus
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsRoute(
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HabitsScreen(
        state = state,
        onSwipe = viewModel::swipe,
        onUndo = viewModel::undo,
        onShowStreaks = viewModel::showStreaks,
        onToggleDay = viewModel::toggleDay,
        onOpenHabit = onOpenHabit,
        onAddHabit = onAddHabit,
    )
}

/** Habits tab: today's check-in deck while habits are pending, otherwise today's card and each habit's week. */
@Composable
fun HabitsScreen(
    state: HabitsUiState,
    onSwipe: (String, Boolean) -> Unit,
    onUndo: () -> Unit,
    onShowStreaks: (Boolean) -> Unit,
    onToggleDay: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
) {
    val top = LocalHeaderInset.current + 12.dp
    val bottom = LocalFloatingBarInset.current + 24.dp
    when {
        state.loading -> SkeletonGroup {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = top), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBox(Modifier.fillMaxWidth().height(150.dp), Resurge.shape.lg)
                repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(140.dp), Resurge.shape.md) }
            }
        }
        state.showDeck -> HabitDeck(
            state = state,
            onSwipe = onSwipe,
            onUndo = onUndo,
            onShowStreaks = { onShowStreaks(true) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = bottom),
        )
        else -> HabitsList(state, onShowStreaks, onToggleDay, onOpenHabit, onAddHabit, top, bottom)
    }
}

@Composable
private fun HabitsList(
    state: HabitsUiState,
    onShowStreaks: (Boolean) -> Unit,
    onToggleDay: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
    onAddHabit: () -> Unit,
    top: Dp,
    bottom: Dp,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TodayCard(state, onCheckIn = { onShowStreaks(false) })
        SectionLabel("Your habits", Modifier.padding(top = 8.dp), trailing = "${state.items.size} of ${HabitSettings.MAX_ACTIVE}")
        state.items.forEach { item -> HabitCard(item, state.today, onToggleDay, onOpenHabit) }
        AddHabitTile(state.canAdd, onAddHabit)
    }
}

/**
 * Today at a glance: how many habits are done, a segment per habit in its own colour, when the
 * day resets, and a way into the check-in deck while some are still open.
 */
@Composable
private fun TodayCard(state: HabitsUiState, onCheckIn: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Overline("Today's habits")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.doneToday}", style = Resurge.type.tide.copy(fontSize = Resurge.type.metric.fontSize * 1.4f, lineHeight = Resurge.type.metric.lineHeight * 1.4f))
                    Spacer(Modifier.width(6.dp))
                    Text("of ${state.items.size} done", style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Text("Resets ${state.settings.resetTime.format(clockFormatter())}", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(bottom = 6.dp))
        }
        // One segment per habit, in its own colour once done.
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            state.items.forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Resurge.shape.pill)
                        .background(if (item.todayStatus == HabitStatus.DONE) HabitVisuals.fill(item.habit.color) else Resurge.colors.surfaceMuted),
                )
            }
        }
        if (state.pending.isNotEmpty()) {
            Button(
                "Check in · ${state.pending.size} left",
                onClick = onCheckIn,
                variant = ButtonVariant.Accent,
                leadingIcon = ResurgeIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (state.items.isNotEmpty()) {
            Text("All done for today.", style = Resurge.type.label, color = Resurge.colors.brandInk)
        }
    }
}

/**
 * One habit: icon, name and streak, a check for today, and the last 7 days as streak cells.
 * Today and yesterday (still in its grace period) can be tapped to flip. Tapping the card opens it.
 */
@Composable
private fun HabitCard(
    item: HabitItem,
    today: LocalDate,
    onToggleDay: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val done = item.stats.days[today] == DayState.DONE
    UnstyledButton(
        onClick = { onOpenHabit(item.habit.id) },
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconTile(item.habit.icon, item.habit.color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.habit.name, style = Resurge.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val running = item.stats.current > 0
                        Icon(ResurgeIcons.HabitStreak, contentDescription = null, tint = if (running) Resurge.colors.glowInk else Resurge.colors.textSubtle, size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                append(if (running) "${item.stats.current}-day streak" else "No streak yet")
                                if (item.stats.best > 0) append(" · best ${item.stats.best}")
                            },
                            style = Resurge.type.caption,
                            color = if (running) Resurge.colors.glowInk else Resurge.colors.textSubtle,
                        )
                    }
                }
                TodayCheck(done, HabitVisuals.fill(item.habit.color), item.habit.name) {
                    haptics.performHapticFeedback(if (done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggleDay(item.habit.id, today)
                }
            }
            WeekCells(item, today, onToggleDay)
        }
    }
}

/** Today's check: an outlined circle, filled with the habit's colour once done. */
@Composable
private fun TodayCheck(done: Boolean, fill: Color, name: String, onClick: () -> Unit) {
    val bg by animateColorAsState(if (done) fill else Color.Transparent, tween(200), label = "check")
    UnstyledButton(
        onClick = onClick,
        role = Role.Checkbox,
        modifier = Modifier
            .size(40.dp)
            .clip(Resurge.shape.pill)
            .background(bg)
            .then(if (done) Modifier else Modifier.border(2.dp, Resurge.colors.border, Resurge.shape.pill))
            .semantics {
                contentDescription = "$name today"
                stateDescription = if (done) "Done" else "Not done yet"
            },
        indication = LocalIndication.current,
    ) {
        if (done) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = 20.dp)
    }
}

@Composable
private fun WeekCells(item: HabitItem, today: LocalDate, onToggleDay: (String, LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val date = today.minusDays(back.toLong())
            val dayState = item.stats.days[date] ?: DayState.NONE
            val editable = back <= 1
            val dayName = if (back == 0) "Today" else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val described = Modifier.semantics {
                    contentDescription = "$dayName: ${dayState.label}" + if (editable) ". Tap to change" else ""
                }
                if (editable) {
                    UnstyledButton(
                        onClick = { onToggleDay(item.habit.id, date) },
                        modifier = described.clip(RoundedCornerShape(10.dp)),
                        indication = LocalIndication.current,
                    ) { DayCell(dayState, item.habit.color) }
                } else {
                    DayCell(dayState, item.habit.color, described)
                }
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption.copy(fontWeight = if (back == 0) FontWeight.Bold else null),
                    color = if (back == 0) Resurge.colors.text else Resurge.colors.textSubtle,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A dashed tile to add a habit, or a note when all eight slots are taken. */
@Composable
private fun AddHabitTile(canAdd: Boolean, onAdd: () -> Unit) {
    UnstyledButton(
        onClick = onAdd,
        enabled = canAdd,
        modifier = Modifier.fillMaxWidth().height(64.dp).clip(Resurge.shape.md).dashedBorder(Resurge.colors.border, 16.dp),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canAdd) {
                Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.textMuted, size = 20.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (canAdd) "Add habit" else "${HabitSettings.MAX_ACTIVE} habits is the most. Archive one to add another.",
                style = Resurge.type.label,
                color = Resurge.colors.textMuted,
            )
        }
    }
}
