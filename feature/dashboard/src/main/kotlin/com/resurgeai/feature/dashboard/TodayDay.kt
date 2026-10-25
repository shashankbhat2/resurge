package com.resurgeai.feature.dashboard

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayWeather
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.Weather
import com.resurgeai.core.model.WeatherKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Days the strip goes back, today included. */
internal const val STRIP_DAYS = 14L

/** Days after today shown (dimmed, not selectable) so today isn't pinned to the strip's edge. */
private const val STRIP_AHEAD = 3

private val CELL = 50.dp

/**
 * The day strip: a flat dial. A fixed lens sits in the middle and the days slide under it, on a
 * ruler of ticks. Tap a day or drag the strip; it settles on the nearest day.
 */
@Composable
internal fun DayStrip(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = today.minusDays(STRIP_DAYS - 1)
    val days = (0 until STRIP_DAYS + STRIP_AHEAD).map { first.plusDays(it) }
    val lastSelectable = (STRIP_DAYS - 1).toInt()
    fun indexOf(date: LocalDate) = (date.toEpochDay() - first.toEpochDay()).toInt().coerceIn(0, lastSelectable)

    // Where the strip is, in days: the lens shows the day at this position. A drag adds to it
    // directly, so nothing queued from the drag can cancel the settle afterwards.
    val position = remember { Animatable(indexOf(selected).toFloat()) }
    var drag by remember { mutableFloatStateOf(0f) }
    val cellPx = with(LocalDensity.current) { CELL.toPx() }
    val settle = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(selected) { position.animateTo(indexOf(selected).toFloat(), settle) }

    val accent = Resurge.colors.accent
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { dx ->
                    drag = (position.value + drag - dx / cellPx).coerceIn(-0.4f, lastSelectable + 0.4f) - position.value
                },
                onDragStarted = { position.stop() },
                onDragStopped = { velocity ->
                    val at = position.value + drag
                    position.snapTo(at)
                    drag = 0f
                    // A fling carries on a little past where the finger let go, then settles on a day.
                    val target = (at - velocity / cellPx * 0.15f).roundToInt().coerceIn(0, lastSelectable)
                    onSelect(first.plusDays(target.toLong()))
                    position.animateTo(target.toFloat(), settle)
                },
            ),
    ) {
        val centre = constraints.maxWidth / 2f
        // The lens, and a notch above it.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(CELL, 68.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Resurge.colors.accentTint)
                .border(1.5.dp, accent, RoundedCornerShape(12.dp)),
        )
        Canvas(Modifier.align(Alignment.TopCenter).size(10.dp, 5.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, accent)
        }
        // The days, faded out towards both ends.
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(0f to Color.Transparent, 0.24f to Color.Black, 0.76f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            // The row is wider than the strip; let it lay out at full width and slide under the lens.
            Row(
                Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset((centre - (position.value + drag + 0.5f) * cellPx).roundToInt(), 0) },
            ) {
                days.forEachIndexed { i, date ->
                    DayCell(
                        date = date,
                        selected = date == selected,
                        future = i > lastSelectable,
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, future: Boolean, onClick: () -> Unit) {
    val content = when {
        selected -> Resurge.colors.text
        future -> Resurge.colors.textSubtle.copy(alpha = 0.4f)
        else -> Resurge.colors.textMuted
    }
    val tick = if (selected) Resurge.colors.accent else Resurge.colors.borderStrong
    val minor = Resurge.colors.border
    Box(Modifier.width(CELL).height(80.dp)) {
        UnstyledButton(
            onClick = onClick,
            enabled = !future,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size(48.dp, 52.dp)
                .semantics {
                    contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())) + if (future) ", not yet" else ""
                    role = Role.Tab
                    this.selected = selected
                },
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.overline,
                    color = content.copy(alpha = content.alpha * 0.8f),
                )
                Text("${date.dayOfMonth}", style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"), color = content)
            }
        }
        // The ruler: a long tick under the day, short ones between.
        Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).fillMaxWidth().height(9.dp)) {
            val step = size.width / 5
            for (k in 0 until 5) {
                val x = step * (k + 0.5f)
                val major = k == 2
                drawLine(
                    if (major) tick else minor,
                    Offset(x, size.height),
                    Offset(x, if (major) 0f else size.height - 4.dp.toPx()),
                    strokeWidth = if (major) 1.5.dp.toPx() else 1.dp.toPx(),
                )
            }
        }
    }
}

/**
 * The day under the strip: its name, a line on its weather, and the weather on the right. Without the location permission the weather slot
 * offers to turn it on.
 */
@Composable
internal fun DayHeader(
    date: LocalDate,
    today: LocalDate,
    weather: Weather?,
    weatherAllowed: Boolean,
    units: UnitSystem,
    onBackToToday: () -> Unit,
    onAllowWeather: () -> Unit,
) {
    val isToday = date == today
    val day = weather?.days?.get(date)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(date, today).substringBefore(','), style = Resurge.type.brandLarge, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (!isToday) {
                    Spacer(Modifier.width(8.dp))
                    UnstyledButton(
                        onClick = onBackToToday,
                        modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text("Back to today", style = Resurge.type.caption, color = Resurge.colors.text, maxLines = 1) }
                }
            }
            val line = weatherLine(isToday, weather, day, units)
            if (line != null) {
                Spacer(Modifier.height(4.dp))
                Text(line, style = Resurge.type.body, color = Resurge.colors.textMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            !weatherAllowed -> UnstyledButton(
                onClick = onAllowWeather,
                modifier = Modifier.padding(top = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ResurgeIcons.WeatherPartlyCloudy, contentDescription = null, tint = Resurge.colors.textMuted, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Weather", style = Resurge.type.caption, color = Resurge.colors.text)
                }
            }
            day != null -> {
                val temp = if (isToday) weather.nowC ?: day.highC else day.highC
                val kind = if (isToday) weather.nowKind ?: day.kind else day.kind
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(top = 2.dp).semantics(mergeDescendants = true) {
                        contentDescription = "${kind.label}, ${degrees(temp, units)}. High ${degrees(day.highC, units)}, low ${degrees(day.lowC, units)}"
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(weatherIcon(kind), contentDescription = null, tint = Resurge.colors.textMuted, size = 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(degrees(temp, units), style = Resurge.type.metric)
                    }
                    Text(
                        "H ${degrees(day.highC, units)} · L ${degrees(day.lowC, units)}",
                        style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                        color = Resurge.colors.textSubtle,
                    )
                }
            }
        }
    }
}

/** Today: the weather now and the high. Earlier days: that day's weather. */
private fun weatherLine(isToday: Boolean, weather: Weather?, day: DayWeather?, units: UnitSystem): String? {
    if (!isToday) return day?.let { "${it.kind.label}, high ${degrees(it.highC, units)}, low ${degrees(it.lowC, units)}." }
    val now = weather?.nowC ?: return null
    val kind = weather.nowKind ?: day?.kind ?: return null
    return "${kind.label}, ${degrees(now, units)} now" + (day?.let { ", high ${degrees(it.highC, units)}." } ?: ".")
}

private fun degrees(celsius: Double, units: UnitSystem): String =
    if (units == UnitSystem.IMPERIAL) "${(celsius * 9 / 5 + 32).roundToInt()}°" else "${celsius.roundToInt()}°"

@DrawableRes
private fun weatherIcon(kind: WeatherKind): Int = when (kind) {
    WeatherKind.CLEAR -> ResurgeIcons.WeatherClear
    WeatherKind.PARTLY_CLOUDY -> ResurgeIcons.WeatherPartlyCloudy
    WeatherKind.CLOUDY -> ResurgeIcons.WeatherCloudy
    WeatherKind.FOG -> ResurgeIcons.WeatherFog
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> ResurgeIcons.WeatherRain
    WeatherKind.SNOW -> ResurgeIcons.WeatherSnow
    WeatherKind.STORM -> ResurgeIcons.WeatherStorm
}
