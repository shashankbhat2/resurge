package com.resurgeai.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.fitness.WorkoutRecord
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WeekSummary(val workouts: Int, val activeMinutes: Long, val kcal: Int)

data class FitnessUiState(
    val loading: Boolean = true,
    val days: List<Pair<LocalDate, List<Workout>>> = emptyList(),
    val week: WeekSummary = WeekSummary(0, 0, 0),
    val units: UnitSystem = UnitSystem.METRIC,
    val today: LocalDate = LocalDate.now(),
    /** Set right after a delete, for the undo bar. */
    val deleted: Workout? = null,
)

/** Fitness feed: the last [DAYS] days of Health Connect sessions and logged workouts, newest first. */
@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val repository: WorkoutsRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(DAYS).atStartOfDay(zone).toInstant()
    private val device = MutableStateFlow<List<Workout>?>(null)
    private val deleted = MutableStateFlow<Workout?>(null)

    private val units = sessionRepository.session
        .filterIsInstance<SessionState.Ready>()
        .map { it.profile.units }

    private val records = repository.records(since).catch { emit(emptyList<WorkoutRecord>()) }

    val state: StateFlow<FitnessUiState> = combine(device, records, units, deleted) { dev, recs, u, del ->
        val today = LocalDate.now(zone)
        if (dev == null) return@combine FitnessUiState(loading = true, units = u, today = today)
        val all = WorkoutsRepository.merge(dev, recs)
        val weekStart = HabitStreaks.weekStart(today)
        val thisWeek = all.filter { !it.date(zone).isBefore(weekStart) }
        FitnessUiState(
            loading = false,
            days = all.groupBy { it.date(zone) }.toList().sortedByDescending { it.first },
            week = WeekSummary(
                workouts = thisWeek.size,
                activeMinutes = thisWeek.sumOf { it.duration.toMinutes() },
                kcal = thisWeek.sumOf { it.metrics.kcal ?: 0 },
            ),
            units = u,
            today = today,
            deleted = del,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitnessUiState())

    init {
        refresh()
    }

    /** Re-reads Health Connect (called when the tab resumes). */
    fun refresh() {
        viewModelScope.launch {
            device.value = runCatching { repository.deviceWorkouts(since, Instant.now().plus(Duration.ofMinutes(1))) }
                .getOrDefault(device.value ?: emptyList())
        }
    }

    fun delete(workout: Workout) {
        repository.delete(workout)
        deleted.value = workout
    }

    fun undoDelete() {
        deleted.value?.let(repository::restore)
        deleted.value = null
    }

    fun clearDeleted() = deleted.update { null }

    companion object {
        const val DAYS = 30L
    }
}
