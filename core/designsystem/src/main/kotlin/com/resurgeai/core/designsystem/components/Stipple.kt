package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge
import kotlin.math.roundToInt

/**
 * One band of a [StippleChart]: a caps label with a dim detail under it (e.g. "REM" / "1H 40M") and
 * one value per column, 0..1 (how often this row was true at that column, across many nights).
 */
class StippleRow(val label: String, val detail: String?, val values: List<Float>)

/** An x-axis label at [at] (0..1 of the chart width). */
data class StippleTick(val at: Float, val label: String)

/**
 * Dot-matrix chart: each row is a band of tiny dots on a fixed pitch whose size, brightness and
 * density follow the row's values, over dotted vertical gridlines at each tick. With [marker] set, an
 * accent scrub line with a knob under the axis can be dragged (or tapped) along the chart; the
 * [markerLabel] rides above it.
 */
@Composable
fun StippleChart(
    rows: List<StippleRow>,
    ticks: List<StippleTick>,
    modifier: Modifier = Modifier,
    marker: Float? = null,
    onMarkerChange: ((Float) -> Unit)? = null,
    markerLabel: (@Composable () -> Unit)? = null,
    dotColor: Color = Resurge.colors.text,
    markerColor: Color = Resurge.colors.accent,
    labelWidth: Dp = 64.dp,
    bandHeight: Dp = 44.dp,
    dotRows: Int = 6,
    description: String = "Dot chart",
) {
    val grid = Resurge.colors.border
    val knob = Resurge.colors.text
    val change = rememberUpdatedState(onMarkerChange)
    Column(modifier.semantics { contentDescription = description }) {
        if (marker != null && markerLabel != null) {
            Row {
                Spacer(Modifier.width(labelWidth))
                AtFraction(marker, Modifier.weight(1f)) { markerLabel() }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row {
            Column(Modifier.width(labelWidth)) {
                rows.forEach { row ->
                    Column(Modifier.height(bandHeight), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        Text(row.label.uppercase(), style = Resurge.type.overline, color = Resurge.colors.text, maxLines = 1)
                        row.detail?.let { Text(it.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1) }
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(bandHeight * rows.size + KnobSpace)
                    .then(
                        if (onMarkerChange == null) Modifier else Modifier
                            .pointerInput(Unit) { detectTapGestures { change.value?.invoke((it.x / size.width).coerceIn(0f, 1f)) } }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { c, _ -> change.value?.invoke((c.position.x / size.width).coerceIn(0f, 1f)) }
                            },
                    ),
            ) {
                val chartH = bandHeight.toPx() * rows.size
                val dotted = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx()))
                ticks.forEach { t ->
                    val x = size.width * t.at
                    drawLine(grid, Offset(x, 0f), Offset(x, chartH), strokeWidth = 1.dp.toPx(), pathEffect = dotted)
                }
                drawLine(grid, Offset(0f, chartH), Offset(size.width, chartH), strokeWidth = 1.dp.toPx())

                val pitchY = 4.dp.toPx()
                val maxR = 1.5.dp.toPx()
                rows.forEachIndexed { r, row ->
                    val cols = row.values.size
                    if (cols == 0) return@forEachIndexed
                    val pitchX = size.width / cols
                    val top = bandHeight.toPx() * r + (bandHeight.toPx() - (dotRows - 1) * pitchY) / 2
                    row.values.forEachIndexed { c, raw ->
                        val f = raw.coerceIn(0f, 1f)
                        if (f <= 0f) return@forEachIndexed
                        val cx = pitchX * (c + 0.5f)
                        repeat(dotRows) { k ->
                            // Fewer dots where the row is rare: a stable per-dot threshold keeps it from flickering.
                            if (scatter(r, c, k) > 0.3f + f * 2.2f) return@repeat
                            drawCircle(
                                dotColor.copy(alpha = 0.3f + 0.7f * f),
                                radius = maxR * (0.45f + 0.55f * f),
                                center = Offset(cx, top + k * pitchY),
                            )
                        }
                    }
                }

                if (marker != null) {
                    val x = size.width * marker.coerceIn(0f, 1f)
                    drawLine(markerColor, Offset(x, 0f), Offset(x, chartH + 4.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                    drawCircle(knob, radius = 6.dp.toPx(), center = Offset(x, chartH + KnobSpace.toPx() / 2 + 2.dp.toPx()))
                }
            }
        }
        Row {
            Spacer(Modifier.width(labelWidth))
            Box(Modifier.weight(1f).height(16.dp)) {
                ticks.forEach { t ->
                    AtFraction(t.at, Modifier.fillMaxWidth()) {
                        Text(t.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val KnobSpace = 20.dp

/** Deterministic 0..1 noise per dot, so density reads as frequency without random flicker. */
private fun scatter(row: Int, col: Int, k: Int): Float {
    var h = row * 73856093 xor col * 19349663 xor k * 83492791
    h = h xor (h ushr 13)
    h *= 0x5bd1e995
    h = h xor (h ushr 15)
    return (h and 0xFFFF) / 65535f
}

/** Places its child centred on [fraction] of the available width, kept inside the edges. */
@Composable
private fun AtFraction(fraction: Float, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        val x = (width * fraction.coerceIn(0f, 1f) - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
        layout(width, p.height) { p.place(x, 0) }
    }
}

/**
 * A compact table row: a status dot, a caps label and value cells. The selected row gets the accent
 * dot and a slightly raised ground; the others are dimmed. [highlight] (one cell) is drawn in accent.
 */
@Composable
fun DotRow(
    label: String,
    cells: List<String>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    highlight: Int? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val text = if (selected) Resurge.colors.text else Resurge.colors.textSubtle
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) Resurge.colors.surfaceMuted else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).clip(Resurge.shape.pill).background(if (selected) Resurge.colors.accent else Resurge.colors.textSubtle))
            Spacer(Modifier.width(10.dp))
            Text(label.uppercase(), style = CellStyle(), color = text, maxLines = 1, modifier = Modifier.weight(1.45f))
            cells.forEachIndexed { i, cell ->
                Text(
                    cell.uppercase(),
                    style = CellStyle(),
                    color = if (i == highlight && selected) Resurge.colors.accent else text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            trailing?.let { Text(it.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1) }
        }
    }
    if (onClick == null) Box(modifier) { body() } else UnstyledButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        indication = LocalIndication.current,
        contentAlignment = Alignment.CenterStart,
    ) { body() }
}

@Composable
private fun CellStyle() = Resurge.type.caption.copy(fontSize = Resurge.type.label.fontSize * 0.92f, letterSpacing = 0.3.sp)
