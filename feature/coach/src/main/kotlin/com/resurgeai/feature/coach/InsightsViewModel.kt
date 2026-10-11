package com.resurgeai.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.model.CoachInsight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InsightsUiState(val insights: List<CoachInsight> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class InsightsViewModel @Inject constructor(repository: CoachRepository) : ViewModel() {
    val state: StateFlow<InsightsUiState> = repository.insights(limit = 100)
        .map { InsightsUiState(it, loaded = true) }
        .catch { emit(InsightsUiState(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())
}
