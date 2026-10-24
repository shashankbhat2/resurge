package com.resurgeai.feature.fitness

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WorkoutSource
import com.resurgeai.core.model.dayOfWeekFromWire
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class LoggedSet(val reps: Int, val weightKg: Double?, val done: Boolean = false)

data class LoggedExercise(val name: String, val last: List<StrengthSet>?, val sets: List<LoggedSet>)

data class SessionLoggerState(
    val loaded: Boolean = false,
    val day: PlanDay? = null,
    val exercises: List<LoggedExercise> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val startedAt: Instant = Instant.now(),
    /** No plan, or no session on that day. */
    val notFound: Boolean = false,
    val saved: Boolean = false,
) {
    val total: Int get() = exercises.sumOf { it.sets.size }
    val doneCount: Int get() = exercises.sumOf { e -> e.sets.count { it.done } }
}

/**
 * Logs today's planned strength session a tap per set. Every set starts filled in from the plan
 * (reps) and the last time the exercise was logged (weight); only a set that differs needs editing.
 */
@HiltViewModel
class SessionLoggerViewModel @Inject constructor(
    private val workouts: WorkoutsRepository,
    private val plans: PlanRepository,
    sessionRepository: SessionRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionLoggerState())
    val state: StateFlow<SessionLoggerState> = _state.asStateFlow()

    init {
        val weekday = dayOfWeekFromWire(savedState.get<String>("day"))
        viewModelScope.launch {
            val profile = sessionRepository.session.filterIsInstance<SessionState.Ready>().first().profile
            val day = profile.activePlanId?.let { id -> runCatching { plans.plan(id) }.getOrNull() }
                ?.days?.firstOrNull { it.day == weekday && !it.rest }
            if (day == null) {
                _state.update { it.copy(loaded = true, notFound = true) }
                return@launch
            }
            val now = Instant.now()
            val history = runCatching { workouts.records(now.minus(Duration.ofDays(FitnessViewModel.HISTORY_DAYS))).first() }.getOrDefault(emptyList())
            val last = LastSession.find(history, PlanLogging.activityId(day), now, null)
            val planned = PlanLogging.plannedExercises(day, history, now)
            _state.update {
                it.copy(
                    loaded = true,
                    day = day,
                    units = profile.units,
                    exercises = planned.map { e ->
                        LoggedExercise(e.name, last[LastSession.key(e.name)], e.sets.map { s -> LoggedSet(s.reps, s.weightKg) })
                    },
                )
            }
        }
    }

    fun toggle(exercise: Int, set: Int) = editSet(exercise, set) { it.copy(done = !it.done) }

    fun setReps(exercise: Int, set: Int, reps: Int) = editSet(exercise, set) { it.copy(reps = reps.coerceIn(1, 200)) }

    /** Null for bodyweight. */
    fun setWeight(exercise: Int, set: Int, kg: Double?) = editSet(exercise, set) { it.copy(weightKg = kg?.takeIf { w -> w > 0 }?.coerceAtMost(1000.0)) }

    /** Adds a set like the last one, not yet done. */
    fun addSet(exercise: Int) = _state.update { s ->
        s.copy(exercises = s.exercises.mapIndexed { i, e -> if (i == exercise) e.copy(sets = e.sets + (e.sets.lastOrNull()?.copy(done = false) ?: LoggedSet(10, null))) else e })
    }

    /** Marks every set done, as planned. */
    fun allDone() = _state.update { s -> s.copy(exercises = s.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(done = true) }) }) }

    /**
     * Saves the finished sets as one workout ending now. Its length is the time since the logger
     * opened; when that's under ten minutes (logged afterwards in one go), the planned length.
     */
    fun finish() {
        val s = _state.value
        val day = s.day ?: return
        if (s.doneCount == 0) return
        val now = Instant.now()
        val elapsed = Duration.between(s.startedAt, now).toMinutes().toInt()
        val minutes = if (elapsed >= 10) elapsed else day.durationMin ?: PlanLogging.DEFAULT_MINUTES
        val exercises = s.exercises.map { e -> StrengthExercise(e.name, e.sets.filter { it.done }.map { StrengthSet(it.reps, it.weightKg) }) }
        viewModelScope.launch {
            workouts.save(PlanLogging.draft(day, now, minutes, exercises, workouts.latestWeightKg()), null, WorkoutSource.MANUAL)
            _state.update { it.copy(saved = true) }
        }
    }

    private fun editSet(exercise: Int, set: Int, transform: (LoggedSet) -> LoggedSet) = _state.update { s ->
        s.copy(
            exercises = s.exercises.mapIndexed { i, e ->
                if (i != exercise) e else e.copy(sets = e.sets.mapIndexed { j, x -> if (j == set) transform(x) else x })
            },
        )
    }
}
