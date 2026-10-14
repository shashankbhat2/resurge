package com.resurgeai.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import com.composeunstyled.Text
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideContentColor
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

/** Full-screen background. Insets are handled by the caller (scaffold or screen). */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Resurge.colors.bg),
    ) {
        ProvideContentColor(Resurge.colors.text) { content() }
    }
}

/**
 * Flat card: surface fill, 28dp corners, no shadow. A hairline border keeps cards separate from the
 * background; selection uses the inverse (navbar) colour. Clickable when [onClick] is set.
 */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    inverse: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Resurge.shape.lg
    val borderColor = when {
        selected -> Resurge.colors.strong
        else -> Resurge.colors.border.copy(alpha = 0.6f)
    }
    val container = if (inverse) Resurge.colors.inverse else Resurge.colors.surface
    val contentColor = if (inverse) Resurge.colors.onInverse else Resurge.colors.text
    val base = modifier
        .clip(shape)
        .background(container, shape)
        .border(if (selected) 1.5.dp else 1.dp, borderColor, shape)

    if (onClick != null) {
        UnstyledButton(
            onClick = onClick,
            modifier = base,
            indication = LocalIndication.current,
            contentAlignment = Alignment.TopStart,
        ) {
            ProvideContentColor(contentColor) {
                Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
            }
        }
    } else {
        ProvideContentColor(contentColor) {
            Column(base.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, color: Color = Resurge.colors.border) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/** Indeterminate circular spinner. */
@Composable
fun Spinner(
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = Resurge.colors.accent,
    strokeWidth: Dp = 2.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Canvas(modifier) {
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Small tinted circle holding an icon (same shape language as the navbar), for metric and goal accents. */
@Composable
fun IconBadge(
    icon: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = tint.copy(alpha = 0.12f),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(Resurge.shape.pill)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, size = size * 0.5f)
    }
}

/**
 * A gentle, non-blocking note under a choice the person can still make (e.g. a low sleep goal):
 * `glowTint` ground, an info icon in `glowInk`, body text in `text`.
 */
@Composable
fun SoftNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Resurge.shape.sm)
            .background(Resurge.colors.glowTint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(ResurgeIcons.AlertCircle, contentDescription = null, tint = Resurge.colors.glowInk, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = Resurge.type.caption.copy(lineHeight = Resurge.type.label.lineHeight), color = Resurge.colors.text)
    }
}
