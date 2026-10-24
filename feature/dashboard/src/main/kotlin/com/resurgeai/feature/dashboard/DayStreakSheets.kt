package com.resurgeai.feature.dashboard

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.dayStatusFill
import com.resurgeai.core.designsystem.components.NumberStepper
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.DayStatus
import com.resurgeai.core.model.DayStreakMath
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.Targets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Which part of the day streak card was tapped. */
internal enum class StreakDetail { STREAK, WEEK, GOALS }

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
internal fun StreakDetailDialog(detail: StreakDetail, state: DayStreakState, settings: HabitSettings, onSaveGoals: (DayGoals) -> Unit, onDismiss: () -> Unit) {
    BottomSheetDialog(onDismiss) {
        when (detail) {
            StreakDetail.STREAK -> StreakSheet(state, settings, onDismiss)
            StreakDetail.WEEK -> WeekSheet(state, onDismiss)
            StreakDetail.GOALS -> GoalsSheet(state, onSave = { onSaveGoals(it); onDismiss() })
        }
    }
}

// --- Streak ---

/** Current and best streak, the last five weeks as a calendar, and how it works. */
@Composable
internal fun StreakSheet(state: DayStreakState, settings: HabitSettings, onDone: () -> Unit) {
    val streak = state.streak
    SheetColumn {
        SheetHeader(
            icon = ResurgeIcons.Check,
            iconTint = if (streak.current > 0) Resurge.colors.bg else Resurge.colors.textMuted,
            iconBackground = if (streak.current > 0) Resurge.colors.up else Resurge.colors.surfaceMuted,
            title = "${days(streak.current)} streak",
            subtitle = "Days in a row with everything done",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Best ${days(streak.best)}")
            Pill(if (streak.freezeLeftThisWeek) "Freeze ready" else "Freeze used this week", glow = streak.freezeLeftThisWeek)
        }
        val weeks = weeksShown(state, max = 5)
        Overline(if (weeks == 1) "This week" else "Last $weeks weeks")
        Calendar(state, weeks)
        Legend()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rule("Today never breaks your streak. You have until ${settings.resetTime.format(clockFormatter())} to finish.")
            Rule("Miss a day and your weekly freeze covers it. The streak carries on, but doesn't grow that day.")
        }
        Button("Done", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Calendar(state: DayStreakState, weeks: Int) {
    val firstWeek = state.week.start.minusWeeks(weeks - 1L)
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
        (0L until weeks.toLong()).forEach { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0L until 7L).forEach { i ->
                    val date = firstWeek.plusWeeks(w).plusDays(i)
                    CalendarDay(date, if (date.isAfter(state.day)) null else state.streak.days[date], date == state.day, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, status: DayStatus?, isToday: Boolean, modifier: Modifier) {
    val shape = Resurge.shape.sm
    val fill = status?.let { dayStatusFill(it) }?.let { if (status == DayStatus.MET) it else it.copy(alpha = 0.28f) } ?: Color.Transparent
    val label = when (status) {
        DayStatus.MET -> "done"
        DayStatus.FORGIVEN -> "covered by the freeze"
        DayStatus.MISSED -> "missed"
        DayStatus.OPEN -> "still open"
        else -> "nothing to count"
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(fill)
            .then(if (status == DayStatus.OPEN || isToday) Modifier.border(if (isToday) 2.dp else 1.dp, if (isToday) Resurge.colors.accent else Resurge.colors.borderStrong, shape) else Modifier)
            .semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))}: $label" },
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            DayStatus.MET -> Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.bg, size = 15.dp)
            DayStatus.FORGIVEN -> Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.mid, size = 15.dp)
            else -> Text(
                date.dayOfMonth.toString(),
                style = Resurge.type.caption,
                color = if (status == null || status == DayStatus.NONE) Resurge.colors.textSubtle.copy(alpha = 0.5f) else Resurge.colors.textMuted,
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem(Resurge.colors.up, "Done")
        LegendItem(Resurge.colors.mid, "Freeze")
        LegendItem(Resurge.colors.down, "Missed")
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

/** This week against the weekly goal, what it still takes, and recent weeks. */
@Composable
internal fun WeekSheet(state: DayStreakState, onDone: () -> Unit) {
    val week = state.week
    val start = week.start
    val daysLeft = (6 - (state.day.toEpochDay() - start.toEpochDay()).toInt()) + if (state.todayMet) 0 else 1
    val needed = (week.target - week.metDays).coerceAtLeast(0)
    val lastDay = start.plusDays(6).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val firstDay = start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    SheetColumn {
        SheetHeader(
            icon = ResurgeIcons.Target,
            iconTint = if (week.reached) Resurge.colors.bg else Resurge.colors.textMuted,
            iconBackground = if (week.reached) Resurge.colors.up else Resurge.colors.surfaceMuted,
            title = "${week.metDays} of ${week.target} days",
            subtitle = "Weekly goal · $firstDay to $lastDay",
        )
        Text(
            when {
                week.reached -> "Goal reached this week."
                needed <= daysLeft -> "${days(needed)} more to go, with ${days(daysLeft)} left."
                else -> "Out of reach this week. Every day still counts for your streak."
            },
            style = Resurge.type.body,
        )
        Overline("This week")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L until 7L).forEach { i ->
                val date = start.plusDays(i)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StripDay(date, if (date.isAfter(state.day)) null else state.streak.days[date], date == state.day, if (state.total == 0) 0f else state.done / state.total.toFloat())
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = Resurge.type.caption,
                        color = if (date == state.day) Resurge.colors.text else Resurge.colors.textSubtle,
                    )
                }
            }
        }
        val weeks = recentWeeks(state, count = weeksShown(state, max = 6))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline("Recent weeks", Modifier.weight(1f))
            Text(if (week.weeksWon == 1) "1 week reached" else "${week.weeksWon} weeks reached", style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            weeks.reversed().forEach { (weekStart, met) -> WeekRow(weekStart, met, week.target, isCurrent = weekStart == start) }
        }
        Button("Done", onClick = onDone, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

/** Met days in each of the last [count] weeks, oldest first, ending with this week. */
private fun recentWeeks(state: DayStreakState, count: Int): List<Pair<LocalDate, Int>> =
    (count - 1 downTo 0).map { back ->
        val start = state.week.start.minusWeeks(back.toLong())
        start to (0L until 7L).count { state.streak.days[start.plusDays(it)] == DayStatus.MET }
    }

@Composable
private fun WeekRow(start: LocalDate, met: Int, target: Int, isCurrent: Boolean) {
    val reached = met >= target
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isCurrent) "This week" else start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())),
            style = Resurge.type.label,
            color = if (isCurrent) Resurge.colors.text else Resurge.colors.textMuted,
            modifier = Modifier.width(80.dp),
        )
        Row(Modifier.weight(1f).height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(target) { i ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(Resurge.shape.pill)
                        .background(if (i < met) (if (reached) Resurge.colors.up else Resurge.colors.text) else Resurge.colors.surfaceMuted),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("${met.coerceAtMost(7)}/$target", style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textMuted, modifier = Modifier.width(28.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (reached) Icon(ResurgeIcons.Check, contentDescription = "Reached", tint = Resurge.colors.up, size = 18.dp)
        }
    }
}

// --- What counts ---

/**
 * The day streak's settings: which goals a day needs (each habit, sleep, steps, meals, water) and
 * the weekly goal. Changes apply from today; days already settled keep their result.
 */
@Composable
internal fun GoalsSheet(state: DayStreakState, onSave: (DayGoals) -> Unit) {
    var goals by remember { mutableStateOf(state.goals) }
    val t: Targets = state.targets
    val chosen = state.habits.count { it.habit.id !in goals.excludedHabits } +
        (if (goals.sleep && state.health) 1 else 0) + (if (goals.steps && state.health) 1 else 0) +
        (if (goals.meals > 0) 1 else 0) + (if (goals.water) 1 else 0)
    SheetColumn {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Day streak", style = Resurge.type.brandLarge)
            Text("A day counts when everything you pick here is done.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        }
        if (state.habits.isNotEmpty()) {
            Overline("Habits")
            Group {
                state.habits.forEachIndexed { i, h ->
                    if (i > 0) Divider(Modifier.padding(start = 50.dp))
                    ToggleRow(
                        icon = HabitVisuals.icon(h.habit.icon),
                        tint = HabitVisuals.fill(h.habit.color),
                        label = h.habit.name,
                        hint = null,
                        checked = h.habit.id !in goals.excludedHabits,
                        onChange = { on -> goals = goals.copy(excludedHabits = if (on) goals.excludedHabits - h.habit.id else goals.excludedHabits + h.habit.id) },
                    )
                }
            }
        }
        Overline("Daily targets")
        Group {
            ToggleRow(ResurgeIcons.Bed, Resurge.colors.textMuted, "Sleep", if (state.health) "${Targets.formatSleep(t.sleepMinutes)} a night" else "Needs Health Connect", goals.sleep && state.health, enabled = state.health) { goals = goals.copy(sleep = it) }
            Divider(Modifier.padding(start = 50.dp))
            ToggleRow(ResurgeIcons.Footprints, Resurge.colors.textMuted, "Steps", if (state.health) "${"%,d".format(t.steps)} a day" else "Needs Health Connect", goals.steps && state.health, enabled = state.health) { goals = goals.copy(steps = it) }
            Divider(Modifier.padding(start = 50.dp))
            ToggleRow(ResurgeIcons.Droplet, Resurge.colors.textMuted, "Water", "${"%,d".format(t.waterMl)} ml a day", goals.water) { goals = goals.copy(water = it) }
            Divider(Modifier.padding(start = 50.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ResurgeIcons.Bowl, contentDescription = null, tint = Resurge.colors.textMuted, size = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Meals logged", style = Resurge.type.body, modifier = Modifier.weight(1f))
                }
                NumberStepper(
                    value = goals.meals,
                    onValueChange = { goals = goals.copy(meals = it) },
                    step = 1,
                    range = DayGoals.MEALS_RANGE,
                    unit = if (goals.meals == 0) "not counted" else if (goals.meals == 1) "meal a day" else "meals a day",
                    format = { if (it == 0) "Off" else "$it" },
                )
            }
        }
        Text("Sleep, steps and water targets are in Settings.", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 4.dp))
        Overline("Weekly goal")
        Group {
            Column(Modifier.padding(16.dp)) {
                NumberStepper(
                    value = goals.weeklyDays,
                    onValueChange = { goals = goals.copy(weeklyDays = it) },
                    step = 1,
                    range = DayGoals.WEEKLY_RANGE,
                    unit = if (goals.weeklyDays == 1) "day a week" else "days a week",
                    format = { "$it" },
                )
            }
        }
        if (chosen == 0) Text("Pick at least one goal.", style = Resurge.type.caption, color = Resurge.colors.danger)
        Button("Save", onClick = { onSave(goals) }, enabled = chosen > 0, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToggleRow(icon: Int, tint: Color, label: String, hint: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, size = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Resurge.type.body)
            hint?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
        }
        Switch(checked, { if (enabled) onChange(it) }, accessibilityLabel = label)
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted), content = content)
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
        color = if (glow) Resurge.colors.up else Resurge.colors.textMuted,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted)
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

/** Weeks to show, up to [max]: back to the first day with anything to count. */
private fun weeksShown(state: DayStreakState, max: Int): Int {
    val first = state.streak.days.filterValues { it != DayStatus.NONE }.keys.minOrNull() ?: return 1
    val since = (state.week.start.toEpochDay() - DayStreakMath.weekStart(first).toEpochDay()) / 7 + 1
    return since.toInt().coerceIn(1, max)
}
