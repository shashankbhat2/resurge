package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipe left past a third of the width to delete. A red wash with a trash icon shows through while
 * dragging; screen readers get a "Delete" action instead.
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableFloatStateOf(1f) }
    var past by remember { mutableStateOf(false) }
    val threshold = width * 0.33f

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true }) },
    ) {
        if (offset.value < 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(Resurge.shape.lg)
                    .background(Resurge.colors.danger.copy(alpha = (abs(offset.value) / threshold).coerceIn(0f, 1f) * 0.9f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(ResurgeIcons.Archive, contentDescription = null, tint = Color.White, size = 22.dp, modifier = Modifier.padding(end = 24.dp))
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -threshold) {
                                    offset.animateTo(-width * 1.2f, tween(180))
                                    onDelete()
                                    offset.snapTo(0f)
                                } else {
                                    offset.animateTo(0f, spring())
                                }
                            }
                            past = false
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + drag).coerceAtMost(0f))
                            val now = offset.value < -threshold
                            if (now != past) {
                                past = now
                                if (now) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                        }
                    }
                },
        ) { content() }
    }
}

/**
 * Floating "Deleted · Undo" pill. Shown while [message] is non-null and hidden again after a few
 * seconds via [onTimeout].
 */
@Composable
fun UndoBar(message: String?, onUndo: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(5_000)
        onTimeout()
    }
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.strong)
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = Resurge.type.label, color = Resurge.colors.bg, modifier = Modifier.weight(1f))
        UnstyledButton(
            onClick = onUndo,
            role = Role.Button,
            modifier = Modifier.clip(Resurge.shape.pill),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            indication = LocalIndication.current,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.Undo, contentDescription = null, tint = Resurge.colors.bg, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Undo", style = Resurge.type.label, color = Resurge.colors.bg)
            }
        }
    }
}

/**
 * A labelled − value + row for numbers that may be unset ([value] null shows "—"; − from the
 * minimum clears it when [optional]). [format] renders the value (unit included).
 */
@Composable
fun ValueStepper(
    label: String,
    value: Double?,
    onValueChange: (Double?) -> Unit,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    optional: Boolean = true,
    /** Where + starts from when the value is unset. */
    start: Double = range.start,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .border(1.dp, Resurge.colors.border.copy(alpha = 0.6f), Resurge.shape.md)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Resurge.colors.textMuted, size = 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
        MiniStep("−", "Decrease $label", enabled = value != null && (optional || value - step >= range.start)) {
            val v = value ?: return@MiniStep
            val next = v - step
            onValueChange(if (next < range.start - 1e-9) (if (optional) null else range.start) else round(next, step))
        }
        Text(
            value?.let(format) ?: "—",
            style = Resurge.type.title.copy(fontFeatureSettings = "tnum"),
            color = if (value != null) Resurge.colors.text else Resurge.colors.textSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(112.dp),
            maxLines = 1,
        )
        MiniStep("+", "Increase $label", enabled = value == null || value + step <= range.endInclusive + 1e-9) {
            onValueChange(if (value == null) start else round((value + step).coerceAtMost(range.endInclusive), step))
        }
    }
}

private fun round(v: Double, step: Double): Double = (v / step).roundToInt() * step

@Composable
private fun MiniStep(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        modifier = Modifier
            .size(40.dp)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted)
            .semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) {
        Text(symbol, style = Resurge.type.title, color = if (enabled) Resurge.colors.text else Resurge.colors.textSubtle)
    }
}

/** Small pill with an icon and a value, used for metrics and macros on feed cards. */
@Composable
fun MetricChip(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, tint: Color = Resurge.colors.textMuted) {
    Row(
        modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, size = 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.text, maxLines = 1)
    }
}

/** Big tappable option with an icon, title and caption (e.g. "Scan meal"). */
@Composable
fun OptionTile(
    @DrawableRes icon: Int,
    title: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Resurge.colors.accent,
) {
    Card(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint = tint, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Resurge.type.title)
                Spacer(Modifier.height(2.dp))
                Text(caption, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 20.dp)
        }
    }
}
