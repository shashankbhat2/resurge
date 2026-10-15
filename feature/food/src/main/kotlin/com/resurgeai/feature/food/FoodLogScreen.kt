package com.resurgeai.feature.food

import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.SoftNote
import com.resurgeai.core.designsystem.components.SectionLabel
import com.composeunstyled.UnstyledButton
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.OptionTile
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
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
                    .onFailure { android.util.Log.w("MealPhotos", "Preparing photo failed: $uri", it) }
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

/**
 * How to log: scan with the camera, pick a photo, type a description, or repeat a recent meal.
 * [mode] "scan" opens the camera straight away and "type" focuses the text field (from the
 * meal reminder's actions).
 */
@Composable
fun FoodLogRoute(
    mode: String?,
    onReview: () -> Unit,
    onBack: () -> Unit,
    viewModel: FoodLogViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    val text = rememberTextFieldState()
    val focus = remember { FocusRequester() }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null) viewModel.usePhoto(context, uri, onReview)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.usePhoto(context, uri, onReview)
    }
    val openCamera = {
        val uri = MealPhotos.newCaptureUri(context)
        captureUri = uri
        runCatching { camera.launch(uri) }
    }
    LaunchedEffect(mode) {
        if (autoStarted) return@LaunchedEffect
        autoStarted = true
        when (mode) {
            "scan" -> openCamera()
            "type" -> runCatching { focus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Log a meal", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The two photo routes, side by side: scanning is the quick path, so it leads.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoTile(
                    icon = ResurgeIcons.Camera,
                    title = "Scan meal",
                    body = "Photo of your plate",
                    primary = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { openCamera() },
                )
                PhotoTile(
                    icon = ResurgeIcons.Photo,
                    title = "Choose photo",
                    body = "From your gallery",
                    primary = false,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
            if (preparing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Spinner(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Preparing photo…", style = Resurge.type.label, color = Resurge.colors.textMuted)
                }
            }
            error?.let { SoftNote(it) }

            SectionLabel("Or describe it", Modifier.padding(top = 4.dp))
            val submit = { viewModel.useText(text.text.toString(), onReview) }
            FoodGroup {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            if (recent.isNotEmpty()) {
                SectionLabel("Log again", Modifier.padding(top = 4.dp), trailing = "One tap, logged now")
                FoodGroup {
                    recent.forEachIndexed { i, meal ->
                        if (i > 0) GroupDivider(inset = 76.dp)
                        RecentRow(meal) { viewModel.logAgain(meal); onBack() }
                    }
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A large tappable tile for a photo route; the primary one is filled with `brand-tint`. */
@Composable
private fun PhotoTile(icon: Int, title: String, body: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier.clip(Resurge.shape.lg).background(if (primary) Resurge.colors.accentMuted else Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp).clip(Resurge.shape.pill).background(if (primary) Resurge.colors.brandInk else Resurge.colors.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = if (primary) Resurge.colors.onAccent else Resurge.colors.text, size = 24.dp) }
            Column {
                Text(title, style = Resurge.type.headline, color = if (primary) Resurge.colors.brandInk else Resurge.colors.text)
                Text(body, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
    }
}

@Composable
private fun RecentRow(meal: Meal, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
