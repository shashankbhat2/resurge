package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge

/** One stage block on a [Hypnogram]: x range as fractions of the night, [level] 0 = top row. */
data class HypnoBlock(val start: Float, val end: Float, val level: Int, val color: Color)

/**
 * Sleep-stage timeline ("hypnogram"): one row per stage level, coloured blocks where each stage
 * occurred, and thin connectors where the sleeper moved between stages. Start/end labels sit under
 * the chart with moon/sun icons.
 */
@Composable
fun Hypnogram(
    blocks: List<HypnoBlock>,
    levelLabels: List<String>,
    modifier: Modifier = Modifier,
    /** Optional bedtime/wake labels drawn under the chart with moon/sun icons. */
    startLabel: String? = null,
    endLabel: String? = null,
    chartHeight: Dp = 148.dp,
) {
    val grid = Resurge.colors.border
    val levels = levelLabels.size.coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(chartHeight)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                levelLabels.forEach { Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1) }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val rowH = size.height / levels
                val blockH = (rowH * 0.62f).coerceAtMost(18.dp.toPx())
                fun cy(level: Int) = rowH * (level + 0.5f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                repeat(levels) { l ->
                    drawLine(grid, Offset(0f, cy(l)), Offset(size.width, cy(l)), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                }
                // Connectors first so blocks sit on top of them.
                blocks.zipWithNext().forEach { (a, b) ->
                    if (a.level == b.level) return@forEach
                    val x = size.width * b.start.coerceIn(0f, 1f)
                    drawLine(b.color.copy(alpha = 0.55f), Offset(x, cy(a.level)), Offset(x, cy(b.level)), strokeWidth = 1.5.dp.toPx())
                }
                val r = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                blocks.forEach { block ->
                    val left = size.width * block.start.coerceIn(0f, 1f)
                    val w = (size.width * (block.end - block.start).coerceAtLeast(0f)).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(block.color, Offset(left, cy(block.level) - blockH / 2), Size(w, blockH), r)
                }
            }
        }
        if (startLabel == null || endLabel == null) return@Column
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.Moon, contentDescription = "Bedtime", tint = Resurge.colors.textMuted, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(startLabel, style = Resurge.type.label)
            Canvas(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .padding(horizontal = 10.dp),
            ) {
                val step = 5.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    x += step
                }
            }
            Text(endLabel, style = Resurge.type.label)
            Spacer(Modifier.width(6.dp))
            Icon(ResurgeIcons.Sun, contentDescription = "Wake time", tint = Resurge.colors.textMuted, size = 16.dp)
        }
    }
}
