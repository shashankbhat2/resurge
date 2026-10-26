package com.resurgeai.feature.fitness

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.cardio.CardioPointsState
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.OverviewBodyHeight
import com.resurgeai.core.designsystem.components.OverviewFooterHeight
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CardioMethod
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.CardioStatus
import com.resurgeai.core.model.DailyCardioPoints
import com.resurgeai.core.model.HabitStreaks
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Matches Move Week: a number-first overview, seven activity columns, and one detail entry. */
@Composable
internal fun CardioPointsCard(state: CardioPointsState, today: LocalDate, onRetry: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    val week = HabitStreaks.weekStart(today)
    val dates = (0L..6L).map(week::plusDays)
    val byDate = state.days.associateBy { it.date }
    val values = dates.map { byDate[it] }
    val day = byDate[today]
    val value = if (state.loading || values.all { it == null }) null else values.filterNotNull().sumOf { it.points }
    val caption = when {
        state.loading -> "Calculating…"
        state.refreshing -> "Updating…"
        state.status == CardioStatus.STALE -> "Update needed"
        state.status == CardioStatus.PERMISSION_REQUIRED -> "Access limited"
        state.status == CardioStatus.UNAVAILABLE -> "Manual workouts only"
        values.filterNotNull().any { it.estimated } -> "Includes estimates"
        state.status == CardioStatus.LIMITED_DATA -> "Limited data"
        else -> "Daily breakdown & history"
    }
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Column(Modifier.fillMaxWidth().heightIn(min = OverviewBodyHeight).padding(20.dp)) {
            Overline("Cardio Points")
            Spacer(Modifier.height(6.dp))
            PointsHeadline(value)
            Spacer(Modifier.height(12.dp))
            CardioDayColumns(dates, values, today.minusDays(CardioPointsEngine.HISTORY_DAYS - 1), today, today)
        }
        Divider()
        UnstyledButton(
            onClick = { details = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = OverviewFooterHeight)
                .semantics(mergeDescendants = true) { contentDescription = "View Cardio Points details" },
            indication = LocalIndication.current,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                    Icon(ResurgeIcons.Heartbeat, contentDescription = null, tint = Resurge.colors.activity, size = 20.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(day?.let { "${it.points} points today" } ?: "Today", style = Resurge.type.label, color = Resurge.colors.text)
                    Text(caption, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                }
                Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 20.dp)
            }
        }
    }
    if (details) {
        Dialog(onDismissRequest = { details = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { details = false } },
                contentAlignment = Alignment.BottomCenter,
            ) {
                CardioPointsDetailsContent(state, today, onRetry, onDismiss = { details = false })
            }
        }
    }
}

@Composable
private fun PointsHeadline(value: Long?, unit: String = "points this week") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value?.toString() ?: "—", style = Resurge.type.tide.copy(fontSize = 44.sp, lineHeight = 46.sp),
            color = if (value == null) Resurge.colors.textSubtle else Resurge.colors.text,
        )
        Spacer(Modifier.width(6.dp))
        Text(unit, style = Resurge.type.label, color = Resurge.colors.textMuted,
            modifier = Modifier.weight(1f).padding(bottom = 6.dp))
    }
}

/** The same rounded tracks, green fill, and day labels as Move Week, without a target line. */
@Composable
private fun CardioDayColumns(
    dates: List<LocalDate>,
    values: List<DailyCardioPoints?>,
    first: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    onSelect: ((LocalDate) -> Unit)? = null,
) {
    val max = values.filterNotNull().maxOfOrNull { it.points }?.coerceAtLeast(1) ?: 1
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dates.forEachIndexed { index, date ->
            val value = values[index]
            val enabled = date in first..today
            val isSelected = date == selectedDate
            val modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                this.selected = isSelected
                contentDescription = "$date: ${if (!enabled) "outside available history" else value?.let { "${it.points} points" } ?: "points unavailable"}"
            }
            val column = @Composable {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (onSelect != null) {
                        Text(if (enabled) value?.points?.toString() ?: "—" else "—", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                        Spacer(Modifier.height(6.dp))
                    }
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(shape)
                            .background(if (enabled && value != null) Resurge.colors.surfaceMuted else Color.Transparent)
                            .then(when {
                                isSelected -> Modifier.border(1.5.dp, Resurge.colors.textSubtle, shape)
                                !enabled || value == null -> Modifier.border(1.dp, Resurge.colors.border, shape)
                                else -> Modifier
                            }),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (enabled && value != null && value.points > 0) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(value.points.toFloat() / max).background(Resurge.colors.activity))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = Resurge.type.caption,
                        color = if (isSelected) Resurge.colors.text else Resurge.colors.textSubtle, textAlign = TextAlign.Center)
                }
            }
            if (onSelect == null) Box(modifier) { column() }
            else UnstyledButton(onClick = { onSelect(date) }, enabled = enabled, modifier = modifier, indication = LocalIndication.current) { column() }
        }
    }
}

/** Content is separate from the dialog so history and accessibility states can be screenshot tested. */
@Composable
internal fun CardioPointsDetailsContent(state: CardioPointsState, today: LocalDate, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val first = today.minusDays(CardioPointsEngine.HISTORY_DAYS - 1)
    var selectedDate by rememberSaveable(today.toString(), state.zone) { mutableStateOf(today.toString()) }
    var explanation by rememberSaveable { mutableStateOf(false) }
    val selected = LocalDate.parse(selectedDate).coerceIn(first, today)
    val week = HabitStreaks.weekStart(selected)
    val dates = (0L..6L).map(week::plusDays)
    val byDate = state.days.associateBy { it.date }
    val values = dates.map { byDate[it] }
    val day = byDate[selected]
    val clock = clockFormatter()
    val canPrevious = week > HabitStreaks.weekStart(first)
    val canNext = week < HabitStreaks.weekStart(today)
    val status = if (day?.status == CardioStatus.STALE) CardioStatus.STALE else state.status
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Resurge.colors.surface)
            .pointerInput(Unit) { detectTapGestures {} }
            .windowInsetsPadding(WindowInsets.navigationBars).padding(top = 10.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
            .clip(Resurge.shape.pill).background(Resurge.colors.border))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cardio Points", style = Resurge.type.title, color = Resurge.colors.text, modifier = Modifier.weight(1f))
            IconButton(ResurgeIcons.Close, "Close Cardio Points", onDismiss)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(ResurgeIcons.ArrowLeft, "Previous week", { selectedDate = maxOf(first, week.minusDays(1)).toString() },
                    enabled = canPrevious, tint = if (canPrevious) Resurge.colors.text else Resurge.colors.textSubtle)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${week.format(DATE)} – ${week.plusDays(6).format(DATE)}", style = Resurge.type.label, color = Resurge.colors.text)
                    if (week < first) Text("Partial week", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
                IconButton(ResurgeIcons.ArrowRight, "Next week", { selectedDate = minOf(today, week.plusDays(7)).toString() },
                    enabled = canNext, tint = if (canNext) Resurge.colors.text else Resurge.colors.textSubtle)
            }
            PointsHeadline(if (state.loading || values.all { it == null }) null else values.filterNotNull().sumOf { it.points },
                unit = if (week == HabitStreaks.weekStart(today)) "points this week" else "points that week")
            if (dates.any { it in first..today && byDate[it] == null } && !state.loading) {
                Text("Some days are unavailable.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            CardioDayColumns(dates, values, first, today, selected, onSelect = { selectedDate = it.toString() })
            Divider()
            CardioValueRow(selected.format(DAY), day?.let { "${it.points} points" } ?: "Unavailable")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.loading) "Calculating points…" else statusMessage(status), style = Resurge.type.caption, color = Resurge.colors.textMuted)
                    day?.let {
                        val updated = it.calculatedAt.atZone(ZoneId.of(state.zone))
                        Text("Updated ${updated.format(DATE)}, ${updated.format(clock)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                    }
                    if (state.refreshing) Text("Refreshing…", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                }
                IconButton(ResurgeIcons.Repeat, "Refresh points", onRetry, enabled = !state.refreshing)
            }
            if (day != null) {
                Overline("Points breakdown")
                Column(Modifier.fillMaxWidth()) {
                    CardioMethod.entries.forEachIndexed { index, method ->
                        if (index > 0) Divider()
                        CardioValueRow(method.label(), points(day.weightedMs(method) / 60_000.0))
                    }
                }
                Overline("Contributions")
                if (day.contributions.isEmpty()) Text("No recorded contributions for this day.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                Column(Modifier.fillMaxWidth()) {
                    day.contributions.forEachIndexed { index, c ->
                        if (index > 0) Divider()
                        CardioValueRow(c.workoutTitle ?: "Everyday movement", "${points(c.points)} pts",
                            "${c.method.label()} · ${c.source} · ${points(c.durationMs / 60_000.0)} min")
                    }
                }
                if (day.unclassifiedMs > 0) Text("${points(day.unclassifiedMs / 60_000.0)} min of unclassified activity has no automatic estimate.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Divider()
            UnstyledButton(
                onClick = { explanation = !explanation },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { stateDescription = if (explanation) "Expanded" else "Collapsed" },
                indication = LocalIndication.current,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("How points work", style = Resurge.type.label, color = Resurge.colors.text, modifier = Modifier.weight(1f))
                    Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 20.dp,
                        modifier = Modifier.rotate(if (explanation) 90f else 0f))
                }
            }
            if (explanation) {
                Text("Earn 1 point per minute of moderate activity, or 2 for vigorous activity.", style = Resurge.type.body, color = Resurge.colors.text)
                Text("Cadence of 100–<130 steps/min earns 1 point/min; 130+ earns 2. Below 100 earns zero. Short step records can provide a step-rate estimate; daily totals cannot establish intensity.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                Text("Recognized workouts without cadence use activity estimates: 3–<6 METs earns 1 point/min; 6+ earns 2. Cycling uses Resurge’s 7.5 MET estimate, earning 2 points/min. Unknown activities have no automatic estimate.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                Text("Walking and running use available cadence. Other workouts own their time; everyday movement counts outside workouts. Overlapping activity and sources count once.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                Text("Daily totals round down after adding weighted activity time. Week totals add those daily totals; contribution decimals can differ because of rounding. Missing recordings limit coverage.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                Text("No heart rate is used. Activity estimates and source handling can produce different totals from Google Fit.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Text("Last 30 days · Calculated on this device\nPoints are not uploaded or sent to the coach.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
    }
}

@Composable
private fun CardioValueRow(label: String, value: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Resurge.type.body, color = Resurge.colors.text)
            subtitle?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted) }
        }
        Text(value, style = Resurge.type.label, color = Resurge.colors.text)
    }
}

private fun statusMessage(status: CardioStatus): String = when (status) {
    CardioStatus.READY -> "Based on available cadence and logged activity."
    CardioStatus.LIMITED_DATA -> "Some movement has no usable cadence. Logged activities may be estimated."
    CardioStatus.PERMISSION_REQUIRED -> "Using permitted data and manual workouts. Manage Health Connect access in Settings."
    CardioStatus.UNAVAILABLE -> "Health Connect is unavailable. Manual workouts can still earn estimated points."
    CardioStatus.STALE -> "Some data could not be refreshed. Totals may be out of date."
}

private fun CardioMethod.label() = when (this) {
    CardioMethod.CADENCE -> "Cadence-based"
    CardioMethod.STEP_RATE_ESTIMATE -> "Step-rate estimate"
    CardioMethod.ACTIVITY_ESTIMATE -> "Estimated activity"
}
private fun points(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
