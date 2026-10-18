package com.resurgeai.feature.progress

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressPhoto
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit

/**
 * Progress photos: a before/after slider from the first photo to the latest, every photo in a grid,
 * and a viewer. Encryption is never mentioned beyond "only you can see these": the key is made and
 * backed up without the user seeing it.
 */
@Composable
fun ProgressRoute(onBack: () -> Unit, onCapture: () -> Unit, onOpenSettings: () -> Unit, onOpenReel: (String) -> Unit) {
    val viewModel: ProgressViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var viewing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<ProgressPhoto?>(null) }
    var confirmFresh by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::importPhoto) }
    val pickFromGallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    SecureWindow(state.secure)

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Snapshots",
            onBack = onBack,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.keyState == PhotoKeyState.READY) RoundButton(ResurgeIcons.Settings, "Snapshots settings", accent = false, onClick = onOpenSettings)
                    if (state.keyState == PhotoKeyState.READY && state.photos.isNotEmpty()) RoundButton(ResurgeIcons.Camera, "Take a snapshot", accent = true, onClick = onCapture)
                }
            },
        )
        when (state.keyState) {
            PhotoKeyState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            PhotoKeyState.OFFLINE -> Offline(onRetry = viewModel::refreshKey)
            PhotoKeyState.LOCKED -> Locked(busy = state.ui.busy, onRetry = viewModel::refreshKey, onStartFresh = { confirmFresh = true })
            // The key is made quietly when the camera opens, so there's nothing to set up here.
            PhotoKeyState.NOT_SET_UP -> FirstPhoto(busy = state.ui.busy, error = state.ui.error, onCapture = onCapture, onGallery = null)
            PhotoKeyState.READY -> if (state.photos.isEmpty()) {
                FirstPhoto(busy = state.ui.busy, error = state.ui.error, onCapture = onCapture, onGallery = pickFromGallery)
            } else {
                Photos(
                    state = state,
                    repository = viewModel.repository,
                    onOpen = { viewing = it.id },
                    onGallery = pickFromGallery,
                    onOpenReel = onOpenReel,
                )
            }
        }
    }

    viewing?.let { id ->
        val photos = state.photos.asReversed()
        val start = photos.indexOfFirst { it.id == id }
        if (start < 0) {
            LaunchedEffect(id) { viewing = null }
        } else {
            PhotoViewer(
                state = state,
                photos = photos,
                start = start,
                repository = viewModel.repository,
                onClose = { viewing = null },
                onDelete = { deleting = it },
            )
        }
    }

    deleting?.let { photo ->
        ProgressSheet(onDismiss = { deleting = null }) {
            Text("Delete this photo?", style = Resurge.type.title)
            Text("You can't undo this.", style = Resurge.type.body, color = Resurge.colors.textMuted)
            Button("Delete", onClick = {
                viewModel.delete(photo)
                deleting = null
            }, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
            Button("Cancel", onClick = { deleting = null }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmFresh) {
        ProgressSheet(onDismiss = { confirmFresh = false }) {
            Text("Start fresh?", style = Resurge.type.title)
            Text("Your old photos will be deleted.", style = Resurge.type.body, color = Resurge.colors.textMuted)
            Button("Start fresh", onClick = {
                confirmFresh = false
                viewModel.startFresh()
            }, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
            Button("Cancel", onClick = { confirmFresh = false }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- States before photos ---

@Composable
private fun FirstPhoto(busy: Boolean, error: String?, onCapture: () -> Unit, onGallery: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Explainer(
            ResurgeIcons.Camera,
            "Snapshots",
            "Snap a photo every week or two. We'll turn them into a short reel every few weeks.",
        )
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surface).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ResurgeIcons.Lock, contentDescription = null, tint = Resurge.colors.brandInk, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Your photos are encrypted. Only you can see them.", style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(28.dp))
        error?.let { ErrorNote(it) }
        Button("Take a photo", onClick = onCapture, variant = ButtonVariant.Accent, leadingIcon = ResurgeIcons.Camera, modifier = Modifier.fillMaxWidth())
        onGallery?.let {
            Spacer(Modifier.height(8.dp))
            Button("Add from gallery", onClick = it, loading = busy, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Photo, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A new phone that couldn't bring the photos' key across. Retries on its own; the only choice is starting over. */
@Composable
private fun Locked(busy: Boolean, onRetry: () -> Unit, onStartFresh: () -> Unit) {
    AutoRetry(onRetry)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Explainer(
            ResurgeIcons.Lock,
            "Your photos are on your old phone",
            "Restore this phone from your old phone's Google backup to bring them over.",
        )
        Spacer(Modifier.height(20.dp))
        Button("Start fresh", onClick = onStartFresh, loading = busy, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Offline(onRetry: () -> Unit) {
    AutoRetry(onRetry)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Explainer(ResurgeIcons.Lock, "You're offline", "Your photos will show up once you're connected.")
    }
}

/** Checks again every few seconds while this state is on screen, so there's nothing to tap. */
@Composable
private fun AutoRetry(onRetry: () -> Unit) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            onRetry()
        }
    }
}

@Composable
private fun ErrorNote(text: String) {
    Text(
        text,
        style = Resurge.type.label,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(Resurge.shape.sm).background(Resurge.colors.glowTint).padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

// --- Photos ---

@Composable
private fun Photos(
    state: ProgressUiState,
    repository: ProgressRepository,
    onOpen: (ProgressPhoto) -> Unit,
    onGallery: () -> Unit,
    onOpenReel: (String) -> Unit,
) {
    val photos = state.photos
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (photos.size >= 2) Compare(state, repository, photos.first(), photos.last()) else Single(state, repository, photos.first())
        if (photos.size >= 2) {
            Button("Play reel", onClick = { onOpenReel(LIVE_REEL) }, variant = ButtonVariant.Primary, leadingIcon = ResurgeIcons.Play, modifier = Modifier.fillMaxWidth())
        }
        if (state.reels.isNotEmpty()) {
            SectionLabel("Reels", Modifier.padding(top = 6.dp), trailing = "${state.reels.size}")
            Reels(state, repository, onOpenReel)
        }
        SectionLabel("Photos", Modifier.padding(top = 6.dp), trailing = "${photos.size}")
        Grid(state, repository, photos.asReversed(), onOpen)
        Button("Add from gallery", onClick = onGallery, loading = state.ui.busy, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Photo, modifier = Modifier.fillMaxWidth())
        state.ui.error?.let { ErrorNote(it) }
    }
}

/** The first photo against the latest, with how far apart they are and what the trend did. */
@Composable
private fun Compare(state: ProgressUiState, repository: ProgressRepository, before: ProgressPhoto, after: ProgressPhoto) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        CompareSlider(repository, before, after, state.label(before), state.label(after))
        CompareSummary(state, before, after)
    }
}

/**
 * Before and after in one frame: drag the handle (or tap) to wipe between them, [before] on the
 * left and [after] on the right.
 */
@Composable
internal fun CompareSlider(repository: ProgressRepository, before: ProgressPhoto, after: ProgressPhoto, beforeLabel: String, afterLabel: String) {
    var split by remember { mutableFloatStateOf(0.5f) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(Resurge.shape.lg)
            .pointerInput(Unit) {
                detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
            .semantics { contentDescription = "Before and after. Drag to compare." },
    ) {
        val w = maxWidth
        EncryptedImage(repository, before, PhotoVariant.FULL, Modifier.fillMaxSize())
        EncryptedImage(
            repository,
            after,
            PhotoVariant.FULL,
            Modifier.fillMaxSize().drawWithContent { clipRect(left = size.width * split) { this@drawWithContent.drawContent() } },
        )
        Box(Modifier.offset(x = w * split - 1.dp).width(2.dp).fillMaxHeight().background(Color.White))
        Box(
            Modifier.align(Alignment.CenterStart).offset(x = w * split - 18.dp).size(36.dp).clip(Resurge.shape.pill).background(Color.White),
            contentAlignment = Alignment.Center,
        ) { Icon(ResurgeIcons.CompareArrows, contentDescription = null, tint = Color(0xFF111127), size = 20.dp) }
        PhotoTag(beforeLabel, Modifier.align(Alignment.TopStart).padding(10.dp))
        PhotoTag(afterLabel, Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun CompareSummary(state: ProgressUiState, before: ProgressPhoto, after: ProgressPhoto) {
    val days = ChronoUnit.DAYS.between(before.date(), after.date())
    val a = state.weights[before.id]
    val b = state.weights[after.id]
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${shortDate(before.date(), state.today)} → ${shortDate(after.date(), state.today)}", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight))
            Text(
                when {
                    days >= 14 -> "${days / 7} weeks apart"
                    days == 0L -> "Same day"
                    days == 1L -> "1 day apart"
                    else -> "$days days apart"
                },
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
            )
        }
        if (a != null && b != null) {
            Text(
                formatChange(b - a, state.units),
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
                color = Resurge.colors.brandInk,
                modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.accentMuted).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Single(state: ProgressUiState, repository: ProgressRepository, photo: ProgressPhoto) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            EncryptedImage(repository, photo, PhotoVariant.FULL, Modifier.fillMaxSize())
            PhotoTag(state.label(photo), Modifier.align(Alignment.TopStart).padding(10.dp))
        }
        Text(
            "Take another in a week or two to compare.",
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PhotoTag(text: String, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text,
        style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight),
        color = Color.White,
        modifier = modifier.clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun Grid(state: ProgressUiState, repository: ProgressRepository, photos: List<ProgressPhoto>, onOpen: (ProgressPhoto) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { photo ->
                    UnstyledButton(
                        onClick = { onOpen(photo) },
                        modifier = Modifier.weight(1f).aspectRatio(3f / 4f).clip(Resurge.shape.sm)
                            .semantics { contentDescription = "${state.label(photo)}, ${shortDate(photo.date(), state.today)}. Open" },
                        indication = LocalIndication.current,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            EncryptedImage(repository, photo, PhotoVariant.THUMB, Modifier.fillMaxSize())
                            Box(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                            )
                            Text(
                                state.label(photo),
                                style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight),
                                color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Made reels, newest first, as a row of covers; a dot marks the unwatched ones. */
@Composable
private fun Reels(state: ProgressUiState, repository: ProgressRepository, onOpenReel: (String) -> Unit) {
    val byId = state.photos.associateBy { it.id }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.reels.forEach { reel ->
            val cover = reel.frameIds.lastOrNull()?.let(byId::get) ?: return@forEach
            UnstyledButton(
                onClick = { onOpenReel(reel.id) },
                modifier = Modifier.width(112.dp).aspectRatio(3f / 4f).clip(Resurge.shape.md)
                    .semantics { contentDescription = "${reelTitle(reel)}${if (reel.watched) "" else ", new"}. Play" },
                indication = LocalIndication.current,
            ) {
                Box(Modifier.fillMaxSize()) {
                    EncryptedImage(repository, cover, PhotoVariant.THUMB, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.65f)))))
                    if (!reel.watched) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(10.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent))
                    Icon(ResurgeIcons.Play, contentDescription = null, tint = Color.White, size = 22.dp, modifier = Modifier.align(Alignment.Center))
                    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text(reelTitle(reel), style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight), color = Color.White, maxLines = 1)
                        Text(shortDate(reel.to, state.today), style = Resurge.type.caption, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "4-week reel", "Milestone", "Goal reached". */
internal fun reelTitle(reel: com.resurgeai.core.model.ProgressReel): String = when (reel.kind) {
    com.resurgeai.core.model.ReelKind.GOAL -> "Goal reached"
    com.resurgeai.core.model.ReelKind.MILESTONE -> "Milestone"
    com.resurgeai.core.model.ReelKind.REGULAR -> ChronoUnit.DAYS.between(reel.from, reel.to).let { d -> if (d >= 14) "${d / 7}-week reel" else "$d-day reel" }
}

// --- Viewer ---

/** Full screen, newest first, swipe between photos. */
@Composable
private fun PhotoViewer(
    state: ProgressUiState,
    photos: List<ProgressPhoto>,
    start: Int,
    repository: ProgressRepository,
    onClose: () -> Unit,
    onDelete: (ProgressPhoto) -> Unit,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start) { photos.size }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EncryptedImage(repository, photos[page], PhotoVariant.FULL, Modifier.fillMaxWidth().aspectRatio(3f / 4f), contentScale = ContentScale.Fit)
            }
        }
        val current = photos.getOrNull(pager.currentPage) ?: return@Box
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(ResurgeIcons.ArrowLeft, "Close", onClick = onClose)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.label(current), style = Resurge.type.headline, color = Color.White)
                Text(shortDate(current.date(), state.today), style = Resurge.type.caption, color = Color.White.copy(alpha = 0.7f))
            }
            GlassButton(ResurgeIcons.Trash, "Delete photo", onClick = { onDelete(current) })
        }
        state.weights[current.id]?.let { kg ->
            Text(
                "Trend ${formatWeight(kg, state.units)}",
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
                    .clip(Resurge.shape.pill)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** A round translucent button over a photo or the camera. */
@Composable
internal fun GlassButton(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).background(if (active) Color.White else Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = label },
        contentPadding = PaddingValues(0.dp),
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = if (active) Color.Black else Color.White, size = 20.dp) }
}
