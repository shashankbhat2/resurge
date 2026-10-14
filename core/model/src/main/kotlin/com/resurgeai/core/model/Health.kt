package com.resurgeai.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Where a piece of health data came from. */
enum class SourceKind { WEARABLE, PHONE, OTHER_DEVICE, MANUAL, UNKNOWN }

data class DataSource(
    val kind: SourceKind,
    val packageName: String,
    val appName: String?,
    /** e.g. "Galaxy Watch6" when the record carries device info. */
    val deviceName: String?,
)

data class SourceShare(val source: DataSource, val fraction: Float)

/** Contribution of each source to a metric, largest first. */
data class SourceBreakdown(val shares: List<SourceShare>) {
    val primary: DataSource? get() = shares.firstOrNull()?.source
    val kinds: Set<SourceKind> get() = shares.map { it.source.kind }.toSet()

    companion object {
        val Empty = SourceBreakdown(emptyList())
    }
}

enum class HealthMetric(val label: String) {
    STEPS("Steps"),
    DISTANCE("Distance"),
    CALORIES_BURNED("Calories burned"),
    SLEEP("Sleep"),
    EXERCISE("Exercise"),
    HEART_RATE("Heart rate"),
    WEIGHT("Weight"),
}

enum class SleepStage { AWAKE, LIGHT, DEEP, REM, SLEEPING, UNKNOWN }

/** One stage interval within a night, in time order. */
data class SleepSegment(val start: Instant, val end: Instant, val stage: SleepStage)

data class SleepSession(
    val start: Instant,
    val end: Instant,
    val stages: Map<SleepStage, Duration>,
    val source: DataSource?,
    /** Stage-by-stage timeline; empty when the source only records total duration. */
    val timeline: List<SleepSegment> = emptyList(),
    /** Health Connect record ID (stable across reads). */
    val id: String = "",
) {
    val duration: Duration get() = Duration.between(start, end)

    /** Time actually asleep (excludes awake stages when they're known). */
    val asleep: Duration get() = duration - (stages[SleepStage.AWAKE] ?: Duration.ZERO)

    /** Sleep efficiency (asleep / in bed), or null without stage data. */
    val quality: Float?
        get() = if (stages.isEmpty() || duration.isZero) null
        else (asleep.toMinutes().toFloat() / duration.toMinutes()).coerceIn(0f, 1f)
}

data class ExerciseSession(
    val id: String,
    val type: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val source: DataSource?,
    /** Health Connect exercise-type key, e.g. "running" (see ActivityCatalog.forHealthConnect). */
    val typeKey: String = "",
) {
    val duration: Duration get() = Duration.between(start, end)
}

data class HeartRateSummary(val minBpm: Long?, val avgBpm: Long?, val maxBpm: Long?, val restingBpm: Long?)

/** Everything the Today screen shows for a single day. Null = no data for that metric. */
data class DailyHealth(
    val date: LocalDate,
    val steps: Long?,
    val distanceMeters: Double?,
    val caloriesTotal: Double?,
    val caloriesActive: Double?,
    /** Sleep sessions that ended on [date] (i.e. "last night"). */
    val sleep: List<SleepSession>,
    val exercise: List<ExerciseSession>,
    val heartRate: HeartRateSummary?,
    val weightKg: Double?,
    val sources: Map<HealthMetric, SourceBreakdown>,
) {
    /** Time in bed across the night's sessions (includes awake time). */
    val sleepDuration: Duration? get() = sleep.takeIf { it.isNotEmpty() }?.fold(Duration.ZERO) { acc, s -> acc + s.duration }

    /** Time actually asleep (awake stages removed), the figure shown on Today and its sleep goal. */
    val asleepDuration: Duration? get() = sleep.takeIf { it.isNotEmpty() }?.fold(Duration.ZERO) { acc, s -> acc + s.asleep }
    val exerciseDuration: Duration get() = exercise.fold(Duration.ZERO) { acc, s -> acc + s.duration }
}

/** Totals over one workout's time window (each null when not recorded or not permitted). */
data class WorkoutMetrics(
    val distanceMeters: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val avgBpm: Long?,
    val maxBpm: Long?,
    val minBpm: Long?,
    val steps: Long?,
)

/** One value per day, used for charts and sparklines. */
data class DailyValue(val date: LocalDate, val value: Double?)

data class MetricSeries(val metric: HealthMetric, val points: List<DailyValue>) {
    val nonNull: List<Double> get() = points.mapNotNull { it.value }
    val average: Double? get() = nonNull.takeIf { it.isNotEmpty() }?.average()
}
