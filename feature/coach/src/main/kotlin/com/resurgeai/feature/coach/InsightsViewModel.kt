package com.resurgeai.feature.coach

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class InsightFilter(val label: String) {
    ALL("All"), WEEKLY("Weekly"), WORKOUTS("Workouts"), SLEEP("Sleep"), CHATS("Chats");

    fun matches(i: CoachInsight): Boolean = when (this) {
        ALL -> true
        WEEKLY -> i.kind == InsightKind.WEEKLY
        WORKOUTS -> i.kind == InsightKind.ACTIVITY
        SLEEP -> i.kind == InsightKind.SLEEP
        CHATS -> false
    }

    companion object {
        fun fromName(name: String?): InsightFilter = entries.firstOrNull { it.name == name } ?: ALL
    }
}

data class InsightsUiState(
    val insights: List<CoachInsight> = emptyList(),
    val threads: List<CoachThread> = emptyList(),
    val loaded: Boolean = false,
    val filter: InsightFilter = InsightFilter.ALL,
    /** Whose voice the notes are in. */
    val coachStyle: CoachStyle = CoachStyle.FRIEND,
    /** For "Week 4" labels and whether an area moved the right way. */
    val goal: MajorGoal? = null,
) {
    val shown: List<CoachInsight> get() = insights.filter(filter::matches)
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    repository: CoachRepository,
    sessionRepository: SessionRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val filter = MutableStateFlow(InsightFilter.fromName(savedState.get<String>(ARG_FILTER)))

    private val profile = sessionRepository.session
        .map { (it as? SessionState.Ready)?.profile }
        .onStart { emit(null) }

    val state: StateFlow<InsightsUiState> = combine(
        repository.insights(limit = 100),
        repository.threads().catch { emit(emptyList()) },
        profile,
        filter,
    ) { insights, threads, p, f ->
        InsightsUiState(insights, threads, loaded = true, filter = f, coachStyle = p?.coachStyle ?: CoachStyle.FRIEND, goal = p?.majorGoal)
    }
        .catch { emit(InsightsUiState(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setFilter(f: InsightFilter) { filter.value = f }

    companion object {
        /** Must match the property name in the navigation route. */
        const val ARG_FILTER = "filter"
    }
}
