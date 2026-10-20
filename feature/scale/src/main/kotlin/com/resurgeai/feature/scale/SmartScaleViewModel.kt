package com.resurgeai.feature.scale

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.scale.PairedScale
import com.resurgeai.core.scale.ScaleBluetooth
import com.resurgeai.core.scale.ScaleControl
import com.resurgeai.core.scale.ScaleReadiness
import com.resurgeai.core.scale.ScaleScanner
import com.resurgeai.core.scale.ScaleStore
import com.resurgeai.core.scale.WeighInEngine
import com.resurgeai.core.scale.protocol.ScaleProtocols
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** What the phone itself allows right now, re-read every second while the screen is shown. */
internal data class PhoneState(
    val readiness: ScaleReadiness = ScaleReadiness.READY,
    /** Android 10 and 11: "Allow all the time" location, needed to hear the scale with the app closed. */
    val needsBackgroundPermission: Boolean = false,
    /** Android 13+: notifications are off, so background weigh-ins arrive silently. */
    val needsNotifications: Boolean = false,
)

/** A scale heard nearby while finding one. [kg] is its latest reading, if it showed one. */
internal data class FoundScale(
    val address: String,
    val protocolId: String,
    val model: String,
    val kg: Double?,
    /** Strongest signal heard, so the scale you're standing next to sorts first. */
    val rssi: Int,
)

internal data class SmartScaleUiState(
    val loading: Boolean = true,
    val units: UnitSystem = UnitSystem.METRIC,
    val scales: List<PairedScale> = emptyList(),
    val autoLog: Boolean = true,
    val askWhenUnusual: Boolean = true,
    val lastReadingAt: Instant? = null,
    val writeToHealthConnect: Boolean = false,
    val healthConnectAvailable: Boolean = false,
    /** The person asked to find a scale (or another one). */
    val finding: Boolean = false,
    val phone: PhoneState = PhoneState(),
    val families: List<String> = emptyList(),
) {
    val mode: SmartScaleMode get() = when {
        finding -> SmartScaleMode.FINDING
        scales.isEmpty() -> SmartScaleMode.INTRO
        else -> SmartScaleMode.PAIRED
    }
}

internal enum class SmartScaleMode { INTRO, FINDING, PAIRED }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmartScaleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ScaleStore,
    private val bluetooth: ScaleBluetooth,
    private val scanner: ScaleScanner,
    private val engine: WeighInEngine,
    private val control: ScaleControl,
    private val preferences: PreferencesRepository,
    private val healthConnect: HealthConnectManager,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val finding = MutableStateFlow(false)

    private val units = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.units }.onStart { emit(UnitSystem.METRIC) }

    private val phone = flow {
        while (true) {
            emit(readPhone())
            delay(1_000)
        }
    }.distinctUntilChanged()

    internal val state: StateFlow<SmartScaleUiState> = combine(
        store.settings,
        preferences.weightWriteToHealthConnect.catch { emit(false) },
        units,
        phone,
        finding,
    ) { settings, writeHc, units, phone, finding ->
        SmartScaleUiState(
            loading = false,
            units = units,
            scales = settings.scales,
            autoLog = settings.autoLog,
            askWhenUnusual = settings.askWhenUnusual,
            lastReadingAt = settings.lastReadingAt,
            writeToHealthConnect = writeHc,
            healthConnectAvailable = healthConnect.availability() == HealthConnectAvailability.AVAILABLE,
            finding = finding,
            phone = phone,
            families = ScaleProtocols.families,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmartScaleUiState())

    val scanPermissions: Array<String> get() = bluetooth.scanPermissions
    val backgroundPermission: String? get() = bluetooth.backgroundPermission
    val healthConnectContract = healthConnect.permissionContract()
    val writeWeightPermission: String = HealthPermissions.WRITE_WEIGHT

    fun bluetoothSettingsIntent(): Intent = bluetooth.bluetoothSettingsIntent()
    fun locationSettingsIntent(): Intent = bluetooth.locationSettingsIntent()

    /** This app's page in system settings, for a permission that was turned down for good. */
    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appSettingsIntent()
        }

    fun startFinding() {
        finding.value = true
    }

    fun stopFinding() {
        finding.value = false
    }

    // ---- Finding: an open scan, only while the finding list is on screen ----

    private val _nearby = MutableStateFlow<List<FoundScale>>(emptyList())
    internal val nearby: StateFlow<List<FoundScale>> = _nearby.asStateFlow()
    private var scanJob: Job? = null

    /** Starts listening to everything nearby; the finding list calls this when it starts and [stopScan] when it stops. */
    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val paired = store.current().scales.map { it.address.uppercase() }.toSet()
            phone.map { it.readiness == ScaleReadiness.READY }.distinctUntilChanged()
                .flatMapLatest { ready -> if (ready) scanner.scan(null) else emptyFlow() }
                .collect { ad ->
                    if (ad.address.uppercase() in paired) return@collect
                    val match = ScaleProtocols.match(ad) ?: return@collect
                    val kg = match.protocol.decode(ad)?.kg?.takeIf { it >= WeighInEngine.MIN_PERSON_KG }
                    _nearby.update { list ->
                        val known = list.firstOrNull { it.address == ad.address }
                        // Some families are recognised loosely, so only list a device once it has shown a person's weight.
                        if (known == null && kg == null) return@update list
                        val next = FoundScale(
                            address = ad.address,
                            protocolId = match.protocol.id,
                            model = match.model,
                            kg = kg ?: known?.kg,
                            rssi = maxOf(ad.rssi, known?.rssi ?: Int.MIN_VALUE),
                        )
                        (list.filterNot { it.address == ad.address } + next).sortedByDescending { it.rssi }
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun pair(address: String) {
        val scale = _nearby.value.firstOrNull { it.address == address } ?: return
        viewModelScope.launch {
            store.pair(PairedScale(scale.address, scale.protocolId, scale.model, Instant.now()))
            control.syncBackgroundListening()
            _nearby.update { list -> list.filterNot { it.address == scale.address } }
            finding.value = false
        }
    }

    // ---- Paired ----

    fun forget(address: String) {
        viewModelScope.launch {
            store.forget(address)
            control.syncBackgroundListening()
        }
    }

    fun setAutoLog(on: Boolean) {
        viewModelScope.launch {
            store.setAutoLog(on)
            control.syncBackgroundListening()
        }
    }

    fun setAskWhenUnusual(on: Boolean) {
        viewModelScope.launch { store.setAskWhenUnusual(on) }
    }

    /** Turning off saves straight away; turning on goes through Health Connect's permission screen first. */
    fun setWriteToHealthConnect(on: Boolean) {
        viewModelScope.launch { preferences.setWeightWriteToHealthConnect(on) }
    }

    fun onHealthConnectResult(granted: Set<String>) {
        if (writeWeightPermission in granted) setWriteToHealthConnect(true)
    }

    /** After a permission answer, background listening may now be possible. */
    fun onPermissionResult() {
        control.syncBackgroundListening()
    }

    fun weighInNow() = control.weighInNow()

    private fun readPhone() = PhoneState(
        readiness = bluetooth.readiness(),
        needsBackgroundPermission = bluetooth.backgroundPermission?.let { !granted(it) } ?: false,
        needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(Manifest.permission.POST_NOTIFICATIONS),
    )

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
