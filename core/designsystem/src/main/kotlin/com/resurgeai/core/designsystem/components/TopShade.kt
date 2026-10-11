package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge

/** Height of the soft shade at the top of the main screens. */
val TopShadeHeight: Dp = 380.dp

/**
 * Ambient glow at the top of a screen: a large, heavily blurred circle of [color] (each main tab
 * passes its own) that fades into the page background. Drawn at a fixed height from the top, so the
 * header can draw the same glow over its own background and line up seamlessly.
 */
@Composable
fun TopShade(color: Color = Resurge.colors.accent, modifier: Modifier = Modifier) {
    val dark = Resurge.colors.bg.luminance() < 0.5f
    val glow by animateColorAsState(color, tween(450), label = "ambient")
    val strength = if (dark) 0.42f else 0.30f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(TopShadeHeight)
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(48.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
    ) {
        val r = size.width * 0.62f
        val c = Offset(size.width * 0.28f, size.height * 0.08f)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = strength), glow.copy(alpha = strength * 0.4f), Color.Transparent), c, r), r, c)
        // A smaller, offset hue for depth.
        val c2 = Offset(size.width * 0.88f, size.height * 0.02f)
        val r2 = size.width * 0.42f
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = strength * 0.55f), Color.Transparent), c2, r2), r2, c2)
    }
}
