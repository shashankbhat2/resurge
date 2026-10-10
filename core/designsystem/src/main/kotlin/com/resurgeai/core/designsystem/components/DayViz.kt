package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge

/*
 * Compact single-row visualisations for per-day lists. All take fractions in 0..1 of a shared axis
 * so rows in the same list line up and can be compared at a glance.
 */

private fun DrawScope.track(color: Color, radius: Float) =
    drawRoundRect(color, size = size, cornerRadius = CornerRadius(radius, radius))

/** Pill bar filled to [fraction], with an optional tick at [marker] (e.g. the period average). */
@Composable
fun MagnitudeBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marker: Float? = null,
    height: Dp = 10.dp,
) {
    val trackColor = Resurge.colors.surfaceMuted
    val markerColor = Resurge.colors.textMuted
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        track(trackColor, r)
        val w = size.width * fraction.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = CornerRadius(r, r))
        marker?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(markerColor, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2.dp.toPx())
        }
    }
}

/**
 * A window on a shared axis (e.g. bedtime → wake on a 6 pm–noon clock). The window is split into
 * [segments] (relative weights + colours), e.g. sleep stages.
 */
@Composable
fun WindowBar(
    start: Float,
    end: Float,
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    gridLines: List<Float> = emptyList(),
    height: Dp = 14.dp,
) {
    val trackColor = Resurge.colors.surfaceMuted
    val gridColor = Resurge.colors.border
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        track(trackColor, r)
        gridLines.forEach { g ->
            val x = size.width * g
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        val left = size.width * start.coerceIn(0f, 1f)
        val right = size.width * end.coerceIn(0f, 1f)
        if (right <= left) return@Canvas
        val window = Path().apply {
            addRoundRect(RoundRect(left, 0f, right, size.height, CornerRadius(r, r)))
        }
        clipPath(window) {
            val total = segments.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: return@clipPath
            var x = left
            segments.forEach { (weight, color) ->
                val w = (right - left) * weight / total
                drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
    }
}

/** Low–high range on a shared axis with a dot at [mid] (e.g. heart rate min–max with average). */
@Composable
fun RangeBar(
    low: Float,
    high: Float,
    mid: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
) {
    val trackColor = Resurge.colors.surfaceMuted
    val dotRing = Resurge.colors.surface
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(trackColor, topLeft = Offset(0f, r - 1.5.dp.toPx()), size = Size(size.width, 3.dp.toPx()), cornerRadius = CornerRadius(r, r))
        val l = size.width * low.coerceIn(0f, 1f)
        val h = size.width * high.coerceIn(0f, 1f)
        drawRoundRect(
            color.copy(alpha = 0.35f),
            topLeft = Offset(l, 0f),
            size = Size((h - l).coerceAtLeast(size.height), size.height),
            cornerRadius = CornerRadius(r, r),
        )
        mid?.let {
            val c = Offset(size.width * it.coerceIn(0f, 1f), r)
            drawCircle(dotRing, radius = r, center = c)
            drawCircle(color, radius = r - 2.dp.toPx(), center = c)
        }
    }
}

/** Single dot on a shared axis (e.g. weight within the week's range). */
@Composable
fun DotOnAxis(position: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val trackColor = Resurge.colors.surfaceMuted
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(trackColor, topLeft = Offset(0f, r - 1.5.dp.toPx()), size = Size(size.width, 3.dp.toPx()), cornerRadius = CornerRadius(r, r))
        drawCircle(color, radius = r, center = Offset(size.width * position.coerceIn(0f, 1f), r))
    }
}

/** Track with consecutive pills, one per item (e.g. each workout sized by duration). */
@Composable
fun SegmentsBar(
    segments: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 3.dp,
) {
    val trackColor = Resurge.colors.surfaceMuted
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        track(trackColor, r)
        var x = 0f
        val g = gap.toPx()
        segments.forEachIndexed { i, f ->
            val w = (size.width * f).coerceAtLeast(size.height)
            if (x + w > size.width) return@Canvas
            drawRoundRect(
                color.copy(alpha = if (i % 2 == 0) 1f else 0.6f),
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(r, r),
            )
            x += w + g
        }
    }
}
