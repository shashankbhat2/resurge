package com.resurgeai.core.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge
import kotlinx.coroutines.launch

private val Threshold = 72.dp
private val IndicatorSize = 40.dp

/**
 * Pull down at the top of [content] (a vertically scrolling column) to refresh. A small circle drops
 * in under the header, its arc filling as you pull; past the threshold, letting go calls [onRefresh]
 * and the circle spins until [refreshing] is false again.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the indicator settles, from the top (under a floating header). */
    indicatorTop: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { Threshold.toPx() }
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentRefreshing by rememberUpdatedState(refreshing)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    // Hold the circle at the threshold while refreshing; let it go when done.
    LaunchedEffect(refreshing) {
        if (refreshing) pull.animateTo(thresholdPx) else pull.animateTo(0f)
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up while pulled: take the pull back first.
                if (available.y < 0 && pull.value > 0f && !currentRefreshing) {
                    val used = maxOf(available.y, -pull.value)
                    scope.launch { pull.snapTo(pull.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Dragging down past the top: resist, so it takes a deliberate pull.
                if (available.y > 0 && source == NestedScrollSource.UserInput && !currentRefreshing) {
                    scope.launch { pull.snapTo((pull.value + available.y * 0.5f).coerceAtMost(thresholdPx * 1.6f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentRefreshing || pull.value <= 0f) return Velocity.Zero
                if (pull.value >= thresholdPx) currentOnRefresh() else pull.animateTo(0f)
                return available
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        val progress = (pull.value / thresholdPx).coerceIn(0f, 1f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTop)
                .graphicsLayer {
                    translationY = pull.value - IndicatorSize.toPx()
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
                }
                .size(IndicatorSize)
                .shadow(6.dp, Resurge.shape.pill)
                .clip(Resurge.shape.pill)
                .background(Resurge.colors.navBar)
                .semantics { contentDescription = if (refreshing) "Refreshing" else "Pull to refresh" },
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                Spinner(Modifier.size(20.dp), color = Resurge.colors.text)
            } else {
                val color = Resurge.colors.text
                Canvas(Modifier.size(20.dp)) {
                    val w = 2.5.dp.toPx()
                    drawArc(
                        color, -90f, 300f * progress, false,
                        Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}
