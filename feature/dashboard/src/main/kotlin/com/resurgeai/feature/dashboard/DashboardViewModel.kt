package com.resurgeai.feature.dashboard

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.health.HealthSyncRepository
import com.resurgeai.core.data.health.HealthSyncWorker
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class PermissionState { UNKNOWN, NONE, PARTIAL, ALL }

data class DashboardUiState(
    val profile: UserProfile? = null,
    val availability: HealthConnectAvailability? = null,
    val permissions: PermissionState = PermissionState.UNKNOWN,
    val loading: Boolean = false,
    val today: DailyHealth? = null,
    val sparklines: Map<HealthMetric, List<Double?>> = emptyMap(),
    /** Mon..Sun, true when that day counts as an exercise day. */
    val exerciseWeek: List<Boolean> = List(7) { false },
    val todayIndex: Int = LocalDate.now().dayOfWeek.value - 1,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val healthConnect: HealthConnectManager,
    private val healthData: HealthDataSource,
    private val healthSync: HealthSyncRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    val permissionContract = healthConnect.permissionContract()
    val permissionsToRequest: Set<String> get() = healthConnect.permissionsToRequest()

    fun installIntent() = healthConnect.installIntent()

    private var loadJob: Job? = null
    private var lastLoadedAt = 0L

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                _state.update { it.copy(profile = (session as? SessionState.Ready)?.profile) }
            }
        }
    }

    /** Called on resume. Skips if data was loaded very recently unless [force]. */
    fun refresh(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && loadJob?.isActive == true) return
        if (!force && now - lastLoadedAt < MIN_REFRESH_INTERVAL_MS && _state.value.today != null) return
        loadJob = viewModelScope.launch { load() }
    }

    fun onPermissionsResult(@Suppress("UNUSED_PARAMETER") granted: Set<String>) = refresh(force = true)

    private suspend fun load() {
        val availability = healthConnect.availability()
        _state.update { it.copy(availability = availability, error = null) }
        if (availability != HealthConnectAvailability.AVAILABLE) return

        val granted = healthConnect.grantedPermissions()
        val permissionState = when {
            granted.containsAll(HealthPermissions.READ) -> PermissionState.ALL
            granted.any { it in HealthPermissions.READ } -> PermissionState.PARTIAL
            else -> PermissionState.NONE
        }
        _state.update { it.copy(permissions = permissionState) }
        if (permissionState == PermissionState.NONE) return

        _state.update { it.copy(loading = true) }
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val monday = HealthMath.weekStart(today)
            coroutineScope {
                val day = async { healthData.readDay(today, zone) }
                val week = async { HealthMath.exerciseDays(healthData.exerciseThisWeek(zone), zone) }
                val series = SPARKLINE_METRICS.map { m -> async { m to healthData.readSeries(m, 7, zone) } }
                val activeDays = week.await()
                _state.update {
                    it.copy(
                        loading = false,
                        today = day.await(),
                        exerciseWeek = (0 until 7).map { i -> monday.plusDays(i.toLong()) in activeDays },
                        todayIndex = today.dayOfWeek.value - 1,
                        sparklines = series.awaitAll().associate { (m, s) -> m to s.points.map { p -> p.value } },
                    )
                }
            }
            lastLoadedAt = SystemClock.elapsedRealtime()

            // Upload daily aggregates for the coach, and (re)schedule background sync.
            viewModelScope.launch {
                healthSync.syncRecent(zone)
                HealthSyncWorker.schedule(appContext, healthConnect)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Health Connect", e)
            _state.update { it.copy(loading = false, error = "Couldn't read Health Connect.") }
        }
    }

    private companion object {
        const val TAG = "Dashboard"
        const val MIN_REFRESH_INTERVAL_MS = 30_000L
        val SPARKLINE_METRICS = listOf(
            HealthMetric.STEPS, HealthMetric.DISTANCE, HealthMetric.CALORIES_BURNED,
            HealthMetric.SLEEP, HealthMetric.WEIGHT,
        )
    }
}
