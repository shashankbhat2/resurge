package com.resurgeai.feature.progress

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.ReelRepository
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.ReelInterval
import com.resurgeai.core.model.ReelSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ProgressSettingsViewModel @Inject constructor(
    private val reels: ReelRepository,
    private val preferences: PreferencesRepository,
) : ViewModel() {
    val reelSettings: StateFlow<ReelSettings> = reels.settings().catch { emit(ReelSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReelSettings())

    fun setInterval(interval: ReelInterval) = reels.setInterval(interval)
    fun setReels(on: Boolean) = reels.setEnabled(on)

    val secure: StateFlow<Boolean> = preferences.progressScreenshotBlock.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setSecure(on: Boolean) {
        viewModelScope.launch { preferences.setProgressScreenshotBlock(on) }
    }
}

/** Reels and screenshot blocking for progress photos. */
@Composable
fun ProgressSettingsRoute(onBack: () -> Unit) {
    val viewModel: ProgressSettingsViewModel = hiltViewModel()
    val secure by viewModel.secure.collectAsStateWithLifecycle()
    val reels by viewModel.reelSettings.collectAsStateWithLifecycle()

    SecureWindow(true)

    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        TopBar("Snapshots", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label("Reels")
            Group {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic reels", style = Resurge.type.body, modifier = Modifier.weight(1f))
                    Switch(reels.enabled, viewModel::setReels, accessibilityLabel = "Automatic reels")
                }
                if (reels.enabled) {
                    Divider(Modifier.padding(start = 16.dp))
                    ReelInterval.entries.forEach { interval ->
                        val on = interval == reels.interval
                        UnstyledButton(
                            onClick = { viewModel.setInterval(interval) },
                            indication = LocalIndication.current,
                            modifier = Modifier.fillMaxWidth().semantics { selected = on },
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(interval.label, style = Resurge.type.body, modifier = Modifier.weight(1f))
                                if (on) Icon(ResurgeIcons.Check, contentDescription = "Selected", tint = Resurge.colors.accent, size = 20.dp)
                            }
                        }
                    }
                }
            }
            Text(
                "A reel needs at least 3 photos.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )

            Label("Privacy")
            Group {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Block screenshots", style = Resurge.type.body, modifier = Modifier.weight(1f))
                    Switch(secure, viewModel::setSecure, accessibilityLabel = "Block screenshots of snapshots")
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = Resurge.type.overline, color = Resurge.colors.textMuted, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp))
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface), content = content)
}
