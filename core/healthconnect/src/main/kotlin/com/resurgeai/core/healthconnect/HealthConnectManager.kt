package com.resurgeai.core.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, NOT_SUPPORTED }

object HealthPermissions {
    /** Read permissions for every metric on the dashboard. Keep in sync with the manifest. */
    val READ: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    const val BACKGROUND = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    /** Optional: saving Resurge weigh-ins to Health Connect, asked for only when turned on. */
    val WRITE_WEIGHT: String = HealthPermission.getWritePermission(WeightRecord::class)
}

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                if (isProviderInstalled()) HealthConnectAvailability.UPDATE_REQUIRED
                else HealthConnectAvailability.NOT_INSTALLED
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (availability() == HealthConnectAvailability.AVAILABLE) {
            client.permissionController.getGrantedPermissions()
        } else {
            emptySet()
        }

    suspend fun hasAllReadPermissions(): Boolean = grantedPermissions().containsAll(HealthPermissions.READ)

    fun isBackgroundReadSupported(): Boolean =
        availability() == HealthConnectAvailability.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    /** Permissions to request: all dashboard reads, plus background read where supported. */
    fun permissionsToRequest(): Set<String> =
        if (isBackgroundReadSupported()) HealthPermissions.READ + HealthPermissions.BACKGROUND
        else HealthPermissions.READ

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** Play Store page for installing/updating Health Connect (Android 13 and below). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Opens Health Connect's own settings (data sources, app priority). */
    fun settingsIntent(): Intent =
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isProviderInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0)
    }.isSuccess

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
    }
}
