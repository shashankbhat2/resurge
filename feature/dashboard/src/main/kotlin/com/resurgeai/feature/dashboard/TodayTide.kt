package com.resurgeai.feature.dashboard

import com.resurgeai.core.designsystem.components.ProgressFlame
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
import com.resurgeai.core.designsystem.components.TideEasing
import com.resurgeai.core.designsystem.components.TideRing
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.InsightKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// --- Tide ---

/**
 * Hero card: today's tide ring with what's left and what's done, and, when there are habits, a
 * footer with the streak and the weekly goal.
 */
@Composable
internal fun TideCard(
    tide: Tide,
    modifier: Modifier = Modifier,
    rise: Int? = null,
    ascent: Ascent? = null,
    onOpenDetail: (TideDetail) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface),
    ) {
        TideRow(tide)
        if (rise != null && ascent != null) {
            Divider()
            TideFooter(rise, ascent, onOpenDetail)
        }
    }
}

@Composable
private fun TideRow(tide: Tide) {
    val percent by animateIntAsState(tide.percent, tween(TIDE_RISE_MS, easing = TideEasing), label = "percent")
    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        TideRing(
            done = tide.done,
            total = tide.total,
            modifier = Modifier
                .size(140.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Tide ${tide.percent} percent, ${tide.done} of ${tide.total} done"
                },
            strokeWidth = 12.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(percent.toString(), style = Resurge.type.tide.copy(fontSize = Resurge.type.tide.fontSize * 0.82f, lineHeight = Resurge.type.tide.lineHeight * 0.82f))
                    Text("%", style = Resurge.type.brand, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    when {
                        tide.high -> "High tide"
                        tide.left == 1 -> "1 to high tide"
                        else -> "${tide.left} to high tide"
                    },
                    style = Resurge.type.caption,
                    color = if (tide.high) Resurge.colors.brandInk else Resurge.colors.textSubtle,
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Overline("Today's tide")
            // What's left first: those are the taps that move the ring.
            val ordered = tide.segments.filterNot { it.done } + tide.segments.filter { it.done }
            ordered.take(LEGEND_ROWS).forEach { TideLegendRow(it) }
            if (ordered.size > LEGEND_ROWS) {
                Text("+${ordered.size - LEGEND_ROWS} more", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
    }
}

@Composable
private fun TideLegendRow(segment: TideSegment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (segment.done) Resurge.colors.accent else Resurge.colors.ebb),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            segment.label,
            style = Resurge.type.label,
            color = if (segment.done) Resurge.colors.text else Resurge.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val LEGEND_ROWS = 5

/**
 * The tide card's footer: two equal cells, end to end, split by a hairline. Left is the streak
 * (days in a row with every habit done); right is the weekly goal (full days this week).
 */
@Composable
private fun TideFooter(streak: Int, week: Ascent, onOpen: (TideDetail) -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        StatCell(
            icon = ResurgeIcons.HabitStreak,
            iconTint = if (streak > 0) Resurge.colors.onGlow else Resurge.colors.textMuted,
            iconBackground = if (streak > 0) Resurge.colors.glow else Resurge.colors.surfaceMuted,
            value = when (streak) {
                0 -> "Start today"
                1 -> "1 day"
                else -> "$streak days"
            },
            label = "Habit streak",
            description = if (streak > 0) "Streak: $streak days in a row with every habit done" else "No streak yet. Do every habit today to start one.",
            onClick = { onOpen(TideDetail.STREAK) },
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        val days = week.fullDaysThisWeek.coerceAtMost(TideMath.STRONG_WEEK_DAYS)
        StatCell(
            icon = null,
            iconTint = Resurge.colors.glow,
            iconBackground = if (week.metThisWeek) Resurge.colors.glowTint else Resurge.colors.surfaceMuted,
            // Fills from the bottom as full days add up toward the goal.
            iconContent = { ProgressFlame(days / TideMath.STRONG_WEEK_DAYS.toFloat()) },
            value = "$days/${TideMath.STRONG_WEEK_DAYS} days",
            label = when {
                week.metThisWeek -> "Weekly goal met"
                week.weeksWon > 0 -> "Weekly goal · ${week.weeksWon} won"
                else -> "Weekly goal"
            },
            description = "Weekly goal: $days of ${TideMath.STRONG_WEEK_DAYS} days with every habit done this week" +
                if (week.weeksWon > 0) ". ${week.weeksWon} weeks won so far." else ".",
            onClick = { onOpen(TideDetail.WEEK) },
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Habits ---

/** Most habits shown on Today; the rest are a tap away on the Habits tab (all still count toward the tide). */
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

@Composable
private fun habitFill(key: String): Color =
    if (key in CATEGORY_KEYS) HabitVisuals.color(key) else Resurge.colors.brandInk

private val CATEGORY_KEYS = setOf("sleep", "activity", "energy", "water", "heart")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitTile(item: TodayHabit, onToggle: (String) -> Unit, onOpen: (String) -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val fill = habitFill(item.habit.color)
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
        Text("Each habit becomes a share of today's tide. Small ones count the same as big ones.", style = Resurge.type.body, color = Resurge.colors.textMuted)
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

internal fun insightLabel(kind: InsightKind): String = when (kind) {
    InsightKind.WEEKLY -> "Weekly review"
    InsightKind.SLEEP -> "Sleep"
    InsightKind.ACTIVITY -> "Activity"
}

// --- High tide ---

/** The day's one celebration, as a bottom sheet. */
@Composable
internal fun HighTideDialog(state: DashboardUiState, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    BottomSheetDialog(onDismiss) { HighTideSheet(state, onDismiss) }
}

@Composable
internal fun HighTideSheet(state: DashboardUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val tide = state.tide
    val day = state.habitDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
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
            Overline("$day · ${tide.done} of ${tide.total}", color = Resurge.colors.glowInk)
            Text("High tide", style = Resurge.type.brandLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.riseCount > 0) RiseBadge(state.riseCount)
                Text(
                    "${state.ascent.fullDaysThisWeek.coerceAtMost(TideMath.STRONG_WEEK_DAYS)} of ${TideMath.STRONG_WEEK_DAYS} full days this week",
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
