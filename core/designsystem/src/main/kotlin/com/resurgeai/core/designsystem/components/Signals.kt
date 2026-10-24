package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayStatus
import com.resurgeai.core.model.PaceSignal

/** Up / mid / down as the three signal colours. */
@Composable
fun signalColor(signal: PaceSignal): Color = when (signal) {
    PaceSignal.UP -> Resurge.colors.up
    PaceSignal.MID -> Resurge.colors.mid
    PaceSignal.DOWN -> Resurge.colors.down
}

/**
 * How a day reads for the streak: met in up, a freeze in mid, missed in down. Open days and days
 * before the streak have no fill (null); callers outline them.
 */
@Composable
fun dayStatusFill(status: DayStatus): Color? = when (status) {
    DayStatus.MET -> Resurge.colors.up
    DayStatus.FORGIVEN -> Resurge.colors.mid
    DayStatus.MISSED -> Resurge.colors.down
    DayStatus.OPEN, DayStatus.NONE -> null
}

/**
 * A small bar chart for a tile: one bar per value, scaled to the largest. The last bar (today) is
 * drawn in the text colour, earlier ones in grey; missing values are a short stub.
 */
@Composable
fun MiniBars(values: List<Double?>, modifier: Modifier = Modifier, height: Dp = 22.dp, highlightLast: Boolean = true) {
    val max = values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val last = highlightLast && i == values.lastIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (v == null || v <= 0) 0.08f else (v / max).toFloat().coerceIn(0.08f, 1f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            v == null || v <= 0 -> Resurge.colors.border
                            last -> Resurge.colors.text
                            else -> Resurge.colors.borderStrong
                        },
                    ),
            )
        }
    }
}
