package com.resurgeai.feature.progress

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressMath
import com.resurgeai.core.model.ProgressPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

internal data class StripState(
    val loaded: Boolean = false,
    val keyState: PhotoKeyState = PhotoKeyState.LOADING,
    val latest: ProgressPhoto? = null,
    val count: Int = 0,
    val anchor: LocalDate? = null,
    val promptDismissed: Boolean = true,
)

@HiltViewModel
internal class ProgressStripViewModel @Inject constructor(
    private val keys: PhotoKeys,
    val repository: ProgressRepository,
    weights: WeightRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    val state: StateFlow<StripState> = combine(
        keys.state,
        repository.photos().catch { emit(emptyList()) },
        weights.goal().catch { emit(null) },
        preferences.progressPromptDismissed,
    ) { key, photos, goal, dismissed ->
        StripState(true, key, photos.lastOrNull(), photos.size, ProgressMath.anchor(goal?.startDate, photos), dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StripState())

    init {
        viewModelScope.launch { runCatching { keys.refresh() } }
    }

    fun dismissPrompt() {
        viewModelScope.launch { preferences.dismissProgressPrompt() }
    }
}

/**
 * Progress photos' way in from the Weight screen, one compact row: the latest photo, where you are
 * ("Week 6 · 9 photos") and a camera button. Before the first photo it's a one-line prompt that can
 * be dismissed for good; then it shows nothing until there are photos.
 */
@Composable
fun ProgressStrip(onOpen: () -> Unit, onCapture: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ProgressStripViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val latest = state.latest
    when {
        latest == null && state.promptDismissed -> Unit
        latest == null -> Row(
            modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledButton(onClick = onOpen, modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), indication = LocalIndication.current, contentAlignment = Alignment.CenterStart) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                        Icon(ResurgeIcons.Camera, contentDescription = null, tint = Resurge.colors.text, size = 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Snapshots", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight))
                        Text("See your change over time", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                    }
                }
            }
            UnstyledButton(
                onClick = viewModel::dismissPrompt,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Dismiss" },
                indication = LocalIndication.current,
            ) { Icon(ResurgeIcons.Close, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp) }
        }
        else -> Row(
            modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnstyledButton(
                onClick = onOpen,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Snapshots, ${state.count}. Open" },
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                indication = LocalIndication.current,
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 42.dp, height = 56.dp).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                        if (state.keyState == PhotoKeyState.READY) {
                            EncryptedImage(viewModel.repository, latest, PhotoVariant.THUMB, Modifier.size(width = 42.dp, height = 56.dp))
                        } else {
                            Icon(ResurgeIcons.Lock, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Snapshots", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight))
                        val where = state.anchor?.let { ProgressMath.label(it, LocalDate.now()) }
                        Text(
                            if (state.keyState == PhotoKeyState.LOCKED) "On your old phone"
                            else listOfNotNull(where, if (state.count == 1) "1 photo" else "${state.count} photos").joinToString(" · "),
                            style = Resurge.type.caption,
                            color = Resurge.colors.textMuted,
                        )
                    }
                    Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
                }
            }
            if (state.keyState == PhotoKeyState.READY) {
                UnstyledButton(
                    onClick = onCapture,
                    modifier = Modifier.padding(end = 12.dp).size(40.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent)
                        .semantics { contentDescription = "Take a snapshot" },
                    indication = LocalIndication.current,
                ) { Icon(ResurgeIcons.Camera, contentDescription = null, tint = Resurge.colors.onAccent, size = 20.dp) }
            }
        }
    }
}
