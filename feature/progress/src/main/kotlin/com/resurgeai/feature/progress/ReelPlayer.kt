package com.resurgeai.feature.progress

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.progress.ReelRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.SecureWindow
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.ProgressMath
import com.resurgeai.core.model.ProgressPhoto
import com.resurgeai.core.model.ProgressReel
import com.resurgeai.core.model.ReelKind
import com.resurgeai.core.model.ReelPlanner
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

/** Plays a reel made on the spot from all photos, not saved. */
const val LIVE_REEL = "live"

internal data class ReelPlayback(
    val reel: ProgressReel,
    val frames: List<ProgressPhoto>,
    val anchor: LocalDate,
    val weights: Map<String, Double>,
    val goal: WeightGoal?,
    val units: UnitSystem,
    val secure: Boolean,
) {
    /** Frames from [ProgressReel.newFrames] on are new since the last reel. */
    fun isNew(index: Int) = reel.newFrames in 1 until frames.size && index >= frames.size - reel.newFrames
}

@HiltViewModel
internal class ReelPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val reels: ReelRepository,
    val repository: ProgressRepository,
    private val weights: WeightRepository,
    private val preferences: PreferencesRepository,
    private val session: SessionRepository,
) : ViewModel() {
    private val id: String = savedState.get<String>("id").orEmpty()

    private val _playback = MutableStateFlow<ReelPlayback?>(null)
    val playback: StateFlow<ReelPlayback?> = _playback.asStateFlow()

    private val _missing = MutableStateFlow(false)
    val missing: StateFlow<Boolean> = _missing.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure { _missing.value = true }
        }
    }

    private suspend fun load() {
        val photos = repository.photos().first()
        val goal = weights.goal().catch { emit(null) }.first()
        val weighIns = weights.weighIns().catch { emit(emptyList()) }.first()
        val units = session.session.filterIsInstance<SessionState.Ready>().firstOrNull()?.profile?.units ?: UnitSystem.METRIC
        val reel = if (id == LIVE_REEL) {
            val frames = ReelPlanner.frames(photos, null)
            if (frames.isEmpty()) null else ProgressReel(id, ReelKind.REGULAR, java.time.Instant.now(), frames.first().date(), frames.last().date(), frames.map { it.id }, 0)
        } else {
            reels.reel(id)
        }
        val byId = photos.associateBy { it.id }
        val frames = reel?.frameIds?.mapNotNull(byId::get).orEmpty()
        if (reel == null || frames.isEmpty()) {
            _missing.value = true
            return
        }
        // Decrypt every frame up front so the reel never waits mid-play.
        coroutineScope { frames.map { f -> async { ProgressImages.load(repository, f, PhotoVariant.FULL) } }.awaitAll() }
        val today = LocalDate.now()
        val trend = WeightMath.trend(weighIns, today)
        _playback.value = ReelPlayback(
            reel = reel,
            frames = frames,
            anchor = ProgressMath.anchor(goal?.startDate, photos) ?: frames.first().date(),
            weights = if (trend.isEmpty()) emptyMap() else frames.mapNotNull { f ->
                if (f.date().isBefore(trend.first().date)) null else WeightMath.trendOn(trend, f.date())?.let { f.id to it }
            }.toMap(),
            goal = goal,
            units = units,
            secure = preferences.progressScreenshotBlock.first(),
        )
        if (!reel.watched && id != LIVE_REEL) reels.markWatched(reel.id)
    }
}

private const val INTRO_MS = 2_200L
private const val FRAME_MS = 1_500L
private const val FIRST_LAST_MS = 2_200L

/**
 * A progress reel, full screen: an intro card, every frame crossfading (with a slow push-in) under
 * story-style progress bars, then the before/after slider and what changed. Tap right to skip, left
 * to go back, hold to pause.
 */
@Composable
fun ReelPlayerRoute(onClose: () -> Unit) {
    val viewModel: ReelPlayerViewModel = hiltViewModel()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    SecureWindow(playback?.secure ?: true)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val p = playback
        when {
            p != null -> Player(p, viewModel.repository, onClose)
            missing -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("This reel isn't available", style = Resurge.type.headline, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button("Close", onClick = onClose, variant = ButtonVariant.Secondary)
            }
            else -> Spinner(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

@Composable
private fun Player(p: ReelPlayback, repository: ProgressRepository, onClose: () -> Unit) {
    // Steps: 0 = intro, 1..n = frames, n + 1 = the end card (stays until the user acts).
    val last = p.frames.size + 1
    var step by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    val duration = when (step) {
        0 -> INTRO_MS
        1, p.frames.size -> FIRST_LAST_MS
        else -> FRAME_MS
    }
    LaunchedEffect(step, paused) {
        if (step >= last || paused) return@LaunchedEffect
        var previous = withFrameMillis { it }
        while (progress < 1f) {
            val now = withFrameMillis { it }
            progress = (progress + (now - previous).toFloat() / duration).coerceAtMost(1f)
            previous = now
        }
        progress = 0f
        step++
    }
    val go: (Int) -> Unit = { to ->
        progress = 0f
        step = to.coerceIn(0, last)
    }

    Box(
        Modifier.fillMaxSize().pointerInput(step) {
            if (step >= last) return@pointerInput
            detectTapGestures(
                onPress = {
                    paused = true
                    tryAwaitRelease()
                    paused = false
                },
                onTap = { o -> if (o.x < size.width / 3f) go(step - 1) else go(step + 1) },
            )
        },
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
            label = "reel",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            when {
                s == 0 -> Intro(p)
                s >= last -> End(p, repository, onReplay = { go(0) }, onClose = onClose)
                else -> Frame(p, repository, s - 1, active = s == step, paused = paused)
            }
        }
        if (step < last) {
            Bars(count = p.frames.size + 1, step = step, progress = progress, modifier = Modifier.align(Alignment.TopCenter))
            GlassClose(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun Bars(count: Int, step: Int, progress: Float, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) { i ->
            val fill = when {
                i < step -> 1f
                i == step -> progress
                else -> 0f
            }
            Box(Modifier.weight(1f).height(3.dp).clip(Resurge.shape.pill).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
            }
        }
    }
}

@Composable
private fun GlassClose(onClose: () -> Unit, modifier: Modifier) {
    Box(modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 20.dp, end = 12.dp)) {
        GlassButton(ResurgeIcons.Close, "Close reel", onClick = onClose)
    }
}

@Composable
private fun Intro(p: ReelPlayback) {
    val days = ChronoUnit.DAYS.between(p.reel.from, p.reel.to)
    val (kicker, title) = when (p.reel.kind) {
        ReelKind.GOAL -> "Goal reached" to "Your whole journey"
        ReelKind.MILESTONE -> "New milestone" to (p.reel.milestoneKg?.let { "${formatWeight(it, p.units).replace(".0 ", " ")} ${if (p.goal?.losing == false) "up" else "down"}" } ?: "A new milestone")
        ReelKind.REGULAR -> "Your progress" to when {
            days >= 14 -> "${days / 7} weeks"
            days == 0L -> "Today"
            days == 1L -> "1 day"
            else -> "$days days"
        }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A2470), Color(0xFF111127)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(32.dp)) {
            Text(kicker.uppercase(), style = Resurge.type.overline, color = Color.White.copy(alpha = 0.7f))
            Text(title, style = Resurge.type.tide.copy(fontSize = 48.sp, lineHeight = 54.sp), color = Color.White, textAlign = TextAlign.Center)
            Text(
                "${ProgressMath.label(p.anchor, p.reel.from)} → ${ProgressMath.label(p.anchor, p.reel.to)} · ${p.frames.size} photos",
                style = Resurge.type.body,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Frame(p: ReelPlayback, repository: ProgressRepository, index: Int, active: Boolean, paused: Boolean) {
    val photo = p.frames[index]
    // A slow push-in while the frame is on screen.
    var zoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(active, paused) {
        if (!active || paused) return@LaunchedEffect
        val start = withFrameMillis { it }
        val from = zoom
        while (zoom < 1.05f) {
            val t = (withFrameMillis { it } - start) / 2_400f
            zoom = (from + 0.05f * t).coerceAtMost(1.05f)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EncryptedImage(
            repository,
            photo,
            PhotoVariant.FULL,
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).graphicsLayer { scaleX = zoom; scaleY = zoom },
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (p.isNew(index)) {
                Text(
                    "NEW",
                    style = Resurge.type.overline,
                    color = Color(0xFF111127),
                    modifier = Modifier.clip(Resurge.shape.pill).background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(ProgressMath.label(p.anchor, photo.date()), style = Resurge.type.tide.copy(fontSize = 40.sp, lineHeight = 44.sp), color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortDate(photo.date()), style = Resurge.type.body, color = Color.White.copy(alpha = 0.8f))
                p.weights[photo.id]?.let { Text("· ${formatWeight(it, p.units)}", style = Resurge.type.body, color = Color.White.copy(alpha = 0.8f)) }
            }
        }
    }
}

/** The end card: the slider from first to last frame and what changed. */
@Composable
private fun End(p: ReelPlayback, repository: ProgressRepository, onReplay: () -> Unit, onClose: () -> Unit) {
    val first = p.frames.first()
    val last = p.frames.last()
    val total = p.weights[last.id]?.let { b -> p.weights[first.id]?.let { a -> b - a } }
    val sinceLast = p.frames.getOrNull(p.frames.size - p.reel.newFrames - 1)?.takeIf { p.reel.newFrames in 1 until p.frames.size }
        ?.let { before -> p.weights[last.id]?.let { b -> p.weights[before.id]?.let { a -> b - a } } }
    Column(
        Modifier.fillMaxSize().background(Resurge.colors.bg).verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(endTitle(p, total), style = Resurge.type.title)
        CompareSlider(repository, first, last, ProgressMath.label(p.anchor, first.date()), ProgressMath.label(p.anchor, last.date()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sinceLast != null) EndStat("Since last reel", formatChange(sinceLast, p.units), Modifier.weight(1f))
            if (total != null) EndStat("Since ${shortDate(first.date())}", formatChange(total, p.units), Modifier.weight(1f))
            EndStat("Photos", "${p.frames.size}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Replay", onClick = onReplay, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Repeat, modifier = Modifier.weight(1f))
            Button("Done", onClick = onClose, variant = ButtonVariant.Accent, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EndStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(Resurge.shape.md).background(Resurge.colors.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
        Text(value, style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"))
    }
}

/** Toward the goal's direction (down when there's no goal) counts as progress. */
private fun towardGoal(p: ReelPlayback, change: Double): Boolean = if (p.goal?.losing == false) change > 0 else change < 0

private fun endTitle(p: ReelPlayback, total: Double?): String = when {
    p.reel.kind == ReelKind.GOAL -> "Goal reached"
    total == null || abs(total) < 0.2 -> "Holding steady"
    towardGoal(p, total) -> "Nice progress"
    else -> "Keep going"
}
