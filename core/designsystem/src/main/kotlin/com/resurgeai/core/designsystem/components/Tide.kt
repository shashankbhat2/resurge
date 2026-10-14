package com.resurgeai.core.designsystem.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge
import kotlin.math.min

/*
 * Resurge Tide: the daily game layer. The tide ring is today's single score and a medal marks the
 * streak (days in a row with every habit done).
 */

/** Ease-out used for every rising value (tide fill, count-ups). */
val TideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
const val TIDE_RISE_MS = 600

/**
 * Segmented ring: [total] equal segments, the first [done] filled with `brand`. The newest filled
 * segment's leading edge shows `foam` while it rises. [content] sits in the middle (the percentage).
 */
@Composable
fun TideRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val level by animateFloatAsState(done.toFloat(), tween(TIDE_RISE_MS, easing = TideEasing), label = "tide")
    val brand = Resurge.colors.accent
    val foam = Resurge.colors.foam
    val track = Resurge.colors.accentMuted
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val n = total.coerceAtLeast(1)
            val stroke = strokeWidth.toPx()
            val d = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            val arc = Size(d, d)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            // Round caps reach half a stroke past each end; leave a visible gap between segments.
            val capDeg = Math.toDegrees((stroke / 2 / (d / 2)).toDouble()).toFloat()
            val slot = 360f / n
            val gap = if (n == 1) 0f else 2 * capDeg + 4f
            val sweep = slot - gap
            for (i in 0 until n) {
                val start = -90f + slot * i + gap / 2
                drawArc(track, start, sweep.coerceAtLeast(0.1f), false, topLeft, arc, style = style)
                val fill = (level - i).coerceIn(0f, 1f)
                if (fill > 0f) {
                    drawArc(if (fill < 1f) foam else brand, start, (sweep * fill).coerceAtLeast(0.1f), false, topLeft, arc, style = style)
                }
            }
        }
        content()
    }
}

/**
 * Streak: consecutive days, shown with a medal on `glow`. Only for a run that exists;
 * callers show their own "start" copy at zero.
 */
@Composable
fun RiseBadge(count: Int, modifier: Modifier = Modifier, label: String = "day streak") {
    Row(
        modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.glowTint)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$count $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(32.dp).clip(Resurge.shape.pill).background(Resurge.colors.glow),
            contentAlignment = Alignment.Center,
        ) { Icon(ResurgeIcons.Medal, contentDescription = null, tint = Resurge.colors.onGlow, size = 20.dp) }
        Text(count.toString(), style = Resurge.type.title.copy(fontFamily = Resurge.type.metric.fontFamily, fontFeatureSettings = "tnum"), color = Resurge.colors.glowInk)
        Text(label, style = Resurge.type.label, color = Resurge.colors.glowInk)
    }
}
