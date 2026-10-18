package com.resurgeai.feature.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayItem
import com.resurgeai.core.model.DayStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Today's hero card: the day streak, this week as seven days (today shows how far along it is),
 * and today's goals. Tapping the streak or the week opens their sheets; the gear opens the
 * choice of what a day needs.
 */
@Composable
internal fun DayStreakCard(
    state: DayStreakState,
    onOpenStreak: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            UnstyledButton(
                onClick = onOpenStreak,
                modifier = Modifier.weight(1f).clip(Resurge.shape.md).semantics { contentDescription = "Day streak: ${days(state.streak.current)}. Open" },
                contentPadding = PaddingValues(12.dp),
                indication = LocalIndication.current,
                contentAlignment = Alignment.CenterStart,
            ) { StreakTitle(state) }
            UnstyledButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp).clip(Resurge.shape.pill).semantics { contentDescription = "Choose what counts" },
                indication = LocalIndication.current,
            ) { Icon(ResurgeIcons.Settings, contentDescription = null, tint = Resurge.colors.textSubtle, size = 20.dp) }
        }
        UnstyledButton(
            onClick = onOpenWeek,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "This week: ${state.week.metDays} of ${state.week.target} days. Open" },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            indication = LocalIndication.current,
        ) { WeekStrip(state) }
        Divider()
        TodayGoals(state, onOpenSettings)
    }
}

@Composable
private fun StreakTitle(state: DayStreakState) {
    val current = state.streak.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(Resurge.shape.pill).background(if (current > 0) Resurge.colors.glow else Resurge.colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ResurgeIcons.FlameFilled, contentDescription = null, tint = if (current > 0) Resurge.colors.onGlow else Resurge.colors.textSubtle, size = 22.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$current", style = Resurge.type.tide.copy(fontSize = 32.sp, lineHeight = 34.sp, fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(6.dp))
                Text("day streak", style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
            Text(
                when {
                    current == 0 -> "Finish today to start one"
                    current >= state.streak.best -> "Your best yet"
                    else -> "Best ${days(state.streak.best)}"
                },
                style = Resurge.type.caption,
                color = if (current > 0 && current >= state.streak.best) Resurge.colors.glowInk else Resurge.colors.textSubtle,
            )
        }
    }
}

/** This week, first day to last: each day's status, today as a ring filling with today's goals. */
@Composable
private fun WeekStrip(state: DayStreakState) {
    val start = state.week.start
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0L until 7L).forEach { i ->
                val date = start.plusDays(i)
                val status = if (date.isAfter(state.day)) null else state.streak.days[date]
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = Resurge.type.caption,
                        color = if (date == state.day) Resurge.colors.text else Resurge.colors.textSubtle,
                    )
                    StripDay(date, status, isToday = date == state.day, progress = if (state.total == 0) 0f else state.done / state.total.toFloat())
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
            Text(
                "${state.week.metDays} of ${state.week.target} days",
                style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
                color = if (state.week.reached) Resurge.colors.glowInk else Resurge.colors.textMuted,
            )
            if (state.week.reached) {
                Spacer(Modifier.width(4.dp))
                Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.glowInk, size = 14.dp)
            }
        }
    }
}

@Composable
internal fun StripDay(date: LocalDate, status: DayStatus?, isToday: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val label = when {
        isToday && status == DayStatus.MET -> "today, done"
        isToday -> "today, ${(progress * 100).toInt()} percent"
        status == DayStatus.MET -> "done"
        status == DayStatus.FORGIVEN -> "covered by the freeze"
        status == DayStatus.MISSED -> "missed"
        status == DayStatus.OPEN -> "still open"
        else -> "not yet"
    }
    val accent = Resurge.colors.accent
    val track = Resurge.colors.surfaceMuted
    Box(
        modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))}: $label" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            status == DayStatus.MET -> Box(Modifier.fillMaxSize(0.92f).clip(Resurge.shape.pill).background(Resurge.colors.glow), contentAlignment = Alignment.Center) {
                Icon(ResurgeIcons.FlameFilled, contentDescription = null, tint = Resurge.colors.onGlow, size = 16.dp)
            }
            status == DayStatus.FORGIVEN -> Box(Modifier.fillMaxSize(0.92f).clip(Resurge.shape.pill).background(Resurge.colors.frost), contentAlignment = Alignment.Center) {
                Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.frostInk, size = 15.dp)
            }
            isToday -> {
                // Today: a ring filling with today's goals.
                Canvas(Modifier.fillMaxSize(0.92f)) {
                    val w = 3.dp.toPx()
                    val inset = w / 2
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                    if (progress > 0f) drawArc(accent, -90f, 360f * progress, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${date.dayOfMonth}", style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight), color = Resurge.colors.text)
            }
            status == DayStatus.MISSED -> Box(Modifier.fillMaxSize(0.92f).clip(Resurge.shape.pill).background(track), contentAlignment = Alignment.Center) {
                Text("${date.dayOfMonth}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
            status == DayStatus.OPEN -> Box(Modifier.fillMaxSize(0.92f).clip(Resurge.shape.pill).border(1.5.dp, Resurge.colors.textSubtle, Resurge.shape.pill), contentAlignment = Alignment.Center) {
                Text("${date.dayOfMonth}", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            else -> Box(Modifier.fillMaxSize(0.92f).clip(Resurge.shape.pill).border(1.dp, Resurge.colors.border, Resurge.shape.pill), contentAlignment = Alignment.Center) {
                Text("${date.dayOfMonth}", style = Resurge.type.caption, color = Resurge.colors.textSubtle.copy(alpha = 0.6f))
            }
        }
    }
}

/** Today's goals in two columns, what's left first, with the habits as one line. */
@Composable
private fun TodayGoals(state: DayStreakState, onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.total == 0) {
            Text("Choose what a day needs to count toward your streak.", style = Resurge.type.body, color = Resurge.colors.textMuted)
            Button("Choose goals", onClick = onOpenSettings, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        // Habits fold into one line: the grid below already lists each of them.
        val habits = state.today.filter { it.key.startsWith("habit_") }
        val lines = buildList {
            if (habits.isNotEmpty()) {
                val done = habits.count { it.done == true }
                add(DayItem("habits", if (habits.size == 1) habits.single().label else "Habits $done of ${habits.size}", if (done == habits.size) true else null))
            }
            addAll(state.today.filterNot { it.key.startsWith("habit_") })
        }.let { all -> all.filter { it.done != true } + all.filter { it.done == true } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY", style = Resurge.type.overline, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
            Text(
                if (state.todayMet) "All done" else "${lines.count { it.done == true }} of ${lines.size}",
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = if (state.todayMet) Resurge.colors.brandInk else Resurge.colors.textSubtle,
            )
        }
        lines.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { GoalRow(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GoalRow(item: DayItem, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(Resurge.shape.pill).then(
                if (item.done == true) Modifier.background(Resurge.colors.accent)
                else Modifier.border(1.5.dp, if (item.done == false) Resurge.colors.border else Resurge.colors.textSubtle, Resurge.shape.pill),
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done == true) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = 12.dp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            item.label,
            style = Resurge.type.label,
            color = when (item.done) {
                true -> Resurge.colors.text
                false -> Resurge.colors.textSubtle
                null -> Resurge.colors.textMuted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun days(n: Int): String = if (n == 1) "1 day" else "$n days"
