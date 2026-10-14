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
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.designsystem.components.ProgressFlame
import com.resurgeai.core.designsystem.theme.Resurge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** This week's progress toward the weekly goal, for the flame beside the profile photo. */
data class WeeklyFlameState(val day: LocalDate = LocalDate.now(), val habits: List<TodayHabit> = emptyList()) {
    val fullDays: Int get() = TideMath.ascent(habits, day).fullDaysThisWeek.coerceAtMost(TideMath.STRONG_WEEK_DAYS)
    val fraction: Float get() = fullDays / TideMath.STRONG_WEEK_DAYS.toFloat()
}

@HiltViewModel
class WeeklyFlameViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    habitsRepository: HabitsRepository,
) : ViewModel() {
    private val clock = MutableStateFlow(Instant.now())

    val state: StateFlow<WeeklyFlameState> = todayHabits(sessionRepository, habitsRepository, clock)
        .map { (day, habits) -> WeeklyFlameState(day, habits) }
        .catch { emit(WeeklyFlameState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyFlameState())

    /** Re-reads the habit day on resume, so the week rolls over at the reset time. */
    fun tick() {
        clock.value = Instant.now()
    }
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
            .semantics { contentDescription = "Weekly goal: ${state.fullDays} of ${TideMath.STRONG_WEEK_DAYS} full days" },
        contentAlignment = Alignment.Center,
    ) {
        // Faint outline for the part of the week still to fill, so 4 of 5 doesn't read as full.
        ProgressFlame(state.fraction, size = size * 0.55f, fill = Resurge.colors.onGlow, track = Resurge.colors.onGlow.copy(alpha = 0.35f))
    }
    if (open) {
        BottomSheetDialog(onDismiss = { open = false }) { WeekSheet(state.habits, state.day, onDone = { open = false }) }
    }
}
