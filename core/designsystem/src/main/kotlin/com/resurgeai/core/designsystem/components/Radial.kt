package com.resurgeai.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Radial visualisations for dashboard cards. All draw inside a square and accept a centre slot for
 * the headline value. Angles are in degrees, 0° = 3 o'clock, clockwise; rings start at 12 o'clock.
 */

/** One coloured arc of a [Ring], as a fraction (0..1) of the ring's total sweep. */
data class RingSegment(val fraction: Float, val color: Color)

/** Plays a one-shot 0 → 1 reveal when [key] changes, so rings draw in on first show. */
@Composable
private fun rememberReveal(key: Any?): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    return anim.value
}

private fun DrawScope.arcRect(stroke: Float): Pair<Offset, Size> {
    val d = min(size.width, size.height) - stroke
    return Offset((size.width - d) / 2, (size.height - d) / 2) to Size(d, d)
}

/**
 * Ring made of consecutive segments (e.g. sleep stages, active vs resting kcal, progress to a goal).
 * Segments are laid end to end from [startAngle]; whatever is left of [sweep] shows as track.
 * Set [gapDegrees] to separate segments (e.g. one segment per weekday).
 */
@Composable
fun Ring(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = Resurge.colors.surfaceMuted,
    startAngle: Float = -90f,
    sweep: Float = 360f,
    gapDegrees: Float = 0f,
    showTrack: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reveal = rememberReveal(segments.map { it.fraction })
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val (topLeft, arcSize) = arcRect(stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            if (showTrack) drawArc(trackColor, startAngle, sweep, false, topLeft, arcSize, style = style)
            // Round caps add ~half a stroke at each end; shave that off so segments don't overlap.
            val radius = arcSize.width / 2
            val capDegrees = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
            var angle = startAngle
            val visible = sweep * reveal
            // A single segment covering the whole ring is drawn as one continuous circle (no cap notch).
            val full = segments.filter { it.fraction > 0f }
            if (sweep >= 360f && gapDegrees == 0f && full.size == 1 && full[0].fraction >= 0.999f) {
                drawArc(full[0].color, startAngle, visible, false, topLeft, arcSize, style = if (reveal >= 1f) Stroke(stroke) else style)
                return@Canvas
            }
            segments.forEach { seg ->
                val segSweep = (sweep * seg.fraction).coerceAtMost(startAngle + visible - angle)
                if (segSweep <= 0f) return@forEach
                val inset = (gapDegrees / 2 + capDegrees).coerceAtMost(segSweep / 2 - 0.1f).coerceAtLeast(0f)
                drawArc(seg.color, angle + inset, (segSweep - inset * 2).coerceAtLeast(0.1f), false, topLeft, arcSize, style = style)
                angle += sweep * seg.fraction
            }
        }
        content()
    }
}

/**
 * Polar-area ("rose") chart: one wedge per value around the circle, each wedge's outer radius ∝ value.
 * [highlightIndex] is drawn at full colour, the rest faded; every wedge has a faint full-length track.
 */
@Composable
fun RadialBars(
    values: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightIndex: Int = values.lastIndex,
    innerFraction: Float = 0.58f,
    gapDegrees: Float = 6f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reveal = rememberReveal(values)
    val track = Resurge.colors.surfaceMuted
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val max = values.mapNotNull { it }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val outer = min(size.width, size.height) / 2
            val inner = outer * innerFraction
            val n = values.size.coerceAtLeast(1)
            val slot = 360f / n
            values.forEachIndexed { i, v ->
                val start = -90f + slot * i + gapDegrees / 2
                val sweepDeg = slot - gapDegrees
                drawSector(track, inner, outer, start, sweepDeg)
                if (v != null && v > 0) {
                    val r = inner + (outer - inner) * (v / max).toFloat() * reveal
                    drawSector(if (i == highlightIndex) color else color.copy(alpha = 0.45f), inner, r, start, sweepDeg)
                }
            }
        }
        content()
    }
}

/** Filled annular sector between [r0] and [r1], from [startDeg] spanning [sweepDeg]. */
private fun DrawScope.drawSector(color: Color, r0: Float, r1: Float, startDeg: Float, sweepDeg: Float) {
    if (r1 <= r0) return
    val c = Offset(size.width / 2, size.height / 2)
    val path = Path().apply {
        arcTo(Rect(c.x - r1, c.y - r1, c.x + r1, c.y + r1), startDeg, sweepDeg, forceMoveTo = true)
        arcTo(Rect(c.x - r0, c.y - r0, c.x + r0, c.y + r0), startDeg + sweepDeg, -sweepDeg, forceMoveTo = false)
        close()
    }
    drawPath(path, color)
}

/**
 * Dial (open-bottom arc) showing a [low]–[high] band and a [marker] dot, all as fractions of the
 * dial's scale. Used for heart-rate range and weight position.
 */
@Composable
fun RangeDial(
    low: Float?,
    high: Float?,
    marker: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = Resurge.colors.surfaceMuted,
    markerColor: Color = color,
    markerRing: Color = Resurge.colors.surface,
    strokeWidth: Dp = 12.dp,
    sweep: Float = 260f,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reveal = rememberReveal(listOf(low, high, marker))
    val start = 90f + (360f - sweep) / 2
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val (topLeft, arcSize) = arcRect(stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(trackColor, start, sweep, false, topLeft, arcSize, style = style)
            if (low != null && high != null) {
                val from = start + sweep * low.coerceIn(0f, 1f)
                val to = start + sweep * high.coerceIn(0f, 1f)
                drawArc(color.copy(alpha = 0.45f), from, ((to - from) * reveal).coerceAtLeast(0.5f), false, topLeft, arcSize, style = style)
            }
            marker?.let { m ->
                val a = Math.toRadians((start + sweep * m.coerceIn(0f, 1f) * reveal).toDouble())
                val r = arcSize.width / 2
                val p = Offset(size.width / 2 + r * cos(a).toFloat(), size.height / 2 + r * sin(a).toFloat())
                drawCircle(markerRing, radius = stroke * 0.85f, center = p)
                drawCircle(markerColor, radius = stroke * 0.55f, center = p)
            }
        }
        content()
    }
}
