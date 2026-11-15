package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge

/*
 * Compact single-row visualisations for per-day lists. All take fractions in 0..1 of a shared axis
 * so rows in the same list line up and can be compared at a glance.
 */

/** Dot pitch shared by every per-day viz so rows in a list form one dot-matrix. */
private val Pitch = 4.dp
private val DotRadius = 1.25.dp

/**
 * Fills the canvas with a grid of dots on [Pitch]: [color] picks each dot's colour from its x (null
 * draws nothing), and the dim [track] dot shows everywhere else.
 */
private fun DrawScope.dotGrid(track: Color?, color: (x: Float) -> Color?) {
    val pitch = Pitch.toPx()
    val r = DotRadius.toPx()
    val rows = (size.height / pitch).toInt().coerceAtLeast(1)
    val top = (size.height - (rows - 1) * pitch) / 2
    val cols = (size.width / pitch).toInt().coerceAtLeast(1)
    val left = (size.width - (cols - 1) * pitch) / 2
    for (c in 0 until cols) {
        val x = left + c * pitch
        val fill = color(x)
        for (row in 0 until rows) {
            val y = top + row * pitch
            if (fill != null) drawCircle(fill, r, Offset(x, y)) else if (track != null) drawCircle(track, r * 0.6f, Offset(x, y))
        }
    }
}

/** Dot bar filled to [fraction], with an optional tick at [marker] (e.g. the period average). */
@Composable
fun MagnitudeBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    marker: Float? = null,
    height: Dp = 10.dp,
) {
    val trackColor = Resurge.colors.border
    val markerColor = Resurge.colors.textMuted
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width * fraction.coerceIn(0f, 1f)
        dotGrid(trackColor) { x -> if (x <= w && w > 0f) color else null }
        marker?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            drawLine(markerColor, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        }
    }
}

/**
 * A window on a shared axis (e.g. bedtime → wake on a 6 pm–noon clock), as dots. The window is
 * split into [segments] (relative weights + colours), e.g. sleep stages.
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
    val trackColor = Resurge.colors.border
    val gridColor = Resurge.colors.border
    Canvas(modifier.fillMaxWidth().height(height)) {
        val dotted = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()))
        gridLines.forEach { g ->
            val x = size.width * g
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx(), pathEffect = dotted)
        }
        val left = size.width * start.coerceIn(0f, 1f)
        val right = size.width * end.coerceIn(0f, 1f)
        val total = segments.sumOf { it.first.toDouble() }.toFloat()
        dotGrid(null) { x ->
            if (right <= left || total <= 0f || x < left || x > right) return@dotGrid null
            var acc = left
            segments.firstOrNull { (weight, _) -> acc += (right - left) * weight / total; x <= acc }?.second ?: segments.last().second
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
    val trackColor = Resurge.colors.border
    val dotRing = Resurge.colors.surface
    Canvas(modifier.fillMaxWidth().height(height)) {
        val l = size.width * low.coerceIn(0f, 1f)
        val h = size.width * high.coerceIn(0f, 1f)
        val range = color.copy(alpha = 0.55f)
        dotGrid(trackColor) { x -> if (x in l..h) range else null }
        mid?.let {
            val r = size.height / 2
            val c = Offset(size.width * it.coerceIn(0f, 1f), r)
            drawCircle(dotRing, radius = r, center = c)
            drawCircle(color, radius = r - 2.dp.toPx(), center = c)
        }
    }
}

/** Single dot on a shared axis (e.g. weight within the week's range). */
@Composable
fun DotOnAxis(position: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val trackColor = Resurge.colors.border
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        val pitch = Pitch.toPx()
        var x = pitch / 2
        while (x < size.width) { drawCircle(trackColor, DotRadius.toPx() * 0.6f, Offset(x, r)); x += pitch }
        drawCircle(color, radius = r * 0.8f, center = Offset(size.width * position.coerceIn(0f, 1f), r))
    }
}

/** Dim dot track with consecutive runs of dots, one per item (e.g. each workout sized by duration). */
@Composable
fun SegmentsBar(
    segments: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    gap: Dp = 3.dp,
) {
    val trackColor = Resurge.colors.border
    Canvas(modifier.fillMaxWidth().height(height)) {
        val g = gap.toPx()
        val runs = mutableListOf<Triple<Float, Float, Color>>()
        var x = 0f
        segments.forEachIndexed { i, f ->
            val w = (size.width * f).coerceAtLeast(Pitch.toPx())
            if (x + w > size.width) return@forEachIndexed
            runs += Triple(x, x + w, color.copy(alpha = if (i % 2 == 0) 1f else 0.6f))
            x += w + g
        }
        dotGrid(trackColor) { px -> runs.firstOrNull { px >= it.first && px <= it.second }?.third }
    }
}
