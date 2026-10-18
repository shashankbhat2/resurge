package com.resurgeai.feature.progress

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.progress.ReelRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressMath
import com.resurgeai.core.model.ProgressPhoto
import com.resurgeai.core.model.ProgressReel
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** Transient things on the Progress screen: busy flag and errors. */
internal data class ProgressUi(
    val busy: Boolean = false,
    val error: String? = null,
)

internal data class ProgressUiState(
    val loading: Boolean = true,
    val keyState: PhotoKeyState = PhotoKeyState.LOADING,
    /** Oldest first. */
    val photos: List<ProgressPhoto> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val anchor: LocalDate? = null,
    val units: UnitSystem = UnitSystem.METRIC,
    /** Trend weight on the day each photo was taken, by photo id (only when there are weigh-ins). */
    val weights: Map<String, Double> = emptyMap(),
    val secure: Boolean = true,
    /** Newest first. */
    val reels: List<ProgressReel> = emptyList(),
    val ui: ProgressUi = ProgressUi(),
) {
    val pendingUploads: Int get() = photos.count { !it.uploaded }

    fun label(photo: ProgressPhoto): String = anchor?.let { ProgressMath.label(it, photo.date()) } ?: ""
}

@HiltViewModel
internal class ProgressViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keys: PhotoKeys,
    val repository: ProgressRepository,
    weights: WeightRepository,
    private val reels: ReelRepository,
    private val preferences: PreferencesRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val ui = MutableStateFlow(ProgressUi())

    private val units = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.units }.onStart { emit(UnitSystem.METRIC) }

    private val weightData = combine(weights.weighIns().catch { emit(emptyList()) }, weights.goal().catch { emit(null) }) { w, g -> w to g }

    val state: StateFlow<ProgressUiState> = combine(
        keys.state,
        repository.photos().catch { emit(emptyList()) },
        weightData,
        combine(units, preferences.progressScreenshotBlock, this.reels.reels().catch { emit(emptyList()) }) { u, s, r -> Triple(u, s, r) },
        ui,
    ) { keyState, photos, (weighIns, goal), (units, secure, reels), ui ->
        val today = LocalDate.now()
        val trend = WeightMath.trend(weighIns, today)
        ProgressUiState(
            loading = keyState == PhotoKeyState.LOADING,
            keyState = keyState,
            photos = photos,
            today = today,
            anchor = ProgressMath.anchor(goal?.startDate, photos),
            units = units,
            weights = if (trend.isEmpty()) emptyMap() else photos.mapNotNull { p ->
                // Only days the trend covers: before the first weigh-in there's nothing to say.
                if (p.date().isBefore(trend.first().date)) null else WeightMath.trendOn(trend, p.date())?.let { p.id to it }
            }.toMap(),
            secure = secure,
            reels = reels.filter { r -> r.frameIds.any { id -> photos.any { it.id == id } } },
            ui = ui,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    init {
        refreshKey()
    }

    fun refreshKey() {
        viewModelScope.launch { runCatching { keys.refresh() }.onFailure { Log.w(TAG, "Key check failed", it) } }
    }

    /**
     * On a phone that can't open the earlier photos: removes them (and their reels) and makes a new
     * key, so new photos work and back up.
     */
    fun startFresh() {
        viewModelScope.launch {
            ui.update { it.copy(busy = true, error = null) }
            runCatching {
                repository.deleteAll()
                reels.deleteAll()
                keys.startFresh()
                ProgressImages.clear()
            }.onFailure { e ->
                Log.w(TAG, "Start fresh failed", e)
                ui.update { it.copy(error = "Something went wrong. Try again.") }
            }
            ui.update { it.copy(busy = false) }
        }
    }

    fun clearError() = ui.update { it.copy(error = null) }

    fun delete(photo: ProgressPhoto) {
        viewModelScope.launch {
            ProgressImages.forget(photo)
            runCatching { repository.delete(photo) }.onFailure { Log.w(TAG, "Delete failed", it) }
        }
    }

    /** Imports a gallery photo, dated from its EXIF when it has one. */
    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            ui.update { it.copy(busy = true, error = null) }
            val ok = runCatching {
                val prepared = withContext(Dispatchers.Default) { ProgressImages.prepare(context, uri) } ?: error("Unreadable photo")
                repository.add(prepared.full, prepared.thumb, prepared.takenAt, prepared.width, prepared.height)
            }.onFailure { Log.w(TAG, "Import failed", it) }.isSuccess
            ui.update { it.copy(busy = false, error = if (ok) null else "Couldn't add that photo.") }
        }
    }

    fun setSecure(on: Boolean) {
        viewModelScope.launch { preferences.setProgressScreenshotBlock(on) }
    }

    private companion object {
        const val TAG = "ProgressViewModel"
    }
}
