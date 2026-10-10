package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Canvas
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
val TopShadeHeight: Dp = 340.dp

/**
 * Soft neutral shade at the top of the main screens: a slightly lighter tone in the dark theme and a
 * slightly darker tone in the light theme, blurred (wide radial falloff) and fading into the page.
 * Drawn at a fixed height from the top, so the header can draw the same shade over its own
 * background and line up seamlessly with the copy behind the content.
 */
@Composable
fun TopShade(modifier: Modifier = Modifier) {
    val dark = Resurge.colors.bg.luminance() < 0.5f
    // Lighter shade of the dark theme / darker shade of the light theme.
    val tone = if (dark) Color.White else Color.Black
    val strength = if (dark) 0.09f else 0.055f
    Canvas(modifier.fillMaxWidth().height(TopShadeHeight)) {
        drawRect(
            Brush.verticalGradient(
                0f to tone.copy(alpha = strength),
                0.45f to tone.copy(alpha = strength * 0.45f),
                1f to Color.Transparent,
            ),
        )
        // A wide, soft bloom near the top so the edge reads as blurred rather than a flat band.
        val c = Offset(size.width * 0.5f, 0f)
        drawCircle(
            Brush.radialGradient(listOf(tone.copy(alpha = strength * 0.6f), Color.Transparent), c, size.width * 0.75f),
            radius = size.width * 0.75f,
            center = c,
        )
    }
}
