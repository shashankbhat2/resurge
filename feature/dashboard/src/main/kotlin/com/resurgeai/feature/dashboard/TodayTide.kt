package com.resurgeai.feature.dashboard

import com.resurgeai.core.designsystem.components.OverviewFooterHeight
import com.resurgeai.core.designsystem.components.OverviewBodyHeight
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.StatDivider
import com.resurgeai.core.designsystem.components.StatCell
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.CoachMascot
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.RiseBadge
import com.resurgeai.core.designsystem.components.TIDE_RISE_MS
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.InsightKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// --- Habits ---

/** Most habits shown on Today; the rest are a tap away on the Habits tab (all still count toward the day streak). */
internal const val TODAY_HABITS = 4

/**
 * Today's habits as a two-column grid of one-tap tiles: the first [TODAY_HABITS] in the user's
 * order, then a link to the Habits tab when there are more.
 */
@Composable
internal fun HabitGrid(
    habits: List<TodayHabit>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val shown = habits.take(TODAY_HABITS)
    val cells: List<TodayHabit?> = shown + if (shown.size % 2 == 1) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    val m = Modifier.weight(1f).fillMaxHeight()
                    if (item != null) HabitTile(item, onToggle, onOpen, m) else AddHabitTile(onAdd, m)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (habits.size > TODAY_HABITS) {
            UnstyledButton(
                onClick = onSeeAll,
                modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface),
                indication = LocalIndication.current,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val more = habits.drop(TODAY_HABITS)
                    Text(
                        "See all ${habits.size} habits",
                        style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                        color = Resurge.colors.brandInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${more.count { it.done }} of ${more.size} more done", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                    Spacer(Modifier.width(6.dp))
                    Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitTile(item: TodayHabit, onToggle: (String) -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val fill = HabitVisuals.fill(item.habit.color)
    val bg by animateColorAsState(if (item.done) fill else Resurge.colors.surface, tween(240), label = "tile")
    val content by animateColorAsState(if (item.done) Resurge.colors.onAccent else Resurge.colors.text, tween(240), label = "tileText")
    val shape = Resurge.shape.md
    Column(
        modifier
            .heightIn(min = 112.dp)
            .clip(shape)
            .background(bg)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Checkbox,
                onLongClickLabel = "Open habit",
                onLongClick = { onOpen(item.habit.id) },
                onClick = {
                    haptics.performHapticFeedback(if (item.done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onToggle(item.habit.id)
                },
            )
            .semantics { stateDescription = if (item.done) "Done today" else "Not done yet" }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(HabitVisuals.icon(item.habit.icon), contentDescription = null, tint = if (item.done) content else fill, size = 24.dp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(Resurge.shape.pill)
                    .then(
                        if (item.done) Modifier.background(Resurge.colors.onAccent)
                        else Modifier.border(2.dp, Resurge.colors.border, Resurge.shape.pill),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) Icon(ResurgeIcons.Check, contentDescription = null, tint = fill, size = 18.dp)
            }
        }
        Spacer(Modifier.weight(1f).heightIn(min = 12.dp))
        Text(item.habit.name, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize), color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val meta = if (item.done) content else Resurge.colors.textSubtle
            if (item.stats.current > 0) {
                Icon(ResurgeIcons.HabitStreak, contentDescription = null, tint = meta, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(if (item.stats.current == 1) "1 day" else "${item.stats.current} days", style = Resurge.type.caption, color = meta)
            } else {
                Text("Start today", style = Resurge.type.caption, color = meta)
            }
        }
    }
}

@Composable
private fun AddHabitTile(onAdd: () -> Unit, modifier: Modifier) {
    UnstyledButton(
        onClick = onAdd,
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(Resurge.shape.md)
            .border(1.5.dp, Resurge.colors.border, Resurge.shape.md),
        indication = LocalIndication.current,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.textMuted, size = 22.dp)
            Spacer(Modifier.height(6.dp))
            Text("Add habit", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
    }
}

/** Shown in place of the grid before the first habit exists. */
@Composable
internal fun FirstHabitCard(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .padding(16.dp),
    ) {
        Text("Add your first habit", style = Resurge.type.headline)
        Spacer(Modifier.height(4.dp))
        Text("Each habit counts toward your day streak. Small ones count the same as big ones.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(12.dp))
        Button("Add habit", onClick = onAdd, variant = ButtonVariant.Accent, leadingIcon = ResurgeIcons.Plus)
    }
}

// --- Coach ---

/** The latest coach insight in the persona's voice. */
@Composable
internal fun CoachNote(insight: CoachInsight?, commander: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(if (commander) Resurge.colors.surface else Resurge.colors.accentMuted),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Row(Modifier.padding(16.dp)) {
            CoachMascot(commander = commander, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label ?: listOfNotNull(if (commander) "Commander" else "Friend", insight?.kind?.let(::insightLabel)).joinToString(" · "),
                    style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.caption.fontSize * 1.08f),
                    color = if (commander) Resurge.colors.commander else Resurge.colors.brandInk,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    insight?.body ?: "Your first note arrives after your next workout or night of sleep.",
                    style = Resurge.type.body,
                    color = if (insight != null) Resurge.colors.text else Resurge.colors.textMuted,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The latest insight, laid over the day streak card until it's seen: persona styling like [CoachNote],
 * "New from your coach", the headline and note. Swipe it left or tap ✕ to mark it seen; it slides
 * away and reveals the card underneath. Tapping it opens Insights. [modifier] sizes it to the card.
 */
@Composable
internal fun PinnedInsight(
    insight: CoachInsight,
    commander: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(insight.id) { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    fun dismiss() = scope.launch {
        offset.animateTo(-width * 1.1f, tween(220))
        onDismiss()
    }
    UnstyledButton(
        onClick = onOpen,
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (-offset.value / width).coerceIn(0f, 1f) * 0.6f
            }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Left only; a rightward pull gives a little and springs back.
                    scope.launch { offset.snapTo((offset.value + delta).coerceAtMost(24f)) }
                },
                onDragStopped = { velocity ->
                    if (offset.value < -width * 0.3f || velocity < -1500f) dismiss()
                    else offset.animateTo(0f, spring())
                },
            )
            .shadow(8.dp, Resurge.shape.lg, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
            .clip(Resurge.shape.lg)
            .background(if (commander) Resurge.colors.surface else Resurge.colors.accentMuted),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 20.dp, bottom = 16.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoachMascot(commander = commander, size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "New from your coach · ${insightLabel(insight.kind)}",
                    style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                    color = if (commander) Resurge.colors.commander else Resurge.colors.brandInk,
                    modifier = Modifier.weight(1f),
                )
                UnstyledButton(
                    onClick = { dismiss() },
                    modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).semantics { contentDescription = "Dismiss, mark as seen" },
                    indication = LocalIndication.current,
                ) { Icon(ResurgeIcons.Close, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp) }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(insight.title, style = Resurge.type.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "Swipe left or tap ✕ to see your streak. This note moves to your feed.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                modifier = Modifier.padding(top = 8.dp, end = 12.dp),
            )
        }
    }
}

internal fun insightLabel(kind: InsightKind): String = when (kind) {
    InsightKind.WEEKLY -> "Weekly review"
    InsightKind.SLEEP -> "Sleep"
    InsightKind.ACTIVITY -> "Activity"
}

// --- Day complete ---

/** The day's one celebration, once everything today is done. */
@Composable
internal fun DayCompleteDialog(state: DashboardUiState, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    BottomSheetDialog(onDismiss) { DayCompleteSheet(state, onDismiss) }
}

@Composable
internal fun DayCompleteSheet(state: DashboardUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val streak = state.streak
    val day = streak.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val commander = state.profile?.coachStyle == CoachStyle.COMMANDER
    Column(
        modifier
            .fillMaxWidth()
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Resurge.colors.glowTint)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Overline("$day · ${streak.done} of ${streak.total}", color = Resurge.colors.glowInk)
            Text("Day complete", style = Resurge.type.brandLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (streak.streak.current > 0) RiseBadge(streak.streak.current)
                Text(
                    "${streak.week.metDays} of ${streak.week.target} days this week",
                    style = Resurge.type.label,
                    color = Resurge.colors.textMuted,
                    modifier = Modifier
                        .clip(Resurge.shape.pill)
                        .background(Resurge.colors.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoachNote(
                insight = state.latestInsight,
                commander = commander,
                onClick = onDone,
                label = if (commander) "Debrief" else "Today's note",
            )
            Button("Done", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- Shared ---

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
}
