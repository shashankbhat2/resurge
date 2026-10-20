package com.resurgeai.feature.scale

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.scale.PairedScale
import com.resurgeai.core.scale.ScaleReadiness
import java.time.LocalDate

/**
 * Smart scale: what it does and which scales work, finding one nearby, then the paired scales
 * and how readings are logged. The app wires navigation; this needs nothing else.
 */
@Composable
fun SmartScaleRoute(onBack: () -> Unit, viewModel: SmartScaleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val context = LocalContext.current
    fun open(intent: android.content.Intent) = runCatching { context.startActivity(intent) }

    var scanAsked by rememberSaveable { mutableStateOf(false) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scanAsked = true
        viewModel.onPermissionResult()
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onPermissionResult() }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAsked = true }
    val healthConnectLauncher = rememberLauncherForActivityResult(viewModel.healthConnectContract, viewModel::onHealthConnectResult)

    BackHandler(enabled = state.finding) { viewModel.stopFinding() }

    SmartScaleScreen(
        state = state,
        nearby = nearby,
        scanDenied = scanAsked,
        actions = SmartScaleActions(
            onBack = { if (state.finding) viewModel.stopFinding() else onBack() },
            onFind = viewModel::startFinding,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onAllowScan = { if (scanAsked) open(viewModel.appSettingsIntent()) else scanLauncher.launch(viewModel.scanPermissions) },
            onBluetoothSettings = { open(viewModel.bluetoothSettingsIntent()) },
            onLocationSettings = { open(viewModel.locationSettingsIntent()) },
            onPair = viewModel::pair,
            onForget = viewModel::forget,
            onAutoLog = viewModel::setAutoLog,
            onAskWhenUnusual = viewModel::setAskWhenUnusual,
            onHealthConnect = { on ->
                if (on) healthConnectLauncher.launch(setOf(viewModel.writeWeightPermission)) else viewModel.setWriteToHealthConnect(false)
            },
            onAllowBackground = { viewModel.backgroundPermission?.let(backgroundLauncher::launch) },
            onAllowNotifications = {
                if (notificationsAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) open(viewModel.notificationSettingsIntent())
                else notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            },
            onWeighInNow = viewModel::weighInNow,
        ),
    )
}

/** Everything the screen can ask for; no-ops by default so previews and tests stay short. */
internal class SmartScaleActions(
    val onBack: () -> Unit = {},
    val onFind: () -> Unit = {},
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onAllowScan: () -> Unit = {},
    val onBluetoothSettings: () -> Unit = {},
    val onLocationSettings: () -> Unit = {},
    val onPair: (address: String) -> Unit = {},
    val onForget: (address: String) -> Unit = {},
    val onAutoLog: (Boolean) -> Unit = {},
    val onAskWhenUnusual: (Boolean) -> Unit = {},
    val onHealthConnect: (Boolean) -> Unit = {},
    val onAllowBackground: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
    val onWeighInNow: () -> Unit = {},
)

@Composable
internal fun SmartScaleScreen(
    state: SmartScaleUiState,
    nearby: List<FoundScale>,
    actions: SmartScaleActions,
    /** The scan permission was asked for and turned down, so the button opens settings instead. */
    scanDenied: Boolean = false,
    today: LocalDate = LocalDate.now(),
) {
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        TopBar(if (state.mode == SmartScaleMode.FINDING) "Find your scale" else "Smart scale", onBack = actions.onBack)
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            return@Column
        }
        AnimatedContent(
            targetState = state.mode,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "smart-scale-mode",
            modifier = Modifier.weight(1f),
        ) { mode ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (mode) {
                    SmartScaleMode.INTRO -> Intro(state.families, actions.onFind)
                    SmartScaleMode.FINDING -> Finding(state, nearby, actions, scanDenied)
                    SmartScaleMode.PAIRED -> Paired(state, actions, scanDenied, today)
                }
            }
        }
    }
}

// ---- No scale yet ----

@Composable
private fun ColumnScope.Intro(families: List<String>, onFind: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ScaleBadge(size = 52)
        Spacer(Modifier.height(8.dp))
        Text("Weigh in without typing", style = Resurge.type.title)
        Text("Step on your scale and Resurge logs it. No pairing, no other app.", style = Resurge.type.body, color = Resurge.colors.textMuted)
        Spacer(Modifier.height(10.dp))
        Button("Find my scale", onClick = onFind, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
    }
    WorksWith(families)
}

@Composable
private fun WorksWith(families: List<String>) {
    GroupLabel("Works with")
    Group {
        families.forEachIndexed { i, family ->
            if (i > 0) Divider(Modifier.padding(start = 16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.brandInk, size = 16.dp)
                Spacer(Modifier.width(12.dp))
                Text(family, style = Resurge.type.body)
            }
        }
    }
}

// ---- Finding ----

@Composable
private fun ColumnScope.Finding(state: SmartScaleUiState, nearby: List<FoundScale>, actions: SmartScaleActions, scanDenied: Boolean) {
    // Listen to everything nearby only while this list is on screen.
    LifecycleStartEffect(Unit) {
        actions.onStartScan()
        onStopOrDispose { actions.onStopScan() }
    }
    val readiness = state.phone.readiness
    if (readiness != ScaleReadiness.READY) {
        Blocker(readiness, actions, scanDenied)
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Step on your scale to wake it up.", style = Resurge.type.headline)
            Text(
                if (nearby.isEmpty()) "Looking for scales nearby" else "Tap yours to start logging from it",
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Spinner(Modifier.size(22.dp))
    }
    if (nearby.isNotEmpty()) {
        GroupLabel(if (nearby.size == 1) "Found nearby" else "Found ${nearby.size} nearby")
        Group {
            nearby.forEachIndexed { i, found ->
                if (i > 0) Divider(Modifier.padding(start = 64.dp))
                FoundRow(found, WeightText(state.units)) { actions.onPair(found.address) }
            }
        }
    }
    Footnote(
        "Don't see yours? Resurge works with scales that show your weight in their Bluetooth broadcasts, " +
            "like the ones it already knows. More are coming.",
    )
}

@Composable
private fun FoundRow(found: FoundScale, weight: WeightText, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        indication = LocalIndication.current,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${found.model}${found.kg?.let { ", reading ${weight.format(it)}" } ?: ""}. Use this scale" },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScaleBadge(size = 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(found.model, style = Resurge.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Tap to use this scale", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
            found.kg?.let {
                Spacer(Modifier.width(12.dp))
                Text(weight.format(it), style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"))
            }
            Spacer(Modifier.width(6.dp))
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
        }
    }
}

/** The one thing between the phone and hearing a scale, with the way past it. */
@Composable
private fun Blocker(readiness: ScaleReadiness, actions: SmartScaleActions, scanDenied: Boolean) {
    val oldAndroid = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val (title, body, action) = when (readiness) {
        ScaleReadiness.UNSUPPORTED -> Triple("This phone can't hear smart scales", "It doesn't have Bluetooth Low Energy, which scales use to share their reading.", null)
        ScaleReadiness.NEEDS_PERMISSION -> Triple(
            if (oldAndroid) "Allow location" else "Allow Nearby devices",
            if (oldAndroid) "This version of Android asks for location to find Bluetooth devices. Resurge doesn't use where you are."
            else "Resurge only listens for your scale's reading. It never connects to other devices or uses your location.",
            (if (scanDenied) "Open settings" else "Allow") to actions.onAllowScan,
        )
        ScaleReadiness.BLUETOOTH_OFF -> Triple("Turn on Bluetooth", "Your scale shares its reading over Bluetooth.", "Bluetooth settings" to actions.onBluetoothSettings)
        ScaleReadiness.LOCATION_OFF -> Triple(
            "Turn on location",
            "This version of Android only finds Bluetooth devices with location on. Resurge doesn't use where you are.",
            "Location settings" to actions.onLocationSettings,
        )
        ScaleReadiness.READY -> return
    }
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = Resurge.type.headline)
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted)
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(8.dp))
            Button(label, onClick = onClick, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---- Paired ----

@Composable
private fun ColumnScope.Paired(state: SmartScaleUiState, actions: SmartScaleActions, scanDenied: Boolean, today: LocalDate) {
    if (state.phone.readiness != ScaleReadiness.READY) Blocker(state.phone.readiness, actions, scanDenied)

    val last = state.lastReadingAt?.let { lastWeighIn(it, today, clockFormatter()) }
    GroupLabel(if (state.scales.size == 1) "Your scale" else "Your scales")
    Group {
        state.scales.forEachIndexed { i, scale ->
            if (i > 0) Divider(Modifier.padding(start = 64.dp))
            ScaleRow(scale, subtitle = if (state.scales.size == 1) last ?: "No weigh-ins yet" else scale.protocol?.family, onForget = { actions.onForget(scale.address) })
        }
    }
    if (state.scales.size > 1 && last != null) Footnote(last)

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button("Weigh in now", onClick = actions.onWeighInNow, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Scale, modifier = Modifier.weight(1f))
        Button("Add another", onClick = actions.onFind, variant = ButtonVariant.Ghost, leadingIcon = ResurgeIcons.Plus, modifier = Modifier.weight(1f))
    }

    GroupLabel("Logging")
    Group {
        ToggleRow("Log automatically", "Works with the app closed, while Bluetooth is on.", state.autoLog, actions.onAutoLog)
        if (state.autoLog && state.phone.needsBackgroundPermission) {
            Notice(
                "Android 10 and 11 only let Resurge hear your scale in the background with location set to “Allow all the time”. It doesn't use where you are.",
                "Allow all the time",
                actions.onAllowBackground,
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            )
        }
        Divider(Modifier.padding(start = 16.dp))
        ToggleRow(
            "Ask when a reading looks like someone else's",
            "For shared scales. A reading far from your recent weight waits until you say it's yours.",
            state.askWhenUnusual,
            actions.onAskWhenUnusual,
        )
        if (state.healthConnectAvailable) {
            Divider(Modifier.padding(start = 16.dp))
            ToggleRow("Save weigh-ins to Health Connect", "So Google Health, Fitbit and other apps see them too.", state.writeToHealthConnect, actions.onHealthConnect)
        }
    }
    if (state.phone.needsNotifications) {
        Spacer(Modifier.height(4.dp))
        Notice("Allow notifications to see each weigh-in and undo it.", "Allow", actions.onAllowNotifications)
    }
}

@Composable
private fun ScaleRow(scale: PairedScale, subtitle: String?, onForget: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ScaleBadge(size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(scale.model, style = Resurge.type.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1) }
        }
        UnstyledButton(
            onClick = onForget,
            role = Role.Button,
            indication = LocalIndication.current,
            modifier = Modifier.clip(Resurge.shape.pill).semantics { contentDescription = "Forget ${scale.model}" },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Forget", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
    }
}

@Composable
private fun ToggleRow(label: String, footnote: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Resurge.type.body)
            Text(footnote, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked, onChange, accessibilityLabel = label)
    }
}

/** Something to allow, with a button: a gentle tinted note rather than a warning. */
@Composable
private fun Notice(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.glowTint).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(ResurgeIcons.Bell, contentDescription = null, tint = Resurge.colors.glowInk, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text(text, style = Resurge.type.caption.copy(lineHeight = Resurge.type.label.lineHeight), color = Resurge.colors.text)
        }
        UnstyledButton(
            onClick = onClick,
            role = Role.Button,
            indication = LocalIndication.current,
            modifier = Modifier.padding(start = 28.dp).clip(Resurge.shape.pill).background(Resurge.colors.surface),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(action, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = Resurge.colors.glowInk)
        }
    }
}

// ---- Shared pieces ----

@Composable
private fun ScaleBadge(size: Int) {
    Box(Modifier.size(size.dp).clip(Resurge.shape.pill).background(Resurge.colors.accentMuted), contentAlignment = Alignment.Center) {
        Icon(ResurgeIcons.Scale, contentDescription = null, tint = Resurge.colors.brandInk, size = (size / 2).dp)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = Resurge.type.overline,
        color = Resurge.colors.textMuted,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface), content = content)
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
}
