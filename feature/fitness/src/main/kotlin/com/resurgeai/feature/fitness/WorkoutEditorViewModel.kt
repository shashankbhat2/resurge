package com.resurgeai.feature.fitness

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.Activity
import com.resurgeai.core.model.ActivityCatalog
import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutDraft
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** A strength exercise being edited; [key] keeps text fields stable as rows are added and removed. */
data class EditableExercise(val key: Int, val name: String, val sets: List<StrengthSet>)

data class WorkoutEditorState(
    val loaded: Boolean = false,
    val workoutId: String? = null,
    val source: WorkoutSource = WorkoutSource.MANUAL,
    val edited: Boolean = false,
    val sourceName: String? = null,
    val activity: Activity = ActivityCatalog.byId("other"),
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().truncatedTo(ChronoUnit.HOURS),
    val durationMin: Int = 30,
    val metrics: WorkoutMetricsInput = WorkoutMetricsInput(),
    val exercises: List<EditableExercise> = emptyList(),
    /** Calories follow duration (MET estimate) until the user sets them. */
    val kcalAuto: Boolean = true,
    val notes: String = "",
    val units: UnitSystem = UnitSystem.METRIC,
    val done: Boolean = false,
    val notFound: Boolean = false,
) {
    val isNew: Boolean get() = workoutId == null
}

/**
 * Log (activityId) or edit (workoutId) a workout. Editing a Health Connect session saves the
 * user's version on top of it; "Reset" brings back the device values.
 */
@HiltViewModel
class WorkoutEditorViewModel @Inject constructor(
    private val repository: WorkoutsRepository,
    sessionRepository: SessionRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val _state = MutableStateFlow(WorkoutEditorState())
    val state: StateFlow<WorkoutEditorState> = _state.asStateFlow()
    private var weightKg: Double? = null
    private var nextKey = 0

    init {
        val workoutId = savedState.get<String>("workoutId")
        val activityId = savedState.get<String>("activityId")
        viewModelScope.launch {
            val units = runCatching {
                sessionRepository.session.filterIsInstance<SessionState.Ready>().first().profile.units
            }.getOrDefault(UnitSystem.METRIC)
            weightKg = repository.latestWeightKg()
            if (workoutId != null) {
                val w = repository.find(workoutId)
                if (w == null) {
                    _state.value = WorkoutEditorState(loaded = true, notFound = true, units = units)
                    return@launch
                }
                val local = w.start.atZone(zone)
                _state.value = WorkoutEditorState(
                    loaded = true,
                    workoutId = w.id,
                    source = w.source,
                    edited = w.edited,
                    sourceName = w.sourceName,
                    activity = w.activity,
                    title = w.title.orEmpty(),
                    date = local.toLocalDate(),
                    time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    durationMin = w.duration.toMinutes().toInt().coerceAtLeast(1),
                    metrics = w.metrics,
                    exercises = w.metrics.exercises.map { EditableExercise(nextKey++, it.name, it.sets) },
                    kcalAuto = w.metrics.kcal == null,
                    notes = w.notes.orEmpty(),
                    units = units,
                )
                if (w.metrics.kcal == null) recomputeKcal()
            } else {
                val activity = ActivityCatalog.byId(activityId)
                val now = LocalTime.now()
                _state.value = WorkoutEditorState(
                    loaded = true,
                    activity = activity,
                    time = now.minusMinutes(defaultDuration(activity).toLong()).truncatedTo(ChronoUnit.MINUTES)
                        .let { it.withMinute(it.minute / 5 * 5) },
                    durationMin = defaultDuration(activity),
                    exercises = if (activity.category == ActivityCategory.STRENGTH) listOf(EditableExercise(nextKey++, "", listOf(StrengthSet(10)))) else emptyList(),
                    metrics = defaultMetrics(activity),
                    units = units,
                )
                recomputeKcal()
            }
        }
    }

    private fun defaultDuration(a: Activity) = when (a.category) {
        ActivityCategory.STRENGTH -> 45
        ActivityCategory.COUNT -> 10
        else -> 30
    }

    private fun defaultMetrics(a: Activity) = when (a.category) {
        ActivityCategory.SWIM -> WorkoutMetricsInput(poolLengthM = 25)
        ActivityCategory.TIMED -> WorkoutMetricsInput(rpe = 6)
        else -> WorkoutMetricsInput()
    }

    private fun recomputeKcal() = _state.update { s ->
        if (!s.kcalAuto) s
        else s.copy(metrics = s.metrics.copy(kcal = FitnessMath.estimateKcal(s.activity, s.durationMin, weightKg)))
    }

    fun setActivity(id: String) {
        val activity = ActivityCatalog.byId(id)
        _state.update { s ->
            val exercises = if (activity.category == ActivityCategory.STRENGTH && s.exercises.isEmpty()) {
                listOf(EditableExercise(nextKey++, "", listOf(StrengthSet(10))))
            } else s.exercises
            s.copy(activity = activity, exercises = exercises)
        }
        recomputeKcal()
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setTime(time: LocalTime) = _state.update { it.copy(time = time) }
    fun setNotes(notes: String) = _state.update { it.copy(notes = notes) }

    fun setDuration(minutes: Int) {
        _state.update { it.copy(durationMin = minutes.coerceIn(1, 600)) }
        recomputeKcal()
    }

    fun setKcal(kcal: Int?) = _state.update { s ->
        s.copy(kcalAuto = kcal == null, metrics = s.metrics.copy(kcal = kcal))
    }.also { if (kcal == null) recomputeKcal() }

    fun updateMetrics(transform: (WorkoutMetricsInput) -> WorkoutMetricsInput) = _state.update { it.copy(metrics = transform(it.metrics)) }

    // Strength
    fun addExercise() = _state.update { it.copy(exercises = it.exercises + EditableExercise(nextKey++, "", listOf(StrengthSet(10)))) }
    fun removeExercise(key: Int) = _state.update { s -> s.copy(exercises = s.exercises.filterNot { it.key == key }) }
    fun renameExercise(key: Int, name: String) = editExercise(key) { it.copy(name = name) }

    /** Adds a set copying the previous one, the usual way people log. */
    fun addSet(key: Int) = editExercise(key) { e -> e.copy(sets = e.sets + (e.sets.lastOrNull() ?: StrengthSet(10))) }
    fun updateSet(key: Int, index: Int, set: StrengthSet) = editExercise(key) { e ->
        e.copy(sets = e.sets.mapIndexed { i, s -> if (i == index) set else s })
    }
    fun removeSet(key: Int, index: Int) = editExercise(key) { e -> e.copy(sets = e.sets.filterIndexed { i, _ -> i != index }) }

    private fun editExercise(key: Int, transform: (EditableExercise) -> EditableExercise) = _state.update { s ->
        s.copy(exercises = s.exercises.map { if (it.key == key) transform(it) else it })
    }

    fun save() {
        val s = _state.value
        val exercises = s.exercises
            .filter { it.sets.isNotEmpty() }
            .map { StrengthExercise(it.name.trim().ifEmpty { "Exercise" }, it.sets) }
        val metrics = when (s.activity.category) {
            ActivityCategory.STRENGTH -> s.metrics.copy(exercises = exercises)
            ActivityCategory.SWIM -> s.metrics.copy(distanceM = FitnessMath.swimDistanceM(s.metrics.copy(distanceM = null)) ?: s.metrics.distanceM, exercises = emptyList())
            else -> s.metrics.copy(exercises = emptyList())
        }
        val draft = WorkoutDraft(
            activityId = s.activity.id,
            start = s.date.atTime(s.time).atZone(zone).toInstant(),
            durationMin = s.durationMin,
            metrics = metrics,
            title = s.title.takeIf { it.isNotBlank() },
            notes = s.notes.takeIf { it.isNotBlank() },
        )
        repository.save(draft, s.workoutId, s.source)
        _state.update { it.copy(done = true) }
    }

    /** Deletes a manual workout, or hides a Health Connect session from the feed. */
    fun delete() {
        val s = _state.value
        val id = s.workoutId ?: return
        val start = s.date.atTime(s.time).atZone(zone).toInstant()
        repository.delete(
            Workout(id, s.source, s.activity.id, s.title.ifBlank { null }, start, start.plusSeconds(s.durationMin * 60L), s.metrics, edited = s.edited),
        )
        _state.update { it.copy(done = true) }
    }

    /** Drops the user's edits to a Health Connect session. */
    fun resetToDevice() {
        val id = _state.value.workoutId ?: return
        repository.resetToDevice(id)
        _state.update { it.copy(done = true) }
    }
}
