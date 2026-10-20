package com.resurgeai.core.scale

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.resurgeai.core.scale.protocol.Advertisement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for scale advertisements. Two ways:
 *
 * - [scan]: an active scan while something (a screen, the weigh-in service) is collecting it.
 * - [watch]: a scan the system runs for us with the app closed, waking [PendingIntent] when a
 *   paired scale is heard. Filtered by address, so the Bluetooth chip does the matching and it
 *   costs next to nothing while nobody is on the scale.
 */
@Singleton
class ScaleScanner @Inject constructor(private val bluetooth: ScaleBluetooth) {

    private fun scanner(): BluetoothLeScanner? = bluetooth.adapter()?.takeIf { it.isEnabled }?.bluetoothLeScanner

    /** Advertisements from [addresses], or from everything nearby when null (only while the screen is on). */
    @SuppressLint("MissingPermission")
    fun scan(addresses: Set<String>?): Flow<Advertisement> {
        if (!bluetooth.hasScanPermission()) return emptyFlow()
        val scanner = scanner() ?: return emptyFlow()
        return callbackFlow {
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    trySend(result.toAdvertisement())
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { trySend(it.toAdvertisement()) }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "Scan failed: $errorCode")
                    close()
                }
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            runCatching { scanner.startScan(filters(addresses), settings, callback) }
                .onFailure { Log.w(TAG, "Couldn't start scan", it); close() }
            awaitClose { runCatching { scanner.stopScan(callback) } }
        }
    }

    /** Asks the system to wake [target] whenever one of [addresses] advertises. False if it couldn't. */
    @SuppressLint("MissingPermission")
    fun watch(addresses: Set<String>, target: PendingIntent): Boolean {
        if (addresses.isEmpty() || !bluetooth.canListenInBackground()) return false
        val scanner = scanner() ?: return false
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        return runCatching { scanner.startScan(filters(addresses), settings, target) == 0 }
            .onFailure { Log.w(TAG, "Couldn't start background scan", it) }
            .getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun unwatch(target: PendingIntent) {
        if (!bluetooth.hasScanPermission()) return
        runCatching { scanner()?.stopScan(target) }
    }

    private fun filters(addresses: Set<String>?): List<ScanFilter> =
        addresses?.map { ScanFilter.Builder().setDeviceAddress(it).build() }.orEmpty()

    companion object {
        private const val TAG = "ScaleScanner"

        /** Advertisements delivered to a [watch] PendingIntent, or empty for an error or anything else. */
        fun advertisementsFrom(intent: Intent): List<Advertisement> {
            if (intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0) != 0) return emptyList()
            return IntentCompat.getParcelableArrayListExtra(intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
                ?.map { it.toAdvertisement() }
                .orEmpty()
        }
    }
}

/** Reads the name from the advertisement itself; asking the device for it needs a connect permission. */
fun ScanResult.toAdvertisement(): Advertisement {
    val record = scanRecord
    val manufacturer = buildMap<Int, ByteArray> {
        record?.manufacturerSpecificData?.let { data -> for (i in 0 until data.size()) put(data.keyAt(i), data.valueAt(i)) }
    }
    val services = buildMap<UUID, ByteArray> {
        record?.serviceData?.forEach { (uuid, bytes) -> if (bytes != null) put(uuid.uuid, bytes) }
    }
    return Advertisement(
        address = device.address,
        name = record?.deviceName,
        rssi = rssi,
        manufacturerData = manufacturer,
        serviceData = services,
        serviceUuids = record?.serviceUuids?.map { it.uuid }.orEmpty(),
    )
}
