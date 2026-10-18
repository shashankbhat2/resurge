package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    val x: Float,
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
    val round: Boolean,
    val delay: Float,
)

/**
 * A burst of confetti from the top that falls and fades over [durationMs], once. [pieces] sets how
 * much: a milestone gets a little, the goal a lot. Draws nothing after it's done.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier, pieces: Int = 70, durationMs: Int = 2_600, seed: Int = 1) {
    val palette = listOf(Resurge.colors.accent, Resurge.colors.glow, Resurge.colors.energy, Resurge.colors.water, Resurge.colors.activity, Resurge.colors.sleep)
    val confetti = remember(pieces, seed) {
        val r = Random(seed)
        List(pieces) {
            val angle = (PI / 2 + (r.nextFloat() - 0.5f) * PI * 0.9).toFloat() // mostly downward, fanned out
            val speed = 0.25f + r.nextFloat() * 0.45f
            Piece(
                x = 0.15f + r.nextFloat() * 0.7f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 0.55f, // thrown up first
                spin = (r.nextFloat() - 0.5f) * 900f,
                size = 5f + r.nextFloat() * 6f,
                color = palette[r.nextInt(palette.size)],
                round = r.nextFloat() < 0.3f,
                delay = r.nextFloat() * 0.15f,
            )
        }
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(confetti) {
        val start = withFrameMillis { it }
        while (t < 1f) t = ((withFrameMillis { it } - start) / durationMs.toFloat()).coerceAtMost(1f)
    }
    if (t >= 1f) return
    Canvas(modifier) {
        val seconds = t * durationMs / 1000f
        confetti.forEach { p ->
            val s = (seconds - p.delay * durationMs / 1000f).coerceAtLeast(0f)
            if (s <= 0f) return@forEach
            // Thrown from near the top, then gravity; units are fractions of the height per second.
            val x = (p.x + p.vx * s * 0.6f) * size.width
            val y = (0.12f + p.vy * s + 0.9f * s * s) * size.height
            val alpha = (1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f))
            val px = p.size.dp.toPx()
            rotate(p.spin * s, Offset(x, y)) {
                if (p.round) drawCircle(p.color.copy(alpha = alpha), px / 2, Offset(x, y))
                else drawRect(p.color.copy(alpha = alpha), Offset(x - px / 2, y - px / 4), Size(px, px / 2))
            }
        }
    }
}
