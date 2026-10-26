package com.resurgeai.feature.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.CoachBadge
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.dayStatusFill
import com.resurgeai.core.designsystem.components.signalColor
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.DayStatus
import com.resurgeai.core.model.DayStreakMath
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.PaceSignal
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The day streak and this week in one card: the streak as one big number with the best run beside
 * it, then this week as seven squares (met in up, a freeze in mid, missed in down, today outlined in
 * the accent) with the count against the weekly target. Each half opens its own sheet.
 */
@Composable
internal fun StreakWeekCard(state: DayStreakState, goal: MajorGoal?, today: LocalDate, onOpenStreak: () -> Unit, onOpenWeek: () -> Unit) {
    val current = state.streak.current
    val week = state.week
    val weekNumber = goal?.let { ChronoUnit.WEEKS.between(DayStreakMath.weekStart(it.startDate), week.start).toInt() + 1 }
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
        UnstyledButton(
            onClick = onOpenStreak,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Day streak: ${days(current)}. Open" },
            indication = LocalIndication.current,
            contentAlignment = Alignment.TopStart,
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("$current", style = Resurge.type.tide.copy(fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = (-2.5).sp, fontFeatureSettings = "tnum"))
                    Text(
                        if (current == 0) "Finish today to start a streak" else "day streak",
                        style = Resurge.type.body,
                        color = Resurge.colors.textMuted,
                    )
                }
                if (state.streak.best > 0) {
                    Text(
                        if (current > 0 && current >= state.streak.best) "Your best yet" else "Best ${state.streak.best}",
                        style = Resurge.type.label,
                        color = Resurge.colors.textSubtle,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Resurge.colors.border))
        UnstyledButton(
            onClick = onOpenWeek,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "This week: ${week.metDays} of ${week.target} good days. Open" },
            indication = LocalIndication.current,
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (weekNumber != null && weekNumber >= 1) "Week $weekNumber" else "This week",
                        style = Resurge.type.headline,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${week.metDays}", style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"))
                    Text(" / ${week.target} good days", style = Resurge.type.label, color = Resurge.colors.textSubtle)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 7).forEach { i ->
                        val date = week.start.plusDays(i.toLong())
                        // Days before the goal began don't count for or against it.
                        val status = if (goal != null && date.isBefore(goal.startDate)) DayStatus.NONE else state.streak.days[date]
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            DaySquare(status, isToday = date == today, future = date.isAfter(today))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                                style = Resurge.type.caption,
                                color = if (date == today) Resurge.colors.accent else Resurge.colors.textSubtle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySquare(status: DayStatus?, isToday: Boolean, future: Boolean) {
    val shape = RoundedCornerShape(8.dp)
    // A met day is the one solid colour; a freeze or a miss is a quiet tint of its signal.
    val fill = status?.let { dayStatusFill(it) }?.let { if (status == DayStatus.MET) it else it.copy(alpha = 0.28f) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(
                when {
                    fill != null -> Modifier.background(fill)
                    isToday -> Modifier.border(2.dp, Resurge.colors.accent, shape)
                    future || status == DayStatus.OPEN -> Modifier.border(1.dp, Resurge.colors.borderStrong, shape)
                    else -> Modifier.background(Resurge.colors.surfaceMuted)
                },
            ),
    )
}

/**
 * The major goal: what it is, how it's going (weight against the planned pace, or weeks won), and
 * the timeline as bars, a week each (a month each past 16 weeks). Won weeks are up, lost weeks down,
 * this one the accent.
 */
@Composable
internal fun GoalCard(
    goal: MajorGoal,
    state: DayStreakState,
    weight: WeightGlance?,
    units: UnitSystem,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    val outcomes = weekOutcomes(goal, state, today)
    val won = outcomes.count { it == WeekOutcome.WON }
    val played = outcomes.count { it == WeekOutcome.WON || it == WeekOutcome.LOST }
    MonoCard(onClick = onOpen, description = "Goal: ${goalTitle(goal, units)}. Open") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(goalTitle(goal, units), style = Resurge.type.headline, modifier = Modifier.weight(1f))
            goalStatus(goal, weight, units, today)?.let { (text, signal) ->
                Text(text, style = Resurge.type.label, color = signal?.let { signalColor(it) } ?: Resurge.colors.textMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        GoalBars(goal, outcomes)
        Spacer(Modifier.height(10.dp))
        Text(
            listOfNotNull(
                "$won of ${goal.weeks} weeks won".takeIf { played > 0 } ?: "Week 1 of ${goal.weeks}",
                weight?.let { "${formatKg(it.kg, units)} now" }.takeIf { goal.type.byWeight },
                "ends ${goal.endDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}",
            ).joinToString(" · "),
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
        )
    }
}

internal enum class WeekOutcome { WON, LOST, CURRENT, FUTURE }

/** Each week of the goal: won or lost (from the saved days), this week, or still to come. */
internal fun weekOutcomes(goal: MajorGoal, state: DayStreakState, today: LocalDate): List<WeekOutcome> {
    val first = DayStreakMath.weekStart(goal.startDate)
    val thisWeek = DayStreakMath.weekStart(today)
    return (0 until goal.weeks).map { i ->
        val start = first.plusWeeks(i.toLong())
        when {
            start.isAfter(thisWeek) -> WeekOutcome.FUTURE
            start == thisWeek -> WeekOutcome.CURRENT
            else -> {
                val met = (0L until 7L).count { state.streak.days[start.plusDays(it)] == DayStatus.MET }
                if (met >= state.goals.weeklyDays) WeekOutcome.WON else WeekOutcome.LOST
            }
        }
    }
}

@Composable
private fun GoalBars(goal: MajorGoal, outcomes: List<WeekOutcome>) {
    val byMonth = goal.weeks > 16
    // Months: a month is played once all its weeks are; it's "won" when most of its weeks were.
    val bars: List<Color> = if (!byMonth) outcomes.map { outcomeColor(it) } else {
        val months = (goal.weeks * 12 / 52.0).roundToInt().coerceAtLeast(1)
        (0 until months).map { m ->
            val from = (m * goal.weeks / months.toDouble()).roundToInt()
            val to = ((m + 1) * goal.weeks / months.toDouble()).roundToInt().coerceAtMost(outcomes.size)
            val chunk = outcomes.subList(from, to.coerceAtLeast(from))
            when {
                WeekOutcome.CURRENT in chunk -> Resurge.colors.accent
                chunk.all { it == WeekOutcome.FUTURE } -> Resurge.colors.borderStrong
                chunk.count { it == WeekOutcome.WON } * 2 >= chunk.count { it != WeekOutcome.FUTURE } -> Resurge.colors.up
                else -> Resurge.colors.down
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(if (bars.size > 12) 3.dp else 4.dp)) {
        bars.forEach { color ->
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}

@Composable
private fun outcomeColor(o: WeekOutcome): Color = when (o) {
    WeekOutcome.WON -> Resurge.colors.up
    WeekOutcome.LOST -> Resurge.colors.down
    WeekOutcome.CURRENT -> Resurge.colors.accent
    WeekOutcome.FUTURE -> Resurge.colors.borderStrong
}

private fun goalTitle(goal: MajorGoal, units: UnitSystem): String = when (goal.type) {
    TrainingGoal.LOSE_FAT -> "Lose ${formatKg(goal.changeKg ?: 0.0, units)}"
    TrainingGoal.BUILD_MUSCLE -> "Gain ${formatKg(goal.changeKg ?: 0.0, units)}"
    TrainingGoal.RUN_FURTHER -> "Run ${MajorGoal.RACES.firstOrNull { it.first == goal.raceKm }?.second ?: "further"}"
    TrainingGoal.GET_IN_SHAPE -> "${MajorGoal.durationLabel(goal.weeks).replaceFirstChar { it.uppercase() }} of showing up"
}

/**
 * For weight goals: kilos moved so far and whether that's on the planned pace (within 10%: up,
 * at least half: mid, else down). Null in the first week or without a weigh-in.
 */
private fun goalStatus(goal: MajorGoal, weight: WeightGlance?, units: UnitSystem, today: LocalDate): Pair<String, PaceSignal?>? {
    if (!goal.type.byWeight) return null
    val start = goal.startKg ?: return null
    val now = weight?.kg ?: return null
    val pace = goal.paceKgPerWeek ?: return null
    val losing = (goal.targetKg ?: start) < start
    val moved = if (losing) start - now else now - start
    val weeks = ChronoUnit.DAYS.between(goal.startDate, today) / 7.0
    val sign = if (now < start) "−" else "+"
    val change = "$sign${formatKg(abs(now - start), units)}"
    if (weeks < 1) return change to null
    val expected = pace * weeks
    val signal = when {
        moved >= expected * 0.9 -> PaceSignal.UP
        moved >= expected * 0.5 -> PaceSignal.MID
        else -> PaceSignal.DOWN
    }
    val note = when (signal) {
        PaceSignal.UP -> "on pace"
        PaceSignal.MID -> "a bit behind"
        PaceSignal.DOWN -> "behind"
    }
    return "$change · $note" to signal
}

internal fun formatKg(kg: Double, units: UnitSystem): String = when (units) {
    UnitSystem.METRIC -> String.format(Locale.getDefault(), "%.1f kg", kg)
    UnitSystem.IMPERIAL -> "${(kg / WeightMath.KG_PER_LB).roundToInt()} lb"
}

/** One thing on today's list: the planned session or a goal of the day. */
private class ListEntry(
    val label: String,
    val detail: String?,
    /** True done, false missed, null still open (or not something you tick, like a rest day). */
    val done: Boolean?,
    val isSession: Boolean,
    /** Ticks a habit, or starts the session; null when the entry isn't tapped. */
    val onClick: (() -> Unit)?,
    val actionLabel: String? = null,
)

/**
 * Today's list: the planned session first (with Start), then each goal of the day. Habits check off
 * with a tap. [compact] folds it into a row of boxes that scrolls sideways; the toggle sits by the
 * count. Footer links open all habits and the choice of what counts.
 */
@Composable
internal fun TodayChecklist(
    state: DayStreakState,
    title: String,
    session: PlanDay?,
    sessionDone: Boolean,
    compact: Boolean,
    onCompactChange: (Boolean) -> Unit,
    onStartSession: (PlanDay) -> Unit,
    onToggleHabit: (String) -> Unit,
    onOpenHabits: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val entries = buildList {
        session?.let { day ->
            val canStart = !day.rest && !sessionDone
            add(
                ListEntry(
                    label = if (day.rest) "Rest day" else day.title,
                    detail = if (day.rest) day.note else listOfNotNull(
                        day.exercises.size.takeIf { it > 0 }?.let { "$it exercises" },
                        day.durationMin?.let { "$it min" },
                    ).joinToString(" · ").ifEmpty { day.focus },
                    done = if (!day.rest && sessionDone) true else null,
                    isSession = true,
                    onClick = if (canStart) ({ onStartSession(day) }) else null,
                    actionLabel = "Start".takeIf { canStart },
                ),
            )
        }
        state.today.forEach { item ->
            val habitId = item.key.removePrefix("habit_").takeIf { item.key.startsWith("habit_") }
            add(ListEntry(item.label, null, item.done, isSession = false, onClick = habitId?.let { id -> { onToggleHabit(id) } }))
        }
    }
    // A rest day isn't something to tick, so it's left out of the count; a session to do is in it.
    val counted = entries.filter { !(it.isSession && it.done == null && it.actionLabel == null) }
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(
            if (counted.isNotEmpty()) "$title · ${counted.count { it.done == true }} of ${counted.size}" else title,
            color = Resurge.colors.textSubtle,
            modifier = Modifier.weight(1f),
        )
        if (entries.isNotEmpty()) {
            SmallIconButton(
                icon = if (compact) ResurgeIcons.ViewList else ResurgeIcons.ViewBoxes,
                description = if (compact) "Show as a list" else "Show as boxes",
                onClick = { onCompactChange(!compact) },
            )
        }
        SmallIconButton(ResurgeIcons.Settings, "Choose what counts", onOpenSettings)
    }
    if (entries.isEmpty()) {
        Text("Choose what a day needs to count toward your streak.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Button("Choose goals", onClick = onOpenSettings, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        return
    }
    val hasHabits = state.today.any { it.key.startsWith("habit_") }
    if (compact) {
        // One card holding a row that scrolls inside it, padded at both ends so no box is cut at
        // the page edge; done boxes go to the end so what's left is in view first.
        Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
            LazyRow(
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.sortedBy { it.done == true }) { EntryBox(it) }
            }
            if (hasHabits) AllHabitsLink(onOpenHabits, Modifier.padding(start = 2.dp, bottom = 4.dp))
        }
    } else {
        entries.forEach { if (it.isSession) SessionRow(it) else EntryRow(it) }
        if (hasHabits) AllHabitsLink(onOpenHabits)
    }
}

@Composable
private fun AllHabitsLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier.clip(Resurge.shape.sm),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        indication = LocalIndication.current,
    ) { Text("All habits", style = Resurge.type.label, color = Resurge.colors.textMuted) }
}

@Composable
private fun SmallIconButton(icon: Int, description: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).semantics { contentDescription = description },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = Resurge.colors.textSubtle, size = 20.dp) }
}

/** Today's planned session as a card: what and how long, Start, or a check once it's logged. */
@Composable
private fun SessionRow(entry: ListEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckDot(entry.done)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = Resurge.type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            entry.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (entry.onClick != null && entry.actionLabel != null) {
            Spacer(Modifier.width(10.dp))
            UnstyledButton(
                onClick = entry.onClick,
                role = Role.Button,
                modifier = Modifier.height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.strong),
                contentPadding = PaddingValues(horizontal = 16.dp),
                indication = LocalIndication.current,
            ) { Text(entry.actionLabel, style = Resurge.type.label, color = Resurge.colors.onStrong) }
        }
    }
}

@Composable
private fun EntryRow(entry: ListEntry) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckDot(entry.done)
            Spacer(Modifier.width(14.dp))
            Text(
                entry.label,
                style = Resurge.type.body,
                color = if (entry.done == true) Resurge.colors.textSubtle else Resurge.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (entry.onClick != null) {
        UnstyledButton(
            onClick = entry.onClick,
            role = Role.Checkbox,
            modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).semantics { contentDescription = "${entry.label}, ${if (entry.done == true) "done" else "not done"}" },
            indication = LocalIndication.current,
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    } else content()
}

/**
 * The compact form: a box per entry, its check at the top and its label at the bottom with room
 * for two lines. The session still to do is outlined in the accent and says Start.
 */
@Composable
private fun EntryBox(entry: ListEntry) {
    val shape = Resurge.shape.sm
    val pending = entry.isSession && entry.actionLabel != null
    val box = Modifier
        .width(112.dp)
        .height(96.dp)
        .clip(shape)
        .background(Resurge.colors.surfaceMuted)
        .then(if (pending) Modifier.border(1.5.dp, Resurge.colors.accent, shape) else Modifier)
    val inner: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            CheckDot(entry.done, size = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.label,
                    style = Resurge.type.caption.copy(
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = if (entry.isSession) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (entry.done == true) Resurge.colors.textSubtle else Resurge.colors.text,
                    maxLines = if (pending) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pending) Text("Start →", style = Resurge.type.caption, color = Resurge.colors.accent, maxLines = 1)
            }
        }
    }
    if (entry.onClick != null) {
        UnstyledButton(
            onClick = entry.onClick,
            role = if (entry.isSession) Role.Button else Role.Checkbox,
            modifier = box.semantics { contentDescription = "${entry.label}, ${if (entry.done == true) "done" else if (pending) "tap to start" else "not done"}" },
            indication = LocalIndication.current,
            contentAlignment = Alignment.TopStart,
        ) { inner() }
    } else {
        Box(box, contentAlignment = Alignment.TopStart) { inner() }
    }
}

/** Done: an up-filled circle with a check. Missed: a down outline. Open: a grey outline. */
@Composable
private fun CheckDot(done: Boolean?, size: Dp = 26.dp) {
    val shape = Resurge.shape.pill
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .then(
                when (done) {
                    true -> Modifier.background(Resurge.colors.up)
                    false -> Modifier.border(2.dp, Resurge.colors.down, shape)
                    null -> Modifier.border(2.dp, Resurge.colors.borderStrong, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done == true) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.bg, size = size * 0.58f)
    }
}

/**
 * The newest coach note, until it's seen: the coach's mark, what it's about, the headline and
 * note, up to two numbers behind it, then Open and Got it.
 */
@Composable
internal fun CoachCard(insight: CoachInsight, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachBadge(size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(insightLabel(insight.kind), style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(Resurge.colors.text).semantics { contentDescription = "New" })
        }
        Text(insight.title, style = Resurge.type.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (insight.highlights.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                insight.highlights.take(2).forEach { h ->
                    Column(
                        Modifier.weight(1f).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(h.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.value, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Open", onClick = onOpen, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
            Button("Got it", onClick = onDismiss, variant = ButtonVariant.Ghost, modifier = Modifier.weight(1f))
        }
    }
}

/** The one card shape on Today: surface fill, medium corners, tappable. */
@Composable
internal fun MonoCard(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).semantics { contentDescription = description },
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}
