package com.resurgeai.core.scale

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What stands between the phone and hearing a scale, worst first. */
enum class ScaleReadiness {
    UNSUPPORTED,
    NEEDS_PERMISSION,
    BLUETOOTH_OFF,
    /** Android 11 and below only scan for Bluetooth devices with location turned on. */
    LOCATION_OFF,
    READY,
}

/**
 * Bluetooth state and permissions for listening to scales. Scales are only ever listened to,
 * never connected to, so the one permission needed is "Nearby devices" (Android 12+), declared
 * `neverForLocation`. Older versions tie Bluetooth scanning to location instead.
 */
@Singleton
class ScaleBluetooth @Inject constructor(@ApplicationContext private val context: Context) {

    val supported: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun adapter(): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Runtime permissions to request before scanning. */
    val scanPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Android 10 and 11 only deliver background scan results with "Allow all the time" location.
     * Null when no extra permission is needed.
     */
    val backgroundPermission: String? =
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R) Manifest.permission.ACCESS_BACKGROUND_LOCATION else null

    fun hasScanPermission(): Boolean = scanPermissions.all(::granted)

    fun canListenInBackground(): Boolean = hasScanPermission() && (backgroundPermission?.let(::granted) ?: true)

    fun readiness(): ScaleReadiness = when {
        !supported || adapter() == null -> ScaleReadiness.UNSUPPORTED
        !hasScanPermission() -> ScaleReadiness.NEEDS_PERMISSION
        adapter()?.isEnabled != true -> ScaleReadiness.BLUETOOTH_OFF
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !locationOn() -> ScaleReadiness.LOCATION_OFF
        else -> ScaleReadiness.READY
    }

    /** Bluetooth settings; asking to enable Bluetooth directly needs a permission we don't otherwise use. */
    fun bluetoothSettingsIntent(): Intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun locationSettingsIntent(): Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun locationOn(): Boolean =
        context.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: false

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
