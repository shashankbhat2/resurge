package com.resurgeai.feature.fitness

import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.cardio.CardioPointsRepository
import com.resurgeai.core.data.cardio.CardioPointsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WeekSummary(
    val workouts: Int,
    val activeMinutes: Long,
    val kcal: Int,
    /** Active minutes per day, Sunday to Saturday (the app's week). */
    val dailyMinutes: List<Long> = List(7) { 0L },
    /** Index of today in [dailyMinutes]. */
    val todayIndex: Int = 6,
) {
    val reached: Boolean get() = activeMinutes >= MOVE_WEEK_TARGET

    companion object {
        /** Weekly active minutes to aim for (WHO guideline for adults). */
        const val MOVE_WEEK_TARGET = 150
    }
}

/** Something a workout did better than the user's own recent ones. Compares only with themselves. */
sealed interface PersonalBest {
    /** The fastest pace for this activity in the feed window (at least two to compare). */
    data class FastestPace(val secPerKm: Long) : PersonalBest

    /** More strength volume than the previous session of the same activity. */
    data class MoreVolume(val deltaKg: Double) : PersonalBest
}

object PersonalBests {
    /** Pace needs a real distance to mean anything. */
    private const val MIN_DISTANCE_M = 1_000.0

    /** At most one per workout, keyed by workout id. [all] is the whole feed window. */
    fun find(all: List<Workout>): Map<String, PersonalBest> = buildMap {
        all.filter { it.activity.category == ActivityCategory.DISTANCE && !it.activity.showSpeed }
            .groupBy { it.activityId }
            .values
            .forEach { group ->
                val paced = group.mapNotNull { w ->
                    w.metrics.distanceM?.takeIf { it >= MIN_DISTANCE_M }?.let { FitnessMath.paceSecPerKm(it, w.duration) }?.let { w to it }
                }
                if (paced.size >= 2) {
                    val (best, pace) = paced.minBy { it.second }
                    put(best.id, PersonalBest.FastestPace(pace))
                }
            }
        all.filter { it.activity.category == ActivityCategory.STRENGTH }
            .groupBy { it.activityId }
            .values
            .forEach { group ->
                group.map { it to it.metrics.exercises.sumOf { e -> e.volumeKg } }
                    .filter { it.second > 0 }
                    .sortedBy { it.first.start }
                    .zipWithNext()
                    .forEach { (previous, current) ->
                        if (current.second > previous.second && current.first.id !in this) {
                            put(current.first.id, PersonalBest.MoreVolume(current.second - previous.second))
                        }
                    }
            }
    }
}

data class FitnessUiState(
    val loading: Boolean = true,
    val cardio: CardioPointsState = CardioPointsState(),
    val days: List<Pair<LocalDate, List<Workout>>> = emptyList(),
    val week: WeekSummary = WeekSummary(0, 0, 0),
    val bests: Map<String, PersonalBest> = emptyMap(),
    val units: UnitSystem = UnitSystem.METRIC,
    val today: LocalDate = LocalDate.now(),
    /** Set right after a delete, for the undo bar. */
    val deleted: Workout? = null,
)

/** Fitness feed: the last [DAYS] days of Health Connect sessions and logged workouts, newest first. */
@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val repository: WorkoutsRepository,
    private val cardioRepository: CardioPointsRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val zone get() = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(DAYS).atStartOfDay(zone).toInstant()
    private val device = MutableStateFlow<List<Workout>?>(null)
    private val deleted = MutableStateFlow<Workout?>(null)

    private val units = sessionRepository.session
        .filterIsInstance<SessionState.Ready>()
        .map { it.profile.units }

    private val records = MutableStateFlow<List<WorkoutRecord>?>(null)
    private var deviceReadComplete = true
    private var recordsReadComplete = true
    private var cardioRefresh: Job? = null
    private var deviceRefresh: Job? = null
    private var recordsRefresh: Job? = null

    val state: StateFlow<FitnessUiState> = combine(device, records, units, deleted, cardioRepository.state) { dev, recs, u, del, cardio ->
        val today = LocalDate.now(zone)
        if (dev == null || recs == null) return@combine FitnessUiState(loading = true, units = u, today = today, cardio = cardio)
        val all = WorkoutsRepository.merge(dev, recs)
        val weekStart = HabitStreaks.weekStart(today)
        val thisWeek = all.filter { !it.date(zone).isBefore(weekStart) }
        val byDay = thisWeek.groupBy { it.date(zone) }
        FitnessUiState(
            loading = false,
            cardio = cardio,
            days = all.groupBy { it.date(zone) }.toList().sortedByDescending { it.first },
            week = WeekSummary(
                workouts = thisWeek.size,
                activeMinutes = thisWeek.sumOf { it.duration.toMinutes() },
                kcal = thisWeek.sumOf { it.metrics.kcal ?: 0 },
                dailyMinutes = (0L until 7L).map { i -> byDay[weekStart.plusDays(i)].orEmpty().sumOf { it.duration.toMinutes() } },
                todayIndex = (today.toEpochDay() - weekStart.toEpochDay()).toInt(),
            ),
            bests = PersonalBests.find(all),
            units = u,
            today = today,
            deleted = del,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitnessUiState())

    init {
        watchRecords()
        refresh()
    }

    private fun watchRecords() {
        recordsRefresh?.cancel()
        recordsRefresh = viewModelScope.launch {
            try {
                repository.records(since).collect {
                    recordsReadComplete = true
                    records.value = it
                    device.value?.let { dev -> updateCardio(WorkoutsRepository.merge(dev, it)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                recordsReadComplete = false
                records.value = records.value ?: emptyList()
                device.value?.let { dev -> updateCardio(WorkoutsRepository.merge(dev, records.value.orEmpty())) }
            }
        }
    }

    private fun updateCardio(workouts: List<Workout>, force: Boolean = false) {
        cardioRefresh?.cancel()
        cardioRefresh = viewModelScope.launch {
            cardioRepository.refresh(workouts, deviceReadComplete && recordsReadComplete, force)
        }
    }

    fun retryCardio() {
        // Reattach a failed Firestore listener as well as retrying Health Connect.
        if (!recordsReadComplete) watchRecords()
        refresh(forceCardio = true)
    }

    /** Re-reads Health Connect (called when the tab resumes). */
    fun refresh(forceCardio: Boolean = false) {
        cardioRefresh?.cancel()
        cardioRefresh = viewModelScope.launch { cardioRepository.refresh(emptyList(), cacheOnly = true) }
        deviceRefresh?.cancel()
        deviceRefresh = viewModelScope.launch {
            try {
                val workouts = repository.deviceWorkouts(since, Instant.now())
                deviceReadComplete = true
                device.value = workouts
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                deviceReadComplete = false
                device.value = device.value ?: emptyList()
            }
            records.value?.let { updateCardio(WorkoutsRepository.merge(device.value.orEmpty(), it), forceCardio) }
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
