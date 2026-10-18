package com.resurgeai.feature.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.TrendPoint
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The period the Weight screen measures. */
enum class WeightRange(val label: String, val days: Long?) {
    WEEK("1W", 7),
    MONTH("1M", 30),
    QUARTER("3M", 90),
    YEAR("1Y", 365),
    ALL("All", null),
    CUSTOM("Custom", null),
}

data class WeightUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val units: UnitSystem = UnitSystem.METRIC,
    /** Oldest first. */
    val weighIns: List<WeighIn> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    val goal: WeightGoal? = null,
    val range: WeightRange = WeightRange.MONTH,
    /** The two ends of a custom period; set when [range] is CUSTOM. */
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
) {
    val current: Double? get() = trend.lastOrNull()?.kg

    /** The goal line belongs on long views (on a week or a month it would flatten the trend), and always while maintaining. */
    val showsGoalLine: Boolean get() = goal != null && (goal.maintaining || range in setOf(WeightRange.YEAR, WeightRange.ALL, WeightRange.CUSTOM))

    /** The plan line, only while working toward a goal. */
    val plan: WeightGoal? get() = goal?.takeIf { g -> !g.maintaining && current?.let { !g.reached(it) } == true }
    val firstDate: LocalDate? get() = trend.firstOrNull()?.date

    /** The span the numbers are about. Fixed ranges are clamped to the first weigh-in. */
    val period: Pair<LocalDate, LocalDate>? get() {
        val first = firstDate ?: return null
        return when (range) {
            WeightRange.CUSTOM -> (customFrom ?: first).coerceAtLeast(first) to (customTo ?: today).coerceAtMost(today)
            WeightRange.ALL -> first to today
            else -> today.minusDays(range.days!! - 1).coerceAtLeast(first) to today
        }
    }

    /** The span the chart draws: the period, or everything for a custom period (with handles over it). */
    val chartSpan: Pair<LocalDate, LocalDate>? get() =
        if (range == WeightRange.CUSTOM) firstDate?.let { it to today } else period

    val change: Double? get() = period?.let { (a, b) -> WeightMath.change(trend, a, b) }
    val weeklyRate: Double? get() = period?.let { (a, b) -> WeightMath.weeklyRate(trend, a, b) }
    val countInPeriod: Int get() = period?.let { (a, b) -> weighIns.count { w -> w.date() in a..b } } ?: 0

    /** Pace over the last 4 weeks, for the goal projection. */
    val recentRate: Double? get() = firstDate
        ?.takeIf { ChronoUnit.DAYS.between(it, today) >= 21 } // too little history to call it a pace
        ?.let { WeightMath.weeklyRate(trend, today.minusDays(28).coerceAtLeast(it), today) }
}

private fun LocalDate.coerceAtLeast(min: LocalDate) = if (isBefore(min)) min else this
private fun LocalDate.coerceAtMost(max: LocalDate) = if (isAfter(max)) max else this

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repository: WeightRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val selection = MutableStateFlow(Triple(WeightRange.MONTH, null as LocalDate?, null as LocalDate?))

    private val units = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.units }.onStart { emit(UnitSystem.METRIC) }

    val state: StateFlow<WeightUiState> = combine(
        repository.weighIns().catch { emit(emptyList()) },
        repository.goal().catch { emit(null) },
        units,
        selection,
    ) { weighIns, goal, units, (range, from, to) ->
        val today = LocalDate.now()
        WeightUiState(
            loading = false,
            today = today,
            units = units,
            weighIns = weighIns,
            trend = WeightMath.trend(weighIns, today),
            goal = goal,
            range = range,
            customFrom = from,
            customTo = to,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun setRange(range: WeightRange) {
        val s = state.value
        if (range == WeightRange.CUSTOM && selection.value.second == null) {
            // Start from when the goal was set, else the last 12 weeks.
            val first = s.firstDate ?: s.today
            val from = s.goal?.startDate ?: s.today.minusWeeks(12)
            selection.value = Triple(range, if (from.isBefore(first)) first else from, s.today)
        } else {
            selection.update { it.copy(first = range) }
        }
    }

    /** Moves the custom period's ends; they stay at least a day apart. */
    fun setCustom(from: LocalDate, to: LocalDate) {
        if (ChronoUnit.DAYS.between(from, to) < 1) return
        selection.value = Triple(WeightRange.CUSTOM, from, to)
    }

    fun save(id: String?, kg: Double, at: Instant, note: String?) = repository.save(id, kg, at, note)

    fun delete(weighIn: WeighIn) = repository.delete(weighIn)

    /**
     * Saves the goal. Changing only the target or pace of a goal in progress keeps its start (and
     * counts milestones already passed as celebrated, so an edit never sets off old celebrations); a
     * goal after reaching one, or after maintaining, starts fresh from today's trend.
     */
    fun setGoal(goalKg: Double, paceKgPerWeek: Double) {
        val s = state.value
        val now = s.current ?: return
        val start = s.goal?.takeIf { !it.maintaining && !it.reached(now) && (it.goalKg < it.startKg) == (goalKg < now) }
        val goal = WeightGoal(start?.startKg ?: now, start?.startDate ?: s.today, goalKg, paceKgPerWeek)
        val passed = WeightMath.milestonesReached(goal, now).coerceAtMost(WeightMath.milestoneSteps(goal).size - 1)
        repository.setGoal(goal.copy(milestonesCelebrated = if (start == null) 0 else passed))
    }

    /** Holds the goal weight (after reaching it), or today's trend when there's no goal. */
    fun maintain() {
        val s = state.value
        val kg = s.goal?.goalKg ?: s.current ?: return
        repository.setGoal(WeightGoal.maintain(kg, s.today))
    }

    fun clearGoal() = repository.clearGoal()
}
