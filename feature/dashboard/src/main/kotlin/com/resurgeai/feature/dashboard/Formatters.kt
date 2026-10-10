package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SourceBreakdown
import com.resurgeai.core.model.SourceKind
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToLong

/** Display formatting for dashboard values. Pure functions so they can be unit tested. */
object Formatters {
    fun count(value: Long): String = String.format(Locale.getDefault(), "%,d", value)

    fun kilometres(meters: Double): String = String.format(Locale.getDefault(), if (meters < 10_000) "%.2f" else "%.1f", meters / 1000)

    fun kcal(value: Double): String = count(value.roundToLong())

    /** "7h 12m", "45m", "0m". */
    fun duration(d: Duration): String {
        val h = d.toHours()
        val m = d.toMinutes() % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun hours(hours: Double): String = duration(Duration.ofMinutes((hours * 60).roundToLong()))

    /** Formats a chart/series value in the metric's series unit (see HealthDataSource.readSeries). */
    fun seriesValue(metric: HealthMetric, value: Double): String = when (metric) {
        HealthMetric.STEPS -> count(value.roundToLong())
        HealthMetric.DISTANCE -> String.format(Locale.getDefault(), "%.1f km", value)
        HealthMetric.CALORIES_BURNED -> "${kcal(value)} kcal"
        HealthMetric.SLEEP -> hours(value)
        HealthMetric.EXERCISE -> duration(Duration.ofMinutes(value.roundToLong()))
        HealthMetric.HEART_RATE -> "${value.roundToLong()} bpm"
        HealthMetric.WEIGHT -> String.format(Locale.getDefault(), "%.1f kg", value)
    }

    /** Short label for a single source: device name, else app name, else its kind. */
    fun sourceLabel(source: DataSource): String = when (source.kind) {
        SourceKind.MANUAL -> "Manual · ${source.appName ?: "entry"}"
        else -> source.deviceName ?: source.appName ?: kindLabel(source.kind)
    }

    fun kindLabel(kind: SourceKind): String = when (kind) {
        SourceKind.WEARABLE -> "Wearable"
        SourceKind.PHONE -> "Phone"
        SourceKind.OTHER_DEVICE -> "Device"
        SourceKind.MANUAL -> "Manual"
        SourceKind.UNKNOWN -> "App"
    }

    /**
     * One-line summary for a card's source chip. When both phone and a wearable contribute
     * meaningfully (≥ [MIXED_THRESHOLD] each), says "Phone + Wearable" instead of one name.
     */
    fun sourceSummary(breakdown: SourceBreakdown): SourceSummary? {
        val primary = breakdown.primary ?: return null
        val byKind = breakdown.shares.groupBy { it.source.kind }.mapValues { (_, s) -> s.sumOf { it.fraction.toDouble() } }
        val phone = byKind[SourceKind.PHONE] ?: 0.0
        val wearable = byKind[SourceKind.WEARABLE] ?: 0.0
        return if (phone >= MIXED_THRESHOLD && wearable >= MIXED_THRESHOLD) {
            SourceSummary("Phone + Wearable", SourceKind.WEARABLE)
        } else {
            SourceSummary(sourceLabel(primary), primary.kind)
        }
    }

    private const val MIXED_THRESHOLD = 0.15

    data class SourceSummary(val label: String, val kind: SourceKind)
}
