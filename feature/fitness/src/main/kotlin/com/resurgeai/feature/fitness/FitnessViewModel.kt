package com.resurgeai.feature.fitness

import com.resurgeai.core.model.Backfill
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import com.resurgeai.core.model.WorkoutSource
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutDraft
import com.resurgeai.core.model.WorkoutPlan
import com.resurgeai.core.model.PlanMatch
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.ActivityCatalog
import com.resurgeai.core.model.Activity
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.model.ActivityCategory
import com.resurgeai.core.model.FitnessMath
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

/** A day of this week's plan and the workout that did it, if one has. */
data class PlannedDay(val date: LocalDate, val day: PlanDay, val workout: Workout?) {
    val done: Boolean get() = workout != null
}

data class FitnessUiState(
    val loading: Boolean = true,
    val days: List<Pair<LocalDate, List<Workout>>> = emptyList(),
    val week: WeekSummary = WeekSummary(0, 0, 0),
    val bests: Map<String, PersonalBest> = emptyMap(),
    val units: UnitSystem = UnitSystem.METRIC,
    val today: LocalDate = LocalDate.now(),
    /** Set right after a delete, for the undo bar. */
    val deleted: Workout? = null,
    /** The plan being followed, and this week of it (Monday first); empty without a plan. */
    val plan: WorkoutPlan? = null,
    val planWeek: List<PlannedDay> = emptyList(),
    /** What quick add offers first: the most logged activities lately, then common ones. */
    val recents: List<Activity> = QuickLog.DEFAULTS.map(ActivityCatalog::byId),
) {
    val todayPlan: PlannedDay? get() = planWeek.firstOrNull { it.date == today }
    val sessionsPlanned: Int get() = planWeek.count { !it.day.rest }
    val sessionsDone: Int get() = planWeek.count { !it.day.rest && it.done }
}

/** Quick add: a few activities and durations, so logging is two taps. */
object QuickLog {
    val DEFAULTS = listOf("walk", "run", "cycle", "weights", "yoga", "swim_pool")
    val MINUTES = listOf(15, 30, 45, 60, 90)

    /** When a workout on an earlier day ended: morning, midday or evening. */
    val PAST_ENDS = listOf(java.time.LocalTime.of(9, 0) to "Morning", java.time.LocalTime.of(13, 0) to "Midday", java.time.LocalTime.of(19, 0) to "Evening")
    val EVENING: java.time.LocalTime = PAST_ENDS.last().first

    /** For today: how long ago it ended, in minutes. */
    val TODAY_ENDS = listOf(0 to "Just now", 60 to "1 h ago", 180 to "3 h ago")

    /** The six most logged activities in [workouts], topped up with [DEFAULTS]. */
    fun recents(workouts: List<Workout>): List<String> =
        (workouts.groupingBy { it.activityId }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.filter { it != "other" } + DEFAULTS)
            .distinct()
            .take(6)

    /** Matches each planned day of [weekStart]'s week to the first workout that day that fits it. */
    fun planWeek(plan: WorkoutPlan, weekStart: LocalDate, workouts: List<Workout>, zone: ZoneId): List<PlannedDay> =
        plan.days.map { d ->
            val date = weekStart.with(java.time.temporal.TemporalAdjusters.nextOrSame(d.day))
            PlannedDay(date, d, workouts.firstOrNull { it.date(zone) == date && PlanMatch.matches(d, it) })
        }.sortedBy { it.date }
}

/** Fitness feed: the last [DAYS] days of Health Connect sessions and logged workouts, newest first. */
@HiltViewModel
class FitnessViewModel @Inject constructor(
    private val repository: WorkoutsRepository,
    sessionRepository: SessionRepository,
    private val plans: PlanRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(DAYS).atStartOfDay(zone).toInstant()
    private val device = MutableStateFlow<List<Workout>?>(null)
    private val deleted = MutableStateFlow<Workout?>(null)

    private val units = sessionRepository.session
        .filterIsInstance<SessionState.Ready>()
        .map { it.profile.units }

    private val records = repository.records(since).catch { emit(emptyList<WorkoutRecord>()) }

    private val plan = sessionRepository.session
        .map { (it as? SessionState.Ready)?.profile?.activePlanId }
        .distinctUntilChanged()
        .map { id -> id?.let { runCatching { plans.plan(it) }.getOrNull() } }

    val state: StateFlow<FitnessUiState> = combine(device, records, units, deleted, plan) { dev, recs, u, del, activePlan ->
        val today = LocalDate.now(zone)
        if (dev == null) return@combine FitnessUiState(loading = true, units = u, today = today, plan = activePlan)
        val all = WorkoutsRepository.merge(dev, recs)
        val weekStart = HabitStreaks.weekStart(today)
        val thisWeek = all.filter { !it.date(zone).isBefore(weekStart) }
        val byDay = thisWeek.groupBy { it.date(zone) }
        FitnessUiState(
            loading = false,
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
            plan = activePlan,
            // The plan's week runs Monday to Sunday, whatever the locale's first day.
            planWeek = activePlan?.let { QuickLog.planWeek(it, today.with(java.time.DayOfWeek.MONDAY), all, zone) }.orEmpty(),
            recents = QuickLog.recents(all).map(ActivityCatalog::byId),
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

    /**
     * Logs [day] as planned: its exercises at last time's weights, ending now on [date] today, or
     * in the evening of an earlier day of the backfill week.
     */
    fun doneAsPlanned(day: PlanDay, date: LocalDate = LocalDate.now(zone)) {
        if (!Backfill.allows(date, LocalDate.now(zone))) return
        viewModelScope.launch {
            val now = if (date == LocalDate.now(zone)) Instant.now() else date.atTime(QuickLog.EVENING).atZone(zone).toInstant()
            val history = runCatching { repository.records(now.minus(Duration.ofDays(HISTORY_DAYS))).first() }.getOrDefault(emptyList())
            val minutes = day.durationMin ?: PlanLogging.DEFAULT_MINUTES
            val exercises = PlanLogging.plannedExercises(day, history, now)
            repository.save(PlanLogging.draft(day, now, minutes, exercises, repository.latestWeightKg()), null, WorkoutSource.MANUAL)
        }
    }

    /** Quick add: [activityId] for [minutes], ending at [end] (somewhere in the backfill week). */
    fun quickLog(activityId: String, minutes: Int, end: Instant) {
        if (!Backfill.allows(end.atZone(zone).toLocalDate(), LocalDate.now(zone))) return
        viewModelScope.launch {
            val activity = ActivityCatalog.byId(activityId)
            val draft = WorkoutDraft(
                activityId = activity.id,
                start = end.minus(Duration.ofMinutes(minutes.toLong())),
                durationMin = minutes,
                metrics = WorkoutMetricsInput(kcal = FitnessMath.estimateKcal(activity, minutes, repository.latestWeightKg())),
            )
            repository.save(draft, null, WorkoutSource.MANUAL)
        }
    }

    companion object {
        const val DAYS = 30L
        const val HISTORY_DAYS = 120L
    }
}
