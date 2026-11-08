package com.resurgeai.feature.food

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.resurgeai.core.data.food.MealAnalysisException
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
import java.util.concurrent.Executors
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

    /** A barcode is being looked up. */
    private val _lookingUp = MutableStateFlow(false)
    val lookingUp: StateFlow<Boolean> = _lookingUp.asStateFlow()

    /** A scanned barcode nobody knows yet: the next photo should be its nutrition label. */
    private val _labelFor = MutableStateFlow<String?>(null)
    val labelFor: StateFlow<String?> = _labelFor.asStateFlow()

    /**
     * Looks a scanned barcode up (no model call). A known product goes straight to review; an
     * unknown one asks for a photo of its label, which is then read with the barcode attached.
     */
    fun useBarcode(code: String, onReady: () -> Unit, onNeedLabel: () -> Unit) {
        if (_lookingUp.value) return
        viewModelScope.launch {
            _lookingUp.value = true
            _error.value = null
            try {
                val product = repository.lookupBarcode(code)
                if (product != null) {
                    draftStore.put(MealDraft(null, null, barcode = code, product = product))
                    onReady()
                } else {
                    _labelFor.value = code
                    _error.value = "This product isn't in the database yet. Take a photo of its nutrition label."
                    onNeedLabel()
                }
            } catch (e: MealAnalysisException) {
                _error.value = e.message
            } finally {
                _lookingUp.value = false
            }
        }
    }

    fun clearLabel() {
        _labelFor.value = null
    }

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
                draftStore.put(MealDraft(photo, null, barcode = _labelFor.value))
                _labelFor.value = null
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

/** The ways in: the in-app camera, a barcode, a gallery photo, or a description (typed or spoken). */
private enum class LogMode(val label: String) { PHOTO("Photo"), BARCODE("Barcode"), GALLERY("Gallery"), DESCRIBE("Describe") }

/**
 * The meal logger: a full-screen in-app camera with frame corners and one hint, a Photo / Barcode /
 * Gallery / Describe switch, and the shutter. Barcode mode reads EAN/UPC codes on the device and
 * looks the product up; Describe takes typed or spoken words. The last meal's photo by the shutter
 * opens recent meals for a one-tap "log again". [mode] "type" opens Describe straight away (from
 * the meal reminder).
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
    val lookingUp by viewModel.lookingUp.collectAsStateWithLifecycle()
    val labelFor by viewModel.labelFor.collectAsStateWithLifecycle()
    var logMode by rememberSaveable { mutableStateOf(if (mode == "type") LogMode.DESCRIBE else LogMode.PHOTO) }
    var showRecent by rememberSaveable { mutableStateOf(false) }
    var flash by rememberSaveable { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    LaunchedEffect(Unit) { if (!hasCamera && logMode.usesCamera) askCamera.launch(Manifest.permission.CAMERA) }

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
        val onBarcode: ((String) -> Unit)? = if (logMode == LogMode.BARCODE && !lookingUp) {
            { code ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.useBarcode(code, onReview, onNeedLabel = { logMode = LogMode.PHOTO })
            }
        } else {
            null
        }
        if (hasCamera) CameraPreview(imageCapture, onBarcode, Modifier.fillMaxSize()) else CameraOff(onAllow = { askCamera.launch(Manifest.permission.CAMERA) })
        if (logMode == LogMode.DESCRIBE) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        // Top: back, title, flash.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(ResurgeIcons.ArrowLeft, "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    logMode == LogMode.DESCRIBE -> "Describe a meal"
                    logMode == LogMode.BARCODE -> "Scan a barcode"
                    labelFor != null -> "Photograph the label"
                    else -> "Scan a meal"
                },
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

        if (logMode.usesCamera && hasCamera) {
            Text(
                when {
                    logMode == LogMode.BARCODE -> if (lookingUp) "Looking up the product…" else "Point at the barcode on the pack"
                    labelFor != null -> "Fit the nutrition table in the frame"
                    else -> "Fit the whole plate in the frame"
                },
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
            FrameCorners(Modifier.align(Alignment.Center).offset(y = (-40).dp).fillMaxWidth(0.82f).height(if (logMode == LogMode.BARCODE) 150.dp else 300.dp))
        }

        // Bottom: what's going on, the describe card, the mode switch and the shutter row.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (preparing || lookingUp) {
                Row(
                    Modifier.clip(Resurge.shape.pill).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spinner(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (lookingUp) "Looking up the product…" else "Preparing photo…", style = Resurge.type.label, color = Color.White)
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
                    LogMode.PHOTO, LogMode.BARCODE -> {
                        if (picked == LogMode.BARCODE) viewModel.clearLabel()
                        logMode = picked
                        if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA)
                    }
                    LogMode.DESCRIBE -> logMode = LogMode.DESCRIBE
                }
            }
            if (logMode == LogMode.PHOTO || logMode == LogMode.GALLERY) {
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

/**
 * Live camera preview, bound to this screen's lifecycle, with [imageCapture] for the shutter. With
 * [onBarcode], frames also go to ML Kit's on-device barcode reader; a code is reported once it has
 * been read the same way twice in a row, so a blurry frame can't pick the wrong product.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(imageCapture: ImageCapture, onBarcode: ((String) -> Unit)?, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val scanning = onBarcode != null
    val latest = rememberUpdatedState(onBarcode)
    DisposableEffect(lifecycleOwner, scanning) {
        val future = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = if (scanning) {
            BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                    .build(),
            )
        } else {
            null
        }
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                provider.unbindAll()
                if (scanner == null) {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                } else {
                    var last: String? = null
                    var reported = false
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val media = proxy.image
                        if (media == null || reported) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                val code = codes.firstNotNullOfOrNull { b -> b.rawValue?.takeIf { v -> v.length in 8..14 && v.all(Char::isDigit) } }
                                if (code != null && code == last && !reported) {
                                    reported = true
                                    latest.value?.invoke(code)
                                }
                                last = code
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }.onFailure { Log.w("MealScanner", "Camera bind failed", it) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { if (future.isDone) future.get().unbindAll() }
            scanner?.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private val LogMode.usesCamera: Boolean get() = this == LogMode.PHOTO || this == LogMode.BARCODE

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

/** Describe mode: what you ate, typed or spoken, then the agent matches it to nutrition tables. */
@Composable
private fun DescribeCard(autoFocus: Boolean, onSubmit: (String) -> Unit) {
    val text = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    val submit = { onSubmit(text.text.toString()) }
    val listen = rememberSpeechInput(text)
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("What did you eat?", style = Resurge.type.headline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                InputField(text, placeholder = "e.g. 2 rotis, dal and a bowl of curd", maxLength = 300, onDone = submit, modifier = Modifier.focusRequester(focus))
            }
            if (listen != null) {
                Spacer(Modifier.width(8.dp))
                UnstyledButton(
                    onClick = listen,
                    role = Role.Button,
                    modifier = Modifier.size(44.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).semantics { contentDescription = "Speak instead" },
                    indication = LocalIndication.current,
                ) { Icon(ResurgeIcons.Microphone, contentDescription = null, size = 20.dp) }
            }
        }
        Button(
            "Log it",
            onClick = submit,
            enabled = text.text.isNotBlank(),
            variant = ButtonVariant.Accent,
            leadingIcon = ResurgeIcons.Sparkles,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Speech to text with the phone's own recognizer (Indian English, so Hinglish works), added to
 * [into]. Null when the phone has no recognizer.
 */
@Composable
private fun rememberSpeechInput(into: TextFieldState): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What did you eat?")
    }
    val available = remember { intent.resolveActivity(context.packageManager) != null }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (heard.isNotEmpty()) {
            val current = into.text.toString().trim()
            into.setTextAndPlaceCursorAtEnd(if (current.isEmpty()) heard else "$current, $heard")
        }
    }
    return if (available) ({ runCatching { launcher.launch(intent) } }) else null
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
