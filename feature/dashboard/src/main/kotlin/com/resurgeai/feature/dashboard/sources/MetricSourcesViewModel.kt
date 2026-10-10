package com.resurgeai.feature.dashboard.sources

import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SourceBreakdown
import com.resurgeai.feature.dashboard.detail.MetricDetailViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class SourceRange(val label: String) { TODAY("Today"), WEEK("7 days"), MONTH("30 days") }

data class MetricSourcesUiState(
    val metric: HealthMetric,
    val range: SourceRange = SourceRange.TODAY,
    val loading: Boolean = true,
    val breakdown: SourceBreakdown = SourceBreakdown.Empty,
    val error: String? = null,
)

@HiltViewModel
class MetricSourcesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val healthData: HealthDataSource,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val metric = HealthMetric.valueOf(checkNotNull(savedStateHandle.get<String>(MetricDetailViewModel.ARG_METRIC)))

    private val _state = MutableStateFlow(MetricSourcesUiState(metric))
    val state: StateFlow<MetricSourcesUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { load(SourceRange.TODAY) }

    fun setRange(range: SourceRange) {
        if (range != _state.value.range) load(range)
    }

    fun healthConnectSettingsIntent(): Intent = healthConnect.settingsIntent()

    private fun load(range: SourceRange) {
        job?.cancel()
        _state.update { it.copy(range = range, loading = true, error = null) }
        job = viewModelScope.launch {
            runCatching {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val start = when (range) {
                    SourceRange.TODAY -> HealthMath.dayBounds(today, zone).first
                    SourceRange.WEEK -> today.minusDays(6).atStartOfDay(zone).toInstant()
                    SourceRange.MONTH -> today.minusDays(29).atStartOfDay(zone).toInstant()
                }
                healthData.readSources(metric, start, Instant.now())
            }.onSuccess { breakdown ->
                _state.update { it.copy(loading = false, breakdown = breakdown) }
            }.onFailure { e ->
                Log.w("MetricSources", "Load failed", e)
                _state.update { it.copy(loading = false, error = "Couldn't load sources.") }
            }
        }
    }
}
