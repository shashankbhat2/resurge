package com.resurgeai.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resurgeai.core.designsystem.components.ProgressFlame
import com.resurgeai.core.designsystem.theme.Resurge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class WeeklyFlameViewModel @Inject constructor(private val engine: DayStreakEngine) : ViewModel() {
    val state: StateFlow<DayStreakState> = engine.state

    /** Re-reads the habit day on resume, so the week rolls over at the reset time. */
    fun tick() = engine.tick()
}

/**
 * The weekly goal as a dark flame on a `glow` circle, filling as full days add up. The header
 * sets it beside the profile photo, partly tucked behind it; tapping it opens the weekly goal sheet.
 */
@Composable
fun WeeklyGoalFlame(modifier: Modifier = Modifier, size: Dp = 38.dp, viewModel: WeeklyFlameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.tick()
        onPauseOrDispose {}
    }
    var open by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.glow)
            .clickable(onClickLabel = "Show weekly goal") { open = true }
            .semantics { contentDescription = "Weekly goal: ${state.week.metDays} of ${state.week.target} days" },
        contentAlignment = Alignment.Center,
    ) {
        WeeklyFlameIcon((state.week.metDays.coerceAtMost(state.week.target) / state.week.target.toFloat()), size = size * 0.55f)
    }
    if (open) {
        BottomSheetDialog(onDismiss = { open = false }) { WeekSheet(state, onDone = { open = false }) }
    }
}

/**
 * The weekly goal's flame, drawn for a `glow` circle: dark fill rising with [fraction], and a faint
 * outline for the part of the week still to fill (so 4 of 5 doesn't read as full). The header
 * and the weekly goal sheet use it.
 */
@Composable
internal fun WeeklyFlameIcon(fraction: Float, size: Dp) {
    ProgressFlame(fraction, size = size, fill = Resurge.colors.onGlow, track = Resurge.colors.onGlow.copy(alpha = 0.35f))
}
