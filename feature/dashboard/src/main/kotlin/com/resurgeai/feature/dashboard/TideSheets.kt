package com.resurgeai.feature.dashboard

import com.resurgeai.core.designsystem.components.Overline
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStreaks
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Which part of the tide card's footer was tapped. */
internal enum class TideDetail { STREAK, WEEK }

/** Bottom sheet over a scrim: tapping outside or the sheet's own button closes it. */
@Composable
internal fun BottomSheetDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.clickable(interactionSource = null, indication = null) {}) { content() }
        }
    }
}

@Composable
internal fun TideDetailDialog(detail: TideDetail, state: DashboardUiState, onDismiss: () -> Unit) {
    BottomSheetDialog(onDismiss) {
        when (detail) {
            TideDetail.STREAK -> StreakSheet(state.habits.orEmpty(), state.habitDay, state.profile?.habitSettings ?: HabitSettings(), onDismiss)
            TideDetail.WEEK -> WeekSheet(state.habits.orEmpty(), state.habitDay, onDismiss)
        }
    }
}

// --- Streak ---

/** Current and best streak, the last five weeks as a calendar, and how the streak works. */
@Composable
internal fun StreakSheet(habits: List<TodayHabit>, today: LocalDate, settings: HabitSettings, onDone: () -> Unit) {
    val streak = remember(habits, today) { TideMath.riseCount(habits, today) }
    val best = remember(habits, today) { TideMath.bestStreak(habits, today) }
    val reset = settings.resetTime.format(clockFormatter())
    SheetColumn {
        SheetHeader(
            icon = ResurgeIcons.HabitStreak,
            iconTint = if (streak > 0) Resurge.colors.onGlow else Resurge.colors.textMuted,
            iconBackground = if (streak > 0) Resurge.colors.glow else Resurge.colors.surfaceMuted,
            title = days(streak),
            subtitle = "Habit streak · days in a row with every habit done",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Best ${days(best)}")
            if (streak > 0 && streak >= best) Pill("Your best yet", glow = true)
        }
        val weeks = weeksShown(habits, today, max = 5)
        Overline(if (weeks == 1) "This week" else "Last $weeks weeks")
        StreakCalendar(habits, today, weeks)
        Legend()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rule("Today never breaks your streak. You have until $reset to finish today's habits.")
            Rule("Miss one day in a week and that habit's freeze covers it, so your streak carries on.")
        }
        Button("Done", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StreakCalendar(habits: List<TodayHabit>, today: LocalDate, weeks: Int) {
    val firstWeek = HabitStreaks.weekStart(today).minusWeeks(weeks - 1L)
    val statuses = remember(habits, today, weeks) {
        (0L until weeks * 7L).map { firstWeek.plusDays(it) }.map { it to TideMath.dayStatus(habits, it, today) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L until 7L).forEach { i ->
                Text(
                    firstWeek.plusDays(i).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption,
                    color = Resurge.colors.textSubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        statuses.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { (date, status) -> DayCell(date, status, isToday = date == today, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, status: FullDay, isToday: Boolean, modifier: Modifier) {
    val shape = Resurge.shape.sm
    val fill = when (status) {
        FullDay.FULL -> Resurge.colors.accent
        FullDay.FORGIVEN -> Resurge.colors.frost
        FullDay.MISSED -> Resurge.colors.ebb
        FullDay.OPEN, FullDay.NONE -> Color.Transparent
    }
    val label = when (status) {
        FullDay.FULL -> "every habit done"
        FullDay.FORGIVEN -> "covered by a freeze"
        FullDay.MISSED -> "missed"
        FullDay.OPEN -> "still open"
        FullDay.NONE -> "no habits yet"
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(fill)
            .then(if (status == FullDay.OPEN || isToday) Modifier.border(1.5.dp, if (isToday) Resurge.colors.text else Resurge.colors.textSubtle, shape) else Modifier)
            .semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))}: $label" },
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            FullDay.FULL -> Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = 16.dp)
            FullDay.FORGIVEN -> Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.frostInk, size = 16.dp)
            else -> Text(
                date.dayOfMonth.toString(),
                style = Resurge.type.caption,
                color = if (status == FullDay.NONE) Resurge.colors.textSubtle.copy(alpha = 0.5f) else Resurge.colors.textMuted,
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(Resurge.colors.accent, "All done")
        LegendItem(Resurge.colors.frost, "Freeze")
        LegendItem(Resurge.colors.ebb, "Missed")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

// --- Weekly goal ---

/** This week toward the goal, how many more days win it, and the last six weeks. */
@Composable
internal fun WeekSheet(habits: List<TodayHabit>, today: LocalDate, onDone: () -> Unit) {
    val weekStart = HabitStreaks.weekStart(today)
    val ascent = remember(habits, today) { TideMath.ascent(habits, today) }
    val weeks = remember(habits, today) { TideMath.recentWeeks(habits, today, count = weeksShown(habits, today, max = 6)) }
    val goal = TideMath.STRONG_WEEK_DAYS
    val full = ascent.fullDaysThisWeek
    // Days still to play this week, today included unless it's already full.
    val todayFull = TideMath.fullDay(habits, today)
    val daysLeft = (6 - (today.toEpochDay() - weekStart.toEpochDay()).toInt()) + if (todayFull) 0 else 1
    val needed = (goal - full).coerceAtLeast(0)
    SheetColumn {
        SheetHeader(
            icon = null,
            iconTint = Resurge.colors.onGlow,
            iconBackground = Resurge.colors.glow,
            iconContent = { WeeklyFlameIcon(full.coerceAtMost(goal) / goal.toFloat(), size = 28.dp) },
            title = "${full.coerceAtMost(goal)} of $goal days",
            subtitle = "Weekly goal · $goal full days, Sun to Sat",
        )
        Text(
            when {
                ascent.metThisWeek -> "You've won this week. Every extra full day still adds to your streak."
                needed <= daysLeft -> "${if (needed == 1) "1 more full day" else "$needed more full days"} wins this week. $daysLeft ${if (daysLeft == 1) "day" else "days"} left, today included."
                else -> "This week is out of reach, but every full day still grows your streak. A new week starts Sunday."
            },
            style = Resurge.type.body,
        )
        Overline("This week")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L until 7L).forEach { i ->
                val date = weekStart.plusDays(i)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayCell(date, TideMath.dayStatus(habits, date, today), isToday = date == today, Modifier.fillMaxWidth())
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = Resurge.type.caption,
                        color = if (date == today) Resurge.colors.text else Resurge.colors.textSubtle,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline("Recent weeks", Modifier.weight(1f))
            Text(
                if (ascent.weeksWon == 1) "1 week won" else "${ascent.weeksWon} weeks won",
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            weeks.reversed().forEach { WeekRow(it, isCurrent = it.start == weekStart) }
        }
        Button("Done", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WeekRow(week: WeekResult, isCurrent: Boolean) {
    val goal = TideMath.STRONG_WEEK_DAYS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isCurrent) "This week" else week.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
            style = Resurge.type.label,
            color = if (isCurrent) Resurge.colors.text else Resurge.colors.textMuted,
            modifier = Modifier.width(80.dp),
        )
        Row(Modifier.weight(1f).height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(goal) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Resurge.shape.pill)
                        .background(if (i < week.fullDays) (if (week.won) Resurge.colors.glow else Resurge.colors.accent) else Resurge.colors.surfaceMuted),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("${week.fullDays.coerceAtMost(7)}/$goal", style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textMuted, modifier = Modifier.width(28.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (week.won) Icon(ResurgeIcons.FlameFilled, contentDescription = "Won", tint = Resurge.colors.glow, size = 18.dp)
        }
    }
}

// --- Shared ---

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 720.dp)
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun SheetHeader(
    icon: Int?,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    iconContent: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(Resurge.shape.pill).background(iconBackground), contentAlignment = Alignment.Center) {
            when {
                iconContent != null -> iconContent()
                icon != null -> Icon(icon, contentDescription = null, tint = iconTint, size = 28.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Resurge.type.brandLarge)
            Text(subtitle, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
    }
}

@Composable
private fun Pill(text: String, glow: Boolean = false) {
    Text(
        text,
        style = Resurge.type.label,
        color = if (glow) Resurge.colors.glowInk else Resurge.colors.textMuted,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(if (glow) Resurge.colors.glowTint else Resurge.colors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Rule(text: String) {
    Row {
        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(Resurge.shape.pill).background(Resurge.colors.textSubtle))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
}

private fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

/** Weeks to show, up to [max]: never earlier than the week of the first habit. */
private fun weeksShown(habits: List<TodayHabit>, today: LocalDate, max: Int): Int {
    val first = habits.minOfOrNull { it.habit.createdDate } ?: return 1
    val since = (HabitStreaks.weekStart(today).toEpochDay() - HabitStreaks.weekStart(first).toEpochDay()) / 7 + 1
    return since.toInt().coerceIn(1, max)
}
