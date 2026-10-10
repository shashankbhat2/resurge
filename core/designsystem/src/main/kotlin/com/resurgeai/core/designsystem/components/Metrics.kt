package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge

/** Small pill naming where data came from, e.g. "⌚ Galaxy Watch". */
@Composable
fun SourceChip(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Resurge.colors.textMuted, size = 12.dp)
        Text(
            label,
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Dashboard card for one metric: icon, label, big value, optional subtitle, sparkline and source.
 * Shows a muted "No data" state when [value] is null.
 */
@Composable
fun MetricCard(
    title: String,
    @DrawableRes icon: Int,
    tint: Color,
    value: String?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    subtitle: String? = null,
    sparkline: List<Double?>? = null,
    footer: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Card(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = Resurge.type.label, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        if (value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Resurge.type.metric, maxLines = 1)
                unit?.let {
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
        } else {
            Text("No data", style = Resurge.type.headline, color = Resurge.colors.textSubtle)
        }
        subtitle?.let {
            Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sparkline != null && sparkline.count { it != null } >= 2) {
            Spacer(Modifier.height(10.dp))
            Sparkline(sparkline, color = tint, modifier = Modifier.fillMaxWidth().height(28.dp))
        }
        footer?.let {
            Spacer(Modifier.height(10.dp))
            it()
        }
    }
}

/** Minimal trend line; nulls are skipped. Last point is marked with a dot. */
@Composable
fun Sparkline(
    values: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier.semantics { contentDescription = "Trend" }) {
        val points = values.withIndex().filter { it.value != null }
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.value!! }
        val max = points.maxOf { it.value!! }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val pad = strokeWidth.toPx() * 2
        fun y(v: Double) = (pad + (size.height - pad * 2) * (1 - (v - min) / span)).toFloat()

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = p.index * stepX
            if (i == 0) path.moveTo(x, y(p.value!!)) else path.lineTo(x, y(p.value!!))
        }
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = points.last()
        drawCircle(color, radius = strokeWidth.toPx() * 1.6f, center = Offset(last.index * stepX, y(last.value!!)))
    }
}

/**
 * Vertical bar chart with day labels. Null values render as an empty track.
 * [highlightIndex] (default: last) is drawn in full colour, others at reduced alpha.
 */
@Composable
fun BarChart(
    values: List<Double?>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightIndex: Int = values.lastIndex,
    target: Double? = null,
    chartHeight: Dp = 160.dp,
) {
    val track = Resurge.colors.surfaceMuted
    val targetColor = Resurge.colors.textSubtle
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            val max = (values.mapNotNull { it } + listOfNotNull(target)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val count = values.size.coerceAtLeast(1)
            val slot = size.width / count
            val barWidth = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            values.forEachIndexed { i, v ->
                val left = slot * i + (slot - barWidth) / 2
                drawRoundRect(track, topLeft = Offset(left, 0f), size = Size(barWidth, size.height), cornerRadius = radius)
                if (v != null && v > 0) {
                    val h = (size.height * (v / max)).toFloat().coerceAtLeast(barWidth)
                    drawRoundRect(
                        color = if (i == highlightIndex) color else color.copy(alpha = 0.45f),
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius,
                    )
                }
            }
            target?.let {
                val y = (size.height * (1 - it / max)).toFloat()
                drawLine(
                    targetColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = Resurge.type.caption,
                    color = if (i == highlightIndex) Resurge.colors.text else Resurge.colors.textSubtle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Seven dots for Mon–Sun; filled = active day, outlined = today/not yet. */
@Composable
fun WeekDots(
    active: List<Boolean>,
    color: Color,
    modifier: Modifier = Modifier,
    todayIndex: Int? = null,
    dotSize: Dp = 10.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        active.forEachIndexed { i, on ->
            Box(
                Modifier
                    .size(dotSize)
                    .clip(Resurge.shape.pill)
                    .background(if (on) color else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = when {
                            on -> color
                            i == todayIndex -> Resurge.colors.textMuted
                            else -> Resurge.colors.border
                        },
                        shape = Resurge.shape.pill,
                    ),
            )
        }
    }
}

/** Label over value, for stat rows on detail screens. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = Resurge.type.headline)
    }
}

/** Horizontal share bar, e.g. 72% watch / 28% phone. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(Resurge.shape.pill)
                .background(color),
        )
    }
}

/** Section heading used on scrolling screens. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
