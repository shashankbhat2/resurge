package com.resurgeai.feature.progress

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.progress.ReelRepository
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressPhoto
import com.resurgeai.core.model.ProgressReel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
internal class ReelReadyViewModel @Inject constructor(
    keys: PhotoKeys,
    val repository: ProgressRepository,
    private val reels: ReelRepository,
) : ViewModel() {
    /** The newest unwatched reel from the last two weeks, with its cover, when this phone can play it. */
    val ready: StateFlow<Pair<ProgressReel, ProgressPhoto>?> = combine(
        reels.reels().catch { emit(emptyList()) },
        repository.photos().catch { emit(emptyList()) },
        keys.state,
    ) { list, photos, key ->
        if (key != PhotoKeyState.READY) return@combine null
        val reel = list.firstOrNull()?.takeIf { !it.watched && it.createdAt.isAfter(Instant.now().minus(FRESH)) } ?: return@combine null
        val cover = reel.frameIds.lastOrNull()?.let { id -> photos.firstOrNull { it.id == id } } ?: return@combine null
        reel to cover
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismiss(reel: ProgressReel) = reels.markWatched(reel.id)

    private companion object {
        val FRESH: Duration = Duration.ofDays(14)
    }
}

/**
 * "Your 4-week reel is ready" on Today, only while a reel is new: watching or dismissing it puts
 * it away (it stays under Progress). Shows nothing otherwise, so Today carries no progress tile.
 */
@Composable
fun ReelReadyCard(onPlay: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ReelReadyViewModel = hiltViewModel()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val (reel, cover) = ready ?: return
    Row(
        modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UnstyledButton(
            onClick = { onPlay(reel.id) },
            modifier = Modifier.weight(1f).semantics { contentDescription = "${reelTitle(reel)} is ready. Play" },
            contentPadding = PaddingValues(12.dp),
            indication = LocalIndication.current,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 48.dp, height = 64.dp).clip(Resurge.shape.sm)) {
                    EncryptedImage(viewModel.repository, cover, PhotoVariant.THUMB, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                    Icon(ResurgeIcons.Play, contentDescription = null, tint = Color.White, size = 20.dp, modifier = Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (reel.kind) {
                            com.resurgeai.core.model.ReelKind.GOAL -> "You reached your goal"
                            com.resurgeai.core.model.ReelKind.MILESTONE -> "New milestone reel"
                            com.resurgeai.core.model.ReelKind.REGULAR -> "Your ${reelTitle(reel)} is ready"
                        },
                        style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                    )
                    Text("Tap to watch", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
            }
        }
        UnstyledButton(
            onClick = { viewModel.dismiss(reel) },
            modifier = Modifier.padding(end = 4.dp).size(48.dp).semantics { contentDescription = "Dismiss" },
            indication = LocalIndication.current,
        ) { Icon(ResurgeIcons.Close, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp) }
    }
}
