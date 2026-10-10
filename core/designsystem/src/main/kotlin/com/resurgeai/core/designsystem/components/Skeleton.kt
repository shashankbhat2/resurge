package com.resurgeai.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge

/** Shared shimmer phase so every placeholder on screen sweeps in sync. */
private val LocalShimmerPhase = compositionLocalOf { 0f }

/**
 * Wrap a group of [SkeletonBox]es so they shimmer together. A soft highlight sweeps left → right
 * across the placeholders every ~1.2s.
 */
@Composable
fun SkeletonGroup(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-phase",
    )
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

/** A placeholder block (text line, circle, chart) used while real content loads. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = Resurge.shape.sm) {
    val base = Resurge.colors.surfaceMuted
    val highlight = Resurge.colors.border
    val phase = LocalShimmerPhase.current
    Box(
        modifier
            .clip(shape)
            .background(base)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(phase * 1000f - 500f, 0f),
                    end = Offset(phase * 1000f + 500f, 0f),
                ),
            ),
    )
}

/** A text-line placeholder of the given width and line height. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 12.dp) =
    SkeletonBox(Modifier.width(width).height(height), Resurge.shape.pill)

/** A circular placeholder (e.g. for a ring or avatar). */
@Composable
fun SkeletonCircle(size: Dp) = SkeletonBox(Modifier.size(size), Resurge.shape.pill)
