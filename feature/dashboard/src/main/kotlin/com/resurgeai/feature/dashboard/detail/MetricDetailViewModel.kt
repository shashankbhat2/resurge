package com.resurgeai.feature.dashboard.detail

import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SourceBreakdown
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
import javax.inject.Inject

enum class DetailRange(val days: Int, val label: String) { WEEK(7, "Week"), MONTH(30, "Month") }

data class MetricDetailUiState(
    val metric: HealthMetric,
    val range: DetailRange = DetailRange.WEEK,
    val loading: Boolean = true,
    val series: MetricSeries? = null,
    val sourcesToday: SourceBreakdown = SourceBreakdown.Empty,
    val sleepSessions: List<SleepSession> = emptyList(),
    val exerciseSessions: List<ExerciseSession> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class MetricDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val healthData: HealthDataSource,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val metric: HealthMetric = HealthMetric.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_METRIC)))

    private val _state = MutableStateFlow(MetricDetailUiState(metric))
    val state: StateFlow<MetricDetailUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { load(DetailRange.WEEK) }

    fun setRange(range: DetailRange) {
        if (range != _state.value.range) load(range)
    }

    fun healthConnectSettingsIntent(): Intent = healthConnect.settingsIntent()

    private fun load(range: DetailRange) {
        job?.cancel()
        _state.update { it.copy(range = range, loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val from = today.minusDays(range.days - 1L).atStartOfDay(zone).toInstant()
                val to = Instant.now()
                coroutineScope {
                    val series = async { healthData.readSeries(metric, range.days, zone) }
                    val day = async { healthData.readDay(today, zone) }
                    val sleep = async { if (metric == HealthMetric.SLEEP) healthData.sleepSessions(from, to) else emptyList() }
                    val exercise = async { if (metric == HealthMetric.EXERCISE) healthData.exerciseSessionsBetween(from, to) else emptyList() }
                    _state.update {
                        it.copy(
                            loading = false,
                            series = series.await(),
                            sourcesToday = day.await().sources[metric] ?: SourceBreakdown.Empty,
                            sleepSessions = sleep.await().sortedByDescending { s -> s.end },
                            exerciseSessions = exercise.await().sortedByDescending { s -> s.start },
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
