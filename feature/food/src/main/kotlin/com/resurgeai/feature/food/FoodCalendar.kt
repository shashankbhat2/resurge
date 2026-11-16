package com.resurgeai.feature.food

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.feature.food.FoodFeed.DayState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private val CellWidth = 48.dp
private val RingSize = 32.dp

/**
 * A strip of days you scroll sideways. The picked day always sits in the fixed frame in the middle:
 * fling and it snaps to the nearest day, tap a day and it slides in. Each day's ring is its calories
 * against the target, so the strip reads as a run of good and bad days at a glance.
 */
@Composable
internal fun DayStrip(state: FoodUiState, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val days = state.calendar
    val selectedIndex = days.indexOfFirst { it.date == state.selected }.coerceAtLeast(0)
    val todayIndex = days.indexOfFirst { it.date == state.today }.coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val cellPx = with(LocalDensity.current) { CellWidth.toPx() }
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(state.selected)
    val latestDays by rememberUpdatedState(days)
    val today by rememberUpdatedState(state.today)
    val todayAt by rememberUpdatedState(todayIndex)
    // With half the strip as padding on each side, the first visible item is the one in the frame.
    val centered by remember {
        derivedStateOf {
            val step = if (list.firstVisibleItemScrollOffset > cellPx / 2) 1 else 0
            (list.firstVisibleItemIndex + step).coerceIn(0, (latestDays.size - 1).coerceAtLeast(0))
        }
    }

    // Picked elsewhere (a tap, the Today button): slide that day into the frame.
    LaunchedEffect(selectedIndex) {
        if (centered != selectedIndex) list.animateScrollToItem(selectedIndex)
    }
    // A fling settles on a day: that day is picked. Future days bounce back to today.
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { scrolling ->
            val day = latestDays.getOrNull(centered)
            if (scrolling || day == null) return@collect
            when {
                day.state == DayState.FUTURE -> {
                    list.animateScrollToItem(todayAt)
                    select(today)
                }
                day.date != current -> select(day.date)
            }
        }
    }

    val focus = days.getOrNull(centered)
    Column(
        modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Overline((focus?.date ?: state.selected).format(monthTitle), Modifier.weight(1f))
            if (!state.isToday) {
                UnstyledButton(
                    onClick = { onSelect(state.today) },
                    role = Role.Button,
                    modifier = Modifier.clip(Resurge.shape.sm).semantics { contentDescription = "Back to today" },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    indication = LocalIndication.current,
                ) { Text("TODAY →", style = Resurge.type.overline, color = Resurge.colors.accent) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = ((maxWidth - CellWidth) / 2).coerceAtLeast(0.dp)
            // The fixed frame the picked day sits in.
            Box(
                Modifier
                    .size(width = CellWidth, height = CellHeight)
                    .clip(Resurge.shape.md)
                    .background(Resurge.colors.accentMuted)
                    .border(1.dp, Resurge.colors.accent, Resurge.shape.md),
            )
            LazyRow(
                state = list,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = side),
                flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
            ) {
                itemsIndexed(days, key = { _, d -> d.date.toEpochDay() }) { index, day ->
                    DayCell(
                        day = day,
                        target = state.targets.calories,
                        isToday = day.date == state.today,
                        inFrame = index == centered,
                        onClick = { onSelect(day.date) },
                    )
                }
            }
        }
        Text(
            focus?.let { summary(it, state.today) } ?: "",
            style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
            color = Resurge.colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            maxLines = 1,
        )
        Legend(Modifier.padding(horizontal = 20.dp))
    }
}

private val CellHeight = 80.dp

@Composable
private fun DayCell(day: FoodFeed.CalendarDay, target: Int, isToday: Boolean, inFrame: Boolean, onClick: () -> Unit) {
    val future = day.state == DayState.FUTURE
    UnstyledButton(
        onClick = onClick,
        enabled = !future,
        role = Role.Button,
        modifier = Modifier
            .size(width = CellWidth, height = CellHeight)
            .clip(Resurge.shape.md)
            .semantics {
                contentDescription = "${day.date.format(a11yDay)}, ${describe(day)}"
                selected = inFrame
            },
        contentPadding = PaddingValues(0.dp),
        indication = LocalIndication.current,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).uppercase(),
                style = Resurge.type.overline,
                color = when {
                    inFrame -> Resurge.colors.accent
                    future -> Resurge.colors.textSubtle.copy(alpha = 0.5f)
                    else -> Resurge.colors.textSubtle
                },
            )
            Spacer(Modifier.height(6.dp))
            Box(contentAlignment = Alignment.Center) {
                DayRing(day.state, day.kcal / target.coerceAtLeast(1), RingSize, stroke = 3.dp)
                Text(
                    day.date.dayOfMonth.toString(),
                    style = Resurge.type.caption.copy(fontFeatureSettings = "tnum", fontWeight = if (inFrame || isToday) Resurge.type.headline.fontWeight else null),
                    color = when {
                        future -> Resurge.colors.textSubtle.copy(alpha = 0.5f)
                        isToday -> Resurge.colors.accent
                        else -> Resurge.colors.text
                    },
                )
            }
            Spacer(Modifier.height(5.dp))
            // Today gets a small mark, so it stays findable when another day is picked.
            Box(
                Modifier
                    .size(width = 12.dp, height = 2.dp)
                    .background(if (isToday) Resurge.colors.accent else Color.Transparent),
            )
        }
    }
}

/**
 * Calories against target as a ring: dashed when nothing's logged, a partial arc under target, a full
 * blue ring on target, and over target a full fuel ring with a badge dot.
 */
@Composable
private fun DayRing(state: DayState, share: Double, size: Dp, stroke: Dp) {
    val track = Resurge.colors.surfaceMuted
    val energy = Resurge.colors.energy
    val accent = Resurge.colors.accent
    val border = Resurge.colors.textSubtle.copy(alpha = 0.6f)
    val cutout = Resurge.colors.surface
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        when (state) {
            DayState.FUTURE -> circle(border.copy(alpha = 0.3f), w, dashed = true)
            DayState.EMPTY -> circle(border, w, dashed = true)
            DayState.UNDER -> {
                circle(track, w)
                arc(energy, w, share.toFloat().coerceIn(0.04f, 1f))
            }
            DayState.ON_TARGET -> circle(accent, w)
            DayState.OVER -> {
                circle(energy, w)
                // A badge on the ring's top-right: over, without crowding the date inside.
                val r = (min(this.size.width, this.size.height) - w) / 2
                val c = Offset(center.x + r * 0.7071f, center.y - r * 0.7071f)
                drawCircle(cutout, radius = w * 1.9f, center = c)
                drawCircle(energy, radius = w * 1.25f, center = c)
            }
        }
    }
}

private fun DrawScope.circle(color: Color, w: Float, dashed: Boolean = false) {
    val d = min(size.width, size.height) - w
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset((size.width - d) / 2, (size.height - d) / 2),
        size = Size(d, d),
        style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(w * 1.2f, w * 1.6f)) else null),
    )
}

private fun DrawScope.arc(color: Color, w: Float, fraction: Float) {
    val d = min(size.width, size.height) - w
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 360f * fraction,
        useCenter = false,
        topLeft = Offset((size.width - d) / 2, (size.height - d) / 2),
        size = Size(d, d),
        style = Stroke(w, cap = StrokeCap.Round),
    )
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LegendItem(DayState.EMPTY, 0.0, "None")
        LegendItem(DayState.UNDER, 0.6, "Under")
        LegendItem(DayState.ON_TARGET, 1.0, "On target")
        LegendItem(DayState.OVER, 1.3, "Over")
    }
}

@Composable
private fun LegendItem(state: DayState, share: Double, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DayRing(state, share, 14.dp, stroke = 2.dp)
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), style = Resurge.type.overline, color = Resurge.colors.textSubtle)
    }
}

private fun describe(day: FoodFeed.CalendarDay): String = when (day.state) {
    DayState.FUTURE, DayState.EMPTY -> day.state.label.lowercase()
    else -> "${kcal(day.kcal)}, ${day.meals} ${if (day.meals == 1) "meal" else "meals"}, ${day.state.label.lowercase()}"
}

internal fun summary(day: FoodFeed.CalendarDay, today: LocalDate): String {
    val name = dayLabel(day.date, today).uppercase()
    return when (day.state) {
        DayState.FUTURE, DayState.EMPTY -> "$name · ${day.state.label.uppercase()}"
        else -> "$name · ${day.meals} ${if (day.meals == 1) "MEAL" else "MEALS"} · ${kcal(day.kcal).uppercase()} · ${day.state.label.uppercase()}"
    }
}

private val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
private val a11yDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
