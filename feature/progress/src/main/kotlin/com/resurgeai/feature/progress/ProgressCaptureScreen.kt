package com.resurgeai.feature.progress

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.data.progress.PhotoVariant
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.ProgressPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
internal class ProgressCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keys: PhotoKeys,
    val repository: ProgressRepository,
    preferences: PreferencesRepository,
) : ViewModel() {
    val keyState: StateFlow<PhotoKeyState> = keys.state
    val photos: StateFlow<List<ProgressPhoto>> = repository.photos().catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val secure: StateFlow<Boolean> = preferences.progressScreenshotBlock.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** The shot waiting for Save / Retake. */
    private val _taken = MutableStateFlow<PreparedProgressPhoto?>(null)
    val taken: StateFlow<PreparedProgressPhoto?> = _taken.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            // First photo ever: make the key now, quietly. Needs a connection once.
            when (runCatching { keys.refresh() }.getOrNull()) {
                PhotoKeyState.NOT_SET_UP -> runCatching { keys.setUp() }.onFailure {
                    Log.w(TAG, "Key setup failed", it)
                    _error.value = OFFLINE
                }
                PhotoKeyState.OFFLINE -> _error.value = OFFLINE
                else -> Unit
            }
        }
    }

    fun prepare(file: File) {
        viewModelScope.launch {
            _busy.value = true
            val photo = withContext(Dispatchers.Default) {
                runCatching { ProgressImages.prepare(context, Uri.fromFile(file)) }.onFailure { Log.w(TAG, "Prepare failed", it) }.getOrNull()
            }
            file.delete()
            _busy.value = false
            if (photo == null) _error.value = "Something went wrong. Try again." else _taken.value = photo
        }
    }

    fun retake() {
        _taken.value = null
    }

    fun save(onSaved: () -> Unit) {
        val photo = _taken.value ?: return
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.add(photo.full, photo.thumb, photo.takenAt, photo.width, photo.height) }
                .onSuccess {
                    _taken.value = null
                    onSaved()
                }
                .onFailure {
                    Log.w(TAG, "Save failed", it)
                    _error.value = "Couldn't save. Try again."
                }
            _busy.value = false
        }
    }

    fun showError(message: String?) {
        _error.value = message
    }

    private companion object {
        const val TAG = "ProgressCapture"
        const val OFFLINE = "You're offline. Connect and try again."
    }
}

/** Self-timer choices; the shutter counts down from this. */
private val TIMERS = listOf(0, 3, 10)

/**
 * The progress camera: a 3:4 viewfinder with the last photo faintly over it (the ghost) so every
 * photo lines up, a self-timer and front/back lens. The first time, it makes the photo key quietly.
 * On a phone that can't open earlier photos it hands over to the Progress screen ([onNeedsKey]).
 */
@Composable
fun ProgressCaptureRoute(onDone: () -> Unit, onNeedsKey: () -> Unit) {
    val viewModel: ProgressCaptureViewModel = hiltViewModel()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keyState by viewModel.keyState.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val taken by viewModel.taken.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val secure by viewModel.secure.collectAsStateWithLifecycle()

    LaunchedEffect(keyState) {
        if (keyState == PhotoKeyState.LOCKED) onNeedsKey()
    }
    SecureWindow(secure)

    var front by rememberSaveable { mutableStateOf(true) }
    var ghostOn by rememberSaveable { mutableStateOf(true) }
    var timer by rememberSaveable { mutableIntStateOf(3) }
    var countdown by remember { mutableIntStateOf(0) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ratio43)
            .build()
    }
    val ghost = photos.lastOrNull()

    val shoot: () -> Unit = {
        scope.launch {
            for (n in timer downTo 1) {
                countdown = n
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                delay(1_000)
            }
            countdown = 0
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            val file = ProgressImages.newCaptureFile(context)
            // Front camera shots are saved mirrored, as the preview shows them.
            val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = front }
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = viewModel.prepare(file)
                    override fun onError(e: ImageCaptureException) {
                        Log.w("ProgressCapture", "Capture failed", e)
                        viewModel.showError("Something went wrong. Try again.")
                    }
                },
            )
        }
    }

    BackHandler(enabled = taken != null) { viewModel.retake() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(ResurgeIcons.ArrowLeft, "Back", onClick = onDone)
                Spacer(Modifier.weight(1f))
                if (ghost != null) GlassButton(ResurgeIcons.Ghost, if (ghostOn) "Hide last photo" else "Show last photo", active = ghostOn, onClick = { ghostOn = !ghostOn })
                Spacer(Modifier.width(8.dp))
                GlassButton(ResurgeIcons.CameraRotate, if (front) "Use back camera" else "Use front camera", onClick = { front = !front })
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(Resurge.shape.md)) {
                    if (hasCamera) {
                        CameraPreview(imageCapture, front, Modifier.fillMaxSize())
                        if (ghostOn && ghost != null) EncryptedImage(viewModel.repository, ghost, PhotoVariant.FULL, Modifier.fillMaxSize(), alpha = 0.35f)
                        Text(
                            if (ghost != null && ghostOn) "Line up with your last photo" else "Fit your whole body in",
                            style = Resurge.type.caption,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    } else {
                        CameraOff(onAllow = { askCamera.launch(Manifest.permission.CAMERA) })
                    }
                    if (countdown > 0) {
                        Text(
                            "$countdown",
                            style = Resurge.type.tide.copy(fontSize = 120.sp, lineHeight = 120.sp),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                error?.let {
                    Text(it, style = Resurge.type.label, color = Resurge.colors.text, modifier = Modifier.clip(Resurge.shape.sm).background(Resurge.colors.glowTint).padding(horizontal = 12.dp, vertical = 10.dp))
                }
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    UnstyledButton(
                        onClick = { timer = TIMERS[(TIMERS.indexOf(timer) + 1) % TIMERS.size] },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(52.dp).clip(Resurge.shape.pill).background(Color.White.copy(alpha = 0.15f))
                            .semantics { contentDescription = if (timer == 0) "Timer off" else "Timer ${timer} seconds" },
                        indication = LocalIndication.current,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(ResurgeIcons.Timer, contentDescription = null, tint = Color.White, size = 18.dp)
                            Text(if (timer == 0) "Off" else "${timer}s", style = Resurge.type.caption, color = Color.White)
                        }
                    }
                    Shutter(enabled = hasCamera && !busy && countdown == 0 && keyState == PhotoKeyState.READY, modifier = Modifier.align(Alignment.Center), onClick = shoot)
                }
            }
        }

        taken?.let { photo ->
            Review(photo, busy, onRetake = viewModel::retake, onSave = { viewModel.save(onDone) })
        }
        if (busy && taken == null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Spinner(color = Color.White) }
        }
    }
}

private val ratio43 = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

/** Live preview in 4:3 (the same field of view as the capture), rebinding when the lens flips. */
@Composable
private fun CameraPreview(imageCapture: ImageCapture, front: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner, front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().setResolutionSelector(ratio43).build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }.onFailure { Log.w("ProgressCapture", "Camera bind failed", it) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { if (future.isDone) future.get().unbindAll() } }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun CameraOff(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF16161C)).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ResurgeIcons.Camera, contentDescription = null, tint = Color.White, size = 40.dp)
        Spacer(Modifier.height(12.dp))
        Text("Allow camera access", style = Resurge.type.headline, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button("Allow camera", onClick = onAllow, variant = ButtonVariant.Accent)
    }
}

@Composable
private fun Shutter(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(76.dp).clip(Resurge.shape.pill).border(4.dp, Color.White, Resurge.shape.pill).padding(8.dp)
            .semantics { contentDescription = "Take photo" },
        indication = LocalIndication.current,
    ) {
        Box(Modifier.fillMaxSize().clip(Resurge.shape.pill).background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f)))
    }
}

/** The shot, full screen, with Retake and Save. */
@Composable
private fun Review(photo: PreparedProgressPhoto, busy: Boolean, onRetake: () -> Unit, onSave: () -> Unit) {
    val bitmap: ImageBitmap? = remember(photo) {
        android.graphics.BitmapFactory.decodeByteArray(photo.full, 0, photo.full.size)?.asImageBitmap()
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(56.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it, contentDescription = "Your photo", modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(Resurge.shape.md), contentScale = ContentScale.Crop) }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button("Retake", onClick = onRetake, enabled = !busy, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
            Button("Save", onClick = onSave, loading = busy, variant = ButtonVariant.Accent, modifier = Modifier.weight(1.4f))
        }
    }
}
