package com.resurgeai.feature.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Stack of today's pending habits. Drag the top card right for done, left for not done
 * (or use the buttons). Cards tilt and tint while dragged; a haptic tick marks the threshold.
 */
@Composable
internal fun HabitDeck(
    state: HabitsUiState,
    onSwipe: (String, Boolean) -> Unit,
    onUndo: () -> Unit,
    onShowStreaks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = state.pending
    // Buttons ask the top card to fly off in a direction.
    val fling = remember { mutableStateOf<Boolean?>(null) }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${pending.size} left today · ${state.doneToday} done",
            style = Resurge.type.label,
            color = Resurge.colors.textMuted,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val visible = pending.take(3)
            visible.indices.reversed().forEach { depth ->
                val item = visible[depth]
                key(item.habit.id) {
                    val behind = Modifier.graphicsLayer {
                        val scale = 1f - depth * 0.05f
                        scaleX = scale
                        scaleY = scale
                        translationY = depth * 18.dp.toPx()
                    }
                    if (depth == 0) {
                        SwipeCard(item, fling, onSwipe = { done -> onSwipe(item.habit.id, done) })
                    } else {
                        HabitCardFace(item, behind)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundAction(ResurgeIcons.Close, "Not done", Resurge.colors.surface, Resurge.colors.text) { fling.value = false }
            RoundAction(ResurgeIcons.Undo, "Undo", Resurge.colors.surfaceMuted, Resurge.colors.textMuted, size = 48, enabled = state.lastSwiped != null, onClick = onUndo)
            RoundAction(ResurgeIcons.Check, "Done", Resurge.colors.accent, Resurge.colors.onAccent) { fling.value = true }
        }
        Spacer(Modifier.height(8.dp))
        Button("View streaks", onClick = onShowStreaks, variant = ButtonVariant.Ghost)
    }
}

@Composable
private fun SwipeCard(item: HabitItem, fling: MutableState<Boolean?>, onSwipe: (Boolean) -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableFloatStateOf(1f) }
    var pastThreshold by remember { mutableStateOf(false) }
    val threshold = width * 0.33f

    suspend fun flyOff(done: Boolean) {
        offset.animateTo((if (done) 1f else -1f) * width * 1.4f, tween(220))
        onSwipe(done)
    }

    // Button presses: listen for requests; clearing the request doesn't cancel the running fly-off.
    LaunchedEffect(Unit) {
        snapshotFlow { fling.value }.filterNotNull().collect { done ->
            fling.value = null
            flyOff(done)
        }
    }

    val progress = (offset.value / threshold).coerceIn(-1f, 1f)
    HabitCardFace(
        item,
        Modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * 10f
            }
            .pointerInput(item.habit.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) > threshold) flyOff(offset.value > 0)
                            else offset.animateTo(0f, spring())
                        }
                        pastThreshold = false
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch {
                        offset.snapTo(offset.value + drag.x)
                        val past = abs(offset.value) > threshold
                        if (past != pastThreshold) {
                            pastThreshold = past
                            if (past) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                    }
                }
            },
        swipeProgress = progress,
    )
}

/** The card itself; [swipeProgress] in -1..1 tints it towards not done / done. */
@Composable
private fun HabitCardFace(item: HabitItem, modifier: Modifier = Modifier, swipeProgress: Float = 0f) {
    val tint = HabitVisuals.color(item.habit.color)
    val doneTint = Resurge.colors.success
    val shape = Resurge.shape.lg
    Box(
        modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(shape)
            .background(Resurge.colors.surface)
            .border(1.dp, Resurge.colors.border, shape),
    ) {
        // Drag feedback wash.
        if (swipeProgress != 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .background((if (swipeProgress > 0) doneTint else Resurge.colors.textSubtle).copy(alpha = abs(swipeProgress) * 0.16f)),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(Resurge.shape.pill)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(HabitVisuals.icon(item.habit.icon), contentDescription = null, tint = tint, size = 40.dp) }
            Spacer(Modifier.height(24.dp))
            Text(item.habit.name, style = Resurge.type.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            StreakLine(item.stats.current, item.stats.best)
        }
        // Direction labels while dragging.
        if (abs(swipeProgress) > 0.15f) {
            val done = swipeProgress > 0
            Row(
                Modifier
                    .align(if (done) Alignment.TopStart else Alignment.TopEnd)
                    .padding(20.dp)
                    .clip(Resurge.shape.pill)
                    .background(if (done) doneTint else Resurge.colors.surfaceMuted)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (done) Color.White else Resurge.colors.text
                Icon(if (done) ResurgeIcons.Check else ResurgeIcons.Close, contentDescription = null, tint = fg, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(if (done) "Done" else "Not done", style = Resurge.type.label, color = fg)
            }
        }
    }
}

@Composable
internal fun StreakLine(current: Int, best: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (current > 0) ResurgeIcons.FlameFilled else ResurgeIcons.Flame,
            contentDescription = null,
            tint = if (current > 0) Resurge.colors.energy else Resurge.colors.textSubtle,
            size = 20.dp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (current > 0) "$current day streak" else "Start a streak today",
            style = Resurge.type.label,
        )
        if (best > 0) {
            Text("  ·  Best $best", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
    }
}

@Composable
private fun RoundAction(
    icon: Int,
    label: String,
    background: Color,
    tint: Color,
    size: Int = 64,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        modifier = Modifier
            .size(size.dp)
            .clip(Resurge.shape.pill)
            .background(background)
            .border(1.dp, Resurge.colors.border, Resurge.shape.pill)
            .semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else Resurge.colors.textSubtle, size = (size * 0.4f).dp)
    }
}
