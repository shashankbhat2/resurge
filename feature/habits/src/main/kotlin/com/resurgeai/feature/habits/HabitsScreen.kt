package com.resurgeai.feature.habits

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.DayState
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

/** Habits tab: today's swipe deck while habits are pending, otherwise the streaks list. */
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
                repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(120.dp), Resurge.shape.lg) }
            }
        }
        state.showDeck -> HabitDeck(
            state = state,
            onSwipe = onSwipe,
            onUndo = onUndo,
            onShowStreaks = { onShowStreaks(true) },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = bottom),
        )
        else -> StreaksList(state, onShowStreaks, onToggleDay, onOpenHabit, onAddHabit, top, bottom)
    }
}

@Composable
private fun StreaksList(
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                    Spacer(Modifier.height(4.dp))
                    Text("${state.doneToday} of ${state.items.size} done", style = Resurge.type.title)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Resets at ${state.settings.resetTime.format(clockFormatter())}",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textSubtle,
                    )
                }
                if (state.pending.isNotEmpty()) {
                    Button("Log ${state.pending.size} left", onClick = { onShowStreaks(false) })
                }
            }
        }
        state.items.forEach { item -> HabitRow(item, state.today, onToggleDay, onOpenHabit) }
        Button(
            text = if (state.canAdd) "Add habit" else "8 habits is the maximum",
            onClick = onAddHabit,
            enabled = state.canAdd,
            variant = ButtonVariant.Secondary,
            leadingIcon = ResurgeIcons.Plus,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HabitRow(
    item: HabitItem,
    today: LocalDate,
    onToggleDay: (String, LocalDate) -> Unit,
    onOpenHabit: (String) -> Unit,
) {
    val tint = HabitVisuals.color(item.habit.color)
    Card(Modifier.fillMaxWidth(), onClick = { onOpenHabit(item.habit.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(HabitVisuals.icon(item.habit.icon), tint = tint, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.habit.name, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Best ${item.stats.best}", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                    Spacer(Modifier.width(10.dp))
                    Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.water, size = 13.dp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        if (item.stats.freezesLeftThisWeek > 0) "Freeze ready" else "Freeze used",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textMuted,
                    )
                }
            }
            Icon(
                if (item.stats.current > 0) ResurgeIcons.FlameFilled else ResurgeIcons.Flame,
                contentDescription = null,
                tint = if (item.stats.current > 0) Resurge.colors.energy else Resurge.colors.textSubtle,
                size = 22.dp,
            )
            Spacer(Modifier.width(4.dp))
            Text("${item.stats.current}", style = Resurge.type.title)
        }
        Spacer(Modifier.height(16.dp))
        WeekStrip(item, today, tint, onToggleDay)
    }
}

/** Last 7 habit days as flames; today and yesterday (grace) can be tapped to flip. */
@Composable
private fun WeekStrip(item: HabitItem, today: LocalDate, tint: Color, onToggleDay: (String, LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (6 downTo 0).forEach { back ->
            val date = today.minusDays(back.toLong())
            val dayState = item.stats.days[date] ?: DayState.NONE
            val editable = back <= 1
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val content = @Composable { DayFlame(dayState, tint) }
                if (editable) {
                    UnstyledButton(
                        onClick = { onToggleDay(item.habit.id, date) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(Resurge.shape.pill)
                            .semantics { contentDescription = "${if (back == 0) "Today" else "Yesterday"}: ${dayState.label}. Tap to change" },
                        indication = LocalIndication.current,
                    ) { content() }
                } else {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { content() }
                }
                Text(
                    if (back == 0) "Today" else date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption,
                    color = if (back == 0) Resurge.colors.text else Resurge.colors.textSubtle,
                )
            }
        }
    }
}

@Composable
internal fun DayFlame(state: DayState, tint: Color, size: Int = 22) {
    when (state) {
        DayState.DONE -> Icon(ResurgeIcons.FlameFilled, contentDescription = null, tint = tint, size = size.dp)
        DayState.FROZEN -> Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.water, size = size.dp)
        DayState.MISSED -> Icon(ResurgeIcons.Flame, contentDescription = null, tint = Resurge.colors.textSubtle, size = size.dp)
        DayState.PENDING -> Icon(ResurgeIcons.Flame, contentDescription = null, tint = Resurge.colors.border, size = size.dp)
        DayState.NONE -> Box(Modifier.size(4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
    }
}

private val DayState.label: String
    get() = when (this) {
        DayState.DONE -> "done"
        DayState.MISSED -> "not done"
        DayState.FROZEN -> "freeze used"
        DayState.PENDING -> "not logged"
        DayState.NONE -> "before habit started"
    }
