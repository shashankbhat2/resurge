package com.resurgeai.feature.dashboard.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import com.resurgeai.core.model.SleepSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

enum class DetailRange(val label: String) {
    /** Last 7 days, one period per day. */
    WEEK("Week"),

    /** Current week plus the 4 before it, one period per calendar week. */
    MONTH("Month");

    fun firstDay(today: LocalDate): LocalDate = when (this) {
        WEEK -> today.minusDays(6)
        MONTH -> HealthMath.weekStart(today).minusWeeks(4)
    }
}

data class MetricDetailUiState(
    val metric: HealthMetric,
    val range: DetailRange = DetailRange.WEEK,
    val loading: Boolean = true,
    val series: MetricSeries? = null,
    val periods: List<PeriodValue> = emptyList(),
    /** Index into [periods]; defaults to the latest period. */
    val selected: Int = -1,
    val sleepSessions: List<SleepSession> = emptyList(),
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    /** Daily min/max bpm, only loaded for heart rate. */
    val heartRanges: Map<LocalDate, Pair<Long, Long>> = emptyMap(),
    val error: String? = null,
)

@HiltViewModel
class MetricDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val healthData: HealthDataSource,
) : ViewModel() {
    private val metric: HealthMetric = HealthMetric.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_METRIC)))

    private val _state = MutableStateFlow(MetricDetailUiState(metric))
    val state: StateFlow<MetricDetailUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(DetailRange.WEEK)
    }

    fun setRange(range: DetailRange) {
        if (range != _state.value.range) load(range)
    }

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    private fun load(range: DetailRange) {
        job?.cancel()
        _state.update { it.copy(range = range, loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val first = range.firstDay(today)
                val days = ChronoUnit.DAYS.between(first, today).toInt() + 1
                val from = first.atStartOfDay(zone).toInstant()
                val to = Instant.now()
                coroutineScope {
                    val series = async { healthData.readSeries(metric, days, zone) }
                    val sleep = async { if (metric == HealthMetric.SLEEP) healthData.sleepSessions(from, to) else emptyList() }
                    val exercise = async { if (metric == HealthMetric.EXERCISE) healthData.exerciseSessionsBetween(from, to) else emptyList() }
                    val heart = async { if (metric == HealthMetric.HEART_RATE) healthData.readHeartRateRanges(days, zone) else emptyMap() }
                    val s = series.await()
                    val periods = when (range) {
                        DetailRange.WEEK -> Periods.daily(s)
                        DetailRange.MONTH -> Periods.weekly(s)
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            series = s,
                            periods = periods,
                            selected = periods.lastIndex,
                            sleepSessions = sleep.await().sortedByDescending { x -> x.end },
                            exerciseSessions = exercise.await().sortedByDescending { x -> x.start },
                            heartRanges = heart.await(),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("MetricDetail", "Load failed", e)
                _state.update { it.copy(loading = false, error = "Couldn't load ${metric.label.lowercase()} data.") }
            }
        }
    }

    companion object {
        /** Must match the property name in the navigation route. */
        const val ARG_METRIC = "metric"
    }
}
