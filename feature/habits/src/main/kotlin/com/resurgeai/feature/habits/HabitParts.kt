package com.resurgeai.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayState

/** A dashed outline with rounded corners (pending days, the add tile). */
internal fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
    )
}

/**
 * One habit day as the design system's streak cell: done fills with the habit's colour (with a
 * check), an anchor day is `frost` with a snowflake, missed is `ebb`, pending is a dashed outline,
 * and a day before the habit existed is empty.
 */
@Composable
internal fun DayCell(state: DayState, colorKey: String, modifier: Modifier = Modifier, size: Dp = 32.dp, radius: Dp = 10.dp) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    val base = modifier.size(size)
    when (state) {
        DayState.DONE -> Box(base.clip(shape).background(HabitVisuals.fill(colorKey)), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = size * 0.5f)
        }
        DayState.FROZEN -> Box(base.clip(shape).background(Resurge.colors.frost), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.frostInk, size = size * 0.5f)
        }
        DayState.MISSED -> Box(base.clip(shape).background(Resurge.colors.ebb))
        DayState.PENDING -> Box(base.dashedBorder(Resurge.colors.textSubtle, radius))
        DayState.NONE -> Box(base)
    }
}

/** The habit's icon in its colour on a `surface-muted` tile. */
@Composable
internal fun HabitIconTile(iconKey: String, colorKey: String, size: Dp = 44.dp) {
    Box(Modifier.size(size).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
        Icon(HabitVisuals.icon(iconKey), contentDescription = null, tint = HabitVisuals.color(colorKey), size = size * 0.52f)
    }
}

internal val DayState.label: String
    get() = when (this) {
        DayState.DONE -> "done"
        DayState.MISSED -> "not done"
        DayState.FROZEN -> "covered by a freeze"
        DayState.PENDING -> "not logged yet"
        DayState.NONE -> "before the habit started"
    }
