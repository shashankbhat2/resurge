package com.resurgeai.core.healthconnect

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.SourceBreakdown
import com.resurgeai.core.model.SourceKind
import com.resurgeai.core.model.SourceShare

/** One record's contribution to a metric, before grouping. [weight] is steps, metres, kcal, seconds… */
data class Contribution(
    val kind: SourceKind,
    val packageName: String,
    val deviceName: String?,
    val weight: Double,
)

/**
 * Pure logic for turning Health Connect record metadata into "phone / wearable / manual" sources.
 * Kept free of Android framework calls so it can be unit tested.
 */
object SourceResolver {

    /** Companion apps whose records come from a wearable even when they omit device info. */
    private val wearablePackages = setOf(
        "com.fitbit.FitbitMobile",
        "com.garmin.android.apps.connectmobile",
        "com.ouraring.oura",
        "com.whoop.android",
        "com.huami.watch.hmwatchmanager",
        "com.xiaomi.wearable",
        "com.mi.health",
        "com.polar.polarflow",
        "com.suunto.china",
        "com.coros.coros",
    )

    private val phonePackages = setOf("android", "com.google.android.apps.healthdata")

    private val knownAppNames = mapOf(
        "android" to "This phone",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.garmin.android.apps.connectmobile" to "Garmin Connect",
        "com.ouraring.oura" to "Oura",
        "com.whoop.android" to "WHOOP",
        "com.withings.wiscale2" to "Withings",
        "com.huami.watch.hmwatchmanager" to "Zepp",
        "com.xiaomi.wearable" to "Mi Fitness",
        "com.mi.health" to "Mi Fitness",
        "com.strava" to "Strava",
        "com.polar.polarflow" to "Polar Flow",
        "com.resurgeai.app" to "Resurge",
    )

    fun knownAppName(packageName: String): String? = knownAppNames[packageName]

    /** Classifies a record from its device type, recording method and origin package. */
    fun classify(deviceType: Int?, recordingMethod: Int, packageName: String): SourceKind = when {
        recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY -> SourceKind.MANUAL
        deviceType == Device.TYPE_WATCH ||
            deviceType == Device.TYPE_RING ||
            deviceType == Device.TYPE_FITNESS_BAND ||
            deviceType == Device.TYPE_CHEST_STRAP ||
            deviceType == Device.TYPE_HEAD_MOUNTED -> SourceKind.WEARABLE
        deviceType == Device.TYPE_PHONE -> SourceKind.PHONE
        deviceType == Device.TYPE_SCALE || deviceType == Device.TYPE_SMART_DISPLAY -> SourceKind.OTHER_DEVICE
        packageName in wearablePackages -> SourceKind.WEARABLE
        packageName in phonePackages -> SourceKind.PHONE
        else -> SourceKind.UNKNOWN
    }

    fun deviceName(manufacturer: String?, model: String?): String? {
        val parts = listOfNotNull(manufacturer?.trim(), model?.trim()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        // Avoid "Samsung Samsung Galaxy Watch6"
        return if (parts.size == 2 && parts[1].startsWith(parts[0], ignoreCase = true)) parts[1]
        else parts.joinToString(" ")
    }

    /**
     * Groups contributions by source and returns each source's share, largest first.
     * Note: shares are computed on raw records, before Health Connect's de-duplication, so they
     * describe *who recorded* the data rather than exactly which records were counted in the total.
     */
    fun resolve(
        contributions: List<Contribution>,
        appName: (String) -> String? = ::knownAppName,
    ): SourceBreakdown {
        val positive = contributions.filter { it.weight > 0 }
        val total = positive.sumOf { it.weight }
        if (total <= 0) return SourceBreakdown.Empty

        val shares = positive
            .groupBy { Triple(it.kind, it.packageName, it.deviceName) }
            .map { (key, group) ->
                val (kind, pkg, device) = key
                SourceShare(
                    source = DataSource(kind = kind, packageName = pkg, appName = appName(pkg), deviceName = device),
                    fraction = (group.sumOf { it.weight } / total).toFloat(),
                )
            }
            .sortedByDescending { it.fraction }
        return SourceBreakdown(shares)
    }
}
