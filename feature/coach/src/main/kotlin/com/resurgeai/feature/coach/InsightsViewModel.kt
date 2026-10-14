package com.resurgeai.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InsightsUiState(
    val insights: List<CoachInsight> = emptyList(),
    val loaded: Boolean = false,
    /** Whose voice the notes are in: sets the blob and note styling. */
    val coachStyle: CoachStyle = CoachStyle.FRIEND,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(repository: CoachRepository, sessionRepository: SessionRepository) : ViewModel() {
    private val style = sessionRepository.session
        .map { (it as? SessionState.Ready)?.profile?.coachStyle ?: CoachStyle.FRIEND }
        .onStart { emit(CoachStyle.FRIEND) }

    val state: StateFlow<InsightsUiState> = combine(repository.insights(limit = 100), style) { insights, s ->
        InsightsUiState(insights, loaded = true, coachStyle = s)
    }
        .catch { emit(InsightsUiState(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())
}
