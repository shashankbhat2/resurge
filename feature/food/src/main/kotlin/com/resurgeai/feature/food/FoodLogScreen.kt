package com.resurgeai.feature.food

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class FoodLogViewModel @Inject constructor(
    private val repository: MealsRepository,
    private val draftStore: MealDraftStore,
) : ViewModel() {
    /** Distinct recent meals for one-tap re-logging. */
    val recent: StateFlow<List<Meal>> = repository.meals(LocalDate.now().minusDays(14))
        .map { meals -> meals.distinctBy { it.title.lowercase() }.take(6) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun usePhoto(context: Context, uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            _preparing.value = true
            val photo = withContext(Dispatchers.IO) {
                runCatching { MealPhotos.prepare(context, uri) }
                    .onFailure { Log.w("MealPhotos", "Preparing photo failed: $uri", it) }
                    .getOrNull()
            }
            _preparing.value = false
            if (photo == null) {
                _error.value = "Couldn't open that photo. Try another one."
            } else {
                _error.value = null
                draftStore.put(MealDraft(photo, null))
                onReady()
            }
        }
    }

    fun showError(message: String) {
        _error.value = message
    }

    fun useText(text: String, onReady: () -> Unit) {
        if (text.isBlank()) return
        draftStore.put(MealDraft(null, text.trim()))
        onReady()
    }

    /** Logs a copy of a recent meal now, in the slot for the current time. */
    fun logAgain(meal: Meal) {
        repository.save(null, MealType.forTime(LocalTime.now()), Instant.now(), meal.source, meal.title, meal.items, meal.thumbnail, null)
    }
}

/** The three ways in: the in-app camera, a gallery photo, or a description. */
private enum class LogMode(val label: String) { PHOTO("Photo"), GALLERY("Gallery"), DESCRIBE("Describe") }

/**
 * The meal scanner: a full-screen in-app camera with frame corners and one hint, a Photo / Gallery /
 * Describe switch, and the shutter. The last meal's photo by the shutter opens recent meals for a
 * one-tap "log again". [mode] "type" opens Describe straight away (from the meal reminder).
 */
@Composable
fun FoodLogRoute(
    mode: String?,
    onReview: () -> Unit,
    onBack: () -> Unit,
    viewModel: FoodLogViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var logMode by rememberSaveable { mutableStateOf(if (mode == "type") LogMode.DESCRIBE else LogMode.PHOTO) }
    var showRecent by rememberSaveable { mutableStateOf(false) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera && logMode == LogMode.PHOTO) askCamera.launch(Manifest.permission.CAMERA) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.usePhoto(context, uri, onReview)
        logMode = LogMode.PHOTO
    }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    LaunchedEffect(flash) { imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF }
    val shoot = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val file = MealPhotos.newCaptureFile(context)
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = viewModel.usePhoto(context, Uri.fromFile(file), onReview)
                override fun onError(e: ImageCaptureException) {
                    Log.w("MealScanner", "Capture failed", e)
                    viewModel.showError("Couldn't take the photo. Try again, or pick one from your gallery.")
                }
            },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black).imePadding()) {
        if (hasCamera) CameraPreview(imageCapture, Modifier.fillMaxSize()) else CameraOff(onAllow = { askCamera.launch(Manifest.permission.CAMERA) })
        if (logMode == LogMode.DESCRIBE) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        // Top: back, title, flash.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(ResurgeIcons.ArrowLeft, "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                if (logMode == LogMode.DESCRIBE) "Describe a meal" else "Scan a meal",
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                color = Color.White,
                modifier = Modifier.clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            if (logMode == LogMode.PHOTO && hasCamera) {
                GlassButton(ResurgeIcons.Bolt, if (flash) "Flash on" else "Flash off", active = flash, onClick = { flash = !flash })
            } else {
                Spacer(Modifier.size(40.dp))
            }
        }

        if (logMode == LogMode.PHOTO && hasCamera) {
            Text(
                "Fit the whole plate in the frame",
                style = Resurge.type.caption,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp)
                    .clip(Resurge.shape.pill)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            FrameCorners(Modifier.align(Alignment.Center).offset(y = (-40).dp).fillMaxWidth(0.82f).height(300.dp))
        }

        // Bottom: what's going on, the describe card, the mode switch and the shutter row.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (preparing) {
                Row(
                    Modifier.clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing photo…", style = Resurge.type.label, color = Color.White)
                }
            }
            error?.let {
                Text(
                    it,
                    style = Resurge.type.label,
                    color = Resurge.colors.text,
                    modifier = Modifier.clip(Resurge.shape.sm).background(Resurge.colors.glowTint).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            if (logMode == LogMode.DESCRIBE) DescribeCard(autoFocus = true) { text -> viewModel.useText(text, onReview) }
            ModeSwitch(logMode) { picked ->
                when (picked) {
                    LogMode.GALLERY -> {
                        logMode = LogMode.GALLERY
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    LogMode.PHOTO -> {
                        logMode = LogMode.PHOTO
                        if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA)
                    }
                    LogMode.DESCRIBE -> logMode = LogMode.DESCRIBE
                }
            }
            if (logMode != LogMode.DESCRIBE) {
                Box(Modifier.fillMaxWidth().height(80.dp)) {
                    recent.firstOrNull()?.let { last ->
                        UnstyledButton(
                            onClick = { showRecent = true },
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(48.dp).clip(Resurge.shape.sm).border(2.dp, Color.White, Resurge.shape.sm)
                                .semantics { contentDescription = "Log a recent meal again" },
                            indication = LocalIndication.current,
                        ) { MealThumb(last.thumbnail, size = 48.dp) }
                    }
                    Shutter(enabled = hasCamera && !preparing, modifier = Modifier.align(Alignment.Center), onClick = shoot)
                }
            }
        }

        if (showRecent) RecentSheet(recent, onDismiss = { showRecent = false }) { meal ->
            viewModel.logAgain(meal)
            onBack()
        }
    }
}

/** Live camera preview, bound to this screen's lifecycle, with [imageCapture] for the shutter. */
@Composable
private fun CameraPreview(imageCapture: ImageCapture, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure { Log.w("MealScanner", "Camera bind failed", it) }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { if (future.isDone) future.get().unbindAll() } }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
private fun CameraOff(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ResurgeIcons.Camera, contentDescription = null, tint = Color.White, size = 40.dp)
        Spacer(Modifier.height(12.dp))
        Text("Scan meals with your camera", style = Resurge.type.headline, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Allow camera access, or use Gallery or Describe below.", style = Resurge.type.body, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button("Allow camera", onClick = onAllow, variant = ButtonVariant.Accent)
    }
}

/** Corner brackets that frame the plate. */
@Composable
private fun FrameCorners(modifier: Modifier) {
    val stroke = 3.dp
    val arm = 34.dp
    Box(modifier) {
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { corner ->
            val top = corner == Alignment.TopStart || corner == Alignment.TopEnd
            val start = corner == Alignment.TopStart || corner == Alignment.BottomStart
            Box(Modifier.align(corner).size(arm)) {
                Box(Modifier.align(if (top) Alignment.TopStart else Alignment.BottomStart).fillMaxWidth().height(stroke).clip(Resurge.shape.pill).background(Color.White))
                Box(Modifier.align(if (start) Alignment.TopStart else Alignment.TopEnd).width(stroke).fillMaxSize().clip(Resurge.shape.pill).background(Color.White))
            }
        }
    }
}

/** A round translucent button over the camera. */
@Composable
internal fun GlassButton(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).background(if (active) Color.White else Color.Black.copy(alpha = 0.45f)).semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = if (active) Color.Black else Color.White, size = 20.dp) }
}

@Composable
private fun ModeSwitch(selected: LogMode, onSelect: (LogMode) -> Unit) {
    Row(
        Modifier.clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogMode.entries.forEach { m ->
            val on = m == selected
            UnstyledButton(
                onClick = { onSelect(m) },
                role = Role.Tab,
                modifier = Modifier.clip(Resurge.shape.pill).background(if (on) Color.White else Color.Transparent).semantics { this.selected = on },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
                indication = LocalIndication.current,
            ) {
                Text(m.label, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = if (on) Color(0xFF111127) else Color.White.copy(alpha = 0.8f))
            }
        }
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

/** Describe mode: what you ate, in your own words, then Estimate. */
@Composable
private fun DescribeCard(autoFocus: Boolean, onSubmit: (String) -> Unit) {
    val text = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    val submit = { onSubmit(text.text.toString()) }
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What did you eat?", style = Resurge.type.headline)
        InputField(text, placeholder = "e.g. 2 rotis, dal and a bowl of curd", maxLength = 300, onDone = submit, modifier = Modifier.focusRequester(focus))
        Button(
            "Estimate nutrition",
            onClick = submit,
            enabled = text.text.isNotBlank(),
            variant = ButtonVariant.Accent,
            leadingIcon = ResurgeIcons.Sparkles,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Recent meals over the camera: one tap logs a copy now. */
@Composable
private fun RecentSheet(recent: List<Meal>, onDismiss: () -> Unit, onLog: (Meal) -> Unit) {
    Box(Modifier.fillMaxSize().background(Resurge.colors.bg.copy(alpha = 0f)).clickable(interactionSource = null, indication = null, onClick = onDismiss)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Resurge.colors.surface)
                .clickable(interactionSource = null, indication = null) {}
                .windowInsetsPadding(WindowInsets.navigationBars)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
            Text("Log again", style = Resurge.type.title, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
            Text("Logged now, in the slot for this time of day.", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
            recent.forEachIndexed { i, meal ->
                if (i > 0) GroupDivider(inset = 84.dp)
                RecentRow(meal) { onLog(meal) }
            }
        }
    }
}

@Composable
private fun RecentRow(meal: Meal, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MealThumb(meal.thumbnail, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meal.title, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${kcal(meal.totals.kcal)} · ${meal.type.label}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Spacer(Modifier.width(8.dp))
        UnstyledButton(
            onClick = onLog,
            modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).semantics { contentDescription = "Log ${meal.title} again" },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            indication = LocalIndication.current,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.text, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("Log", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight))
            }
        }
    }
}
