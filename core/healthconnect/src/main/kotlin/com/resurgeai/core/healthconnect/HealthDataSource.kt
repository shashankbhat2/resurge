package com.resurgeai.core.healthconnect

import android.content.Context
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.DailyValue
import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.HeartRateSummary
import com.resurgeai.core.model.MetricSeries
import com.resurgeai.core.model.SleepSegment
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SourceBreakdown
import com.resurgeai.core.model.WorkoutMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Reads dashboard data from Health Connect. Every read is gated on the matching permission so a
 * partially granted set still returns whatever is allowed instead of throwing.
 */
@Singleton
class HealthDataSource @Inject constructor(
    private val manager: HealthConnectManager,
    @ApplicationContext private val context: Context,
) {
    private val client get() = manager.client
    private val appLabels = mutableMapOf<String, String?>()

    suspend fun readDay(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyHealth = coroutineScope {
        val granted = manager.grantedPermissions()
        fun can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
        val (start, end) = HealthMath.dayBounds(date, zone)
        val range = TimeRangeFilter.between(start, end)

        val totals = async { aggregate(granted, range) }
        val stepsSources = async { if (can(StepsRecord::class)) sources(StepsRecord::class, range) { (it as StepsRecord).count.toDouble() } else null }
        val distanceSources = async { if (can(DistanceRecord::class)) sources(DistanceRecord::class, range) { (it as DistanceRecord).distance.inMeters } else null }
        val caloriesSources = async {
            if (can(TotalCaloriesBurnedRecord::class)) {
                sources(TotalCaloriesBurnedRecord::class, range) { (it as TotalCaloriesBurnedRecord).energy.inKilocalories }
            } else null
        }
        val heartSources = async { if (can(HeartRateRecord::class)) sources(HeartRateRecord::class, range) { (it as HeartRateRecord).samples.size.toDouble() } else null }
        val sleep = async { if (can(SleepSessionRecord::class)) sleepEndingIn(start, end) else emptyList() }
        val exercise = async { if (can(ExerciseSessionRecord::class)) exerciseSessions(start, end) else emptyList() }
        val resting = async { if (can(RestingHeartRateRecord::class)) latestRestingHr(start, end) else null }
        val weight = async { if (can(WeightRecord::class)) latestWeightKg(end) else null }

        val t = totals.await()
        val sleepSessions = sleep.await()
        val exerciseSessions = exercise.await()
        val heart = HeartRateSummary(
            minBpm = t[HeartRateRecord.BPM_MIN],
            avgBpm = t[HeartRateRecord.BPM_AVG],
            maxBpm = t[HeartRateRecord.BPM_MAX],
            restingBpm = resting.await(),
        ).takeIf { it.avgBpm != null || it.restingBpm != null }

        DailyHealth(
            date = date,
            steps = t[StepsRecord.COUNT_TOTAL],
            distanceMeters = t[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            caloriesTotal = t[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
            caloriesActive = t[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            sleep = sleepSessions,
            exercise = exerciseSessions,
            heartRate = heart,
            weightKg = weight.await(),
            sources = buildMap {
                stepsSources.await()?.let { put(HealthMetric.STEPS, it) }
                distanceSources.await()?.let { put(HealthMetric.DISTANCE, it) }
                caloriesSources.await()?.let { put(HealthMetric.CALORIES_BURNED, it) }
                heartSources.await()?.let { put(HealthMetric.HEART_RATE, it) }
                put(HealthMetric.SLEEP, sessionSources(sleepSessions.map { it.source to it.duration }))
                put(HealthMetric.EXERCISE, sessionSources(exerciseSessions.map { it.source to it.duration }))
            },
        )
    }

    /** One value per day for the last [days] days (ending today), for charts and sparklines. */
    suspend fun readSeries(
        metric: HealthMetric,
        days: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): MetricSeries {
        val today = LocalDate.now(zone)
        val first = today.minusDays(days - 1L)
        val dates = (0 until days).map { first.plusDays(it.toLong()) }
        val granted = manager.grantedPermissions()

        if (metric == HealthMetric.SLEEP) {
            // Attribute each night to the day it ended on, matching the Today card.
            if (HealthPermission.getReadPermission(SleepSessionRecord::class) !in granted) return MetricSeries(metric, dates.map { DailyValue(it, null) })
            val (start, _) = HealthMath.dayBounds(first, zone)
            val (_, end) = HealthMath.dayBounds(today, zone)
            val byDay = sleepEndingIn(start, end)
                .groupBy { it.end.atZone(zone).toLocalDate() }
                .mapValues { (_, s) -> s.sumOf { it.duration.toMinutes() } / 60.0 }
            return MetricSeries(metric, dates.map { DailyValue(it, byDay[it]) })
        }

        val (recordType, aggregateMetric) = seriesSpec(metric)
        if (HealthPermission.getReadPermission(recordType) !in granted) {
            return MetricSeries(metric, dates.map { DailyValue(it, null) })
        }
        val response = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(aggregateMetric),
                timeRangeFilter = TimeRangeFilter.between(
                    first.atStartOfDay(),
                    today.plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        val byDay = response.associate { bucket ->
            bucket.startTime.toLocalDate() to bucket.result[aggregateMetric]?.let { toSeriesValue(metric, it) }
        }
        return MetricSeries(metric, dates.map { DailyValue(it, byDay[it]) })
    }

    /** Exercise sessions from the start of this week (Sunday) up to now. */
    suspend fun exerciseThisWeek(zone: ZoneId = ZoneId.systemDefault()): List<ExerciseSession> {
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in manager.grantedPermissions()) return emptyList()
        val weekStart = HealthMath.weekStart(LocalDate.now(zone))
        return exerciseSessions(weekStart.atStartOfDay(zone).toInstant(), Instant.now())
    }

    suspend fun sleepSessions(from: Instant, to: Instant): List<SleepSession> =
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in manager.grantedPermissions()) sleepEndingIn(from, to)
        else emptyList()

    suspend fun exerciseSessionsBetween(from: Instant, to: Instant): List<ExerciseSession> =
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in manager.grantedPermissions()) exerciseSessions(from, to)
        else emptyList()

    /** Most recent weight in the last 30 days, for calorie estimates. */
    suspend fun latestWeight(): Double? =
        if (HealthPermission.getReadPermission(WeightRecord::class) in manager.grantedPermissions()) latestWeightKg(Instant.now())
        else null

    /**
     * Weigh-ins from other apps and scales since [since], oldest first, for the one-time import.
     * Leaves out what Resurge wrote itself. Empty without the read permission.
     */
    suspend fun weightHistory(since: Instant): List<HealthWeight> {
        if (HealthPermission.getReadPermission(WeightRecord::class) !in manager.grantedPermissions()) return emptyList()
        return readAll(WeightRecord::class, TimeRangeFilter.after(since), maxPages = 20)
            .filter { it.metadata.dataOrigin.packageName != context.packageName }
            .map { HealthWeight(it.metadata.id, it.weight.inKilograms, it.time) }
            .sortedBy { it.at }
    }

    /** Saves a manual weigh-in to Health Connect; returns its record id, or null without permission. */
    suspend fun writeWeight(kg: Double, at: Instant, scale: ScaleOrigin? = null): String? {
        if (HealthPermissions.WRITE_WEIGHT !in manager.grantedPermissions()) return null
        val metadata = if (scale == null) {
            Metadata.manualEntry()
        } else {
            // Marked as measured by a scale, and keyed by our id so a retry replaces rather than duplicates.
            Metadata.autoRecorded(
                device = Device(type = Device.TYPE_SCALE, manufacturer = scale.manufacturer, model = scale.model),
                clientRecordId = scale.clientRecordId,
                clientRecordVersion = 1,
            )
        }
        val record = WeightRecord(
            time = at,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(at),
            weight = Mass.kilograms(kg),
            metadata = metadata,
        )
        return client.insertRecords(listOf(record)).recordIdsList.firstOrNull()
    }

    /** Removes a weigh-in Resurge wrote earlier. */
    suspend fun deleteWeight(recordId: String) {
        if (HealthPermissions.WRITE_WEIGHT !in manager.grantedPermissions()) return
        client.deleteRecords(WeightRecord::class, recordIdsList = listOf(recordId), clientRecordIdsList = emptyList())
    }

    /** Daily heart-rate min/max (bpm) for the last [days] days, keyed by local date. */
    suspend fun readHeartRateRanges(days: Int, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Pair<Long, Long>> {
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in manager.grantedPermissions()) return emptyMap()
        val today = LocalDate.now(zone)
        val first = today.minusDays(days - 1L)
        return client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
                timeRangeFilter = TimeRangeFilter.between(first.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).mapNotNull { bucket ->
            val min = bucket.result[HeartRateRecord.BPM_MIN] ?: return@mapNotNull null
            val max = bucket.result[HeartRateRecord.BPM_MAX] ?: return@mapNotNull null
            bucket.startTime.toLocalDate() to (min to max)
        }.toMap()
    }

    /**
     * Totals within one session's time window (distance, calories, heart rate, steps). Health Connect
     * de-duplicates phone + watch data inside the window. Used for post-activity insights.
     */
    suspend fun metricsBetween(start: Instant, end: Instant): WorkoutMetrics {
        val granted = manager.grantedPermissions()
        fun can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
        val metrics = buildSet {
            if (can(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (can(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (can(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (can(HeartRateRecord::class)) { add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MAX); add(HeartRateRecord.BPM_MIN) }
            if (can(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
        }
        if (metrics.isEmpty()) return WorkoutMetrics(null, null, null, null, null, null, null)
        val r = client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = TimeRangeFilter.between(start, end)))
        return WorkoutMetrics(
            distanceMeters = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            activeKcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            totalKcal = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
            avgBpm = r[HeartRateRecord.BPM_AVG],
            maxBpm = r[HeartRateRecord.BPM_MAX],
            minBpm = r[HeartRateRecord.BPM_MIN],
            steps = r[StepsRecord.COUNT_TOTAL],
        )
    }

    /** Which sources contributed to [metric] between [start] and [end]. */
    suspend fun readSources(metric: HealthMetric, start: Instant, end: Instant): SourceBreakdown {
        val granted = manager.grantedPermissions()
        val range = TimeRangeFilter.between(start, end)
        fun can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted
        // Longer ranges have many more step/HR records; allow more pages than a single day needs.
        val pages = 20
        return when (metric) {
            HealthMetric.STEPS -> if (can(StepsRecord::class)) sources(StepsRecord::class, range, pages) { (it as StepsRecord).count.toDouble() } else SourceBreakdown.Empty
            HealthMetric.DISTANCE -> if (can(DistanceRecord::class)) sources(DistanceRecord::class, range, pages) { (it as DistanceRecord).distance.inMeters } else SourceBreakdown.Empty
            HealthMetric.CALORIES_BURNED -> if (can(TotalCaloriesBurnedRecord::class)) sources(TotalCaloriesBurnedRecord::class, range, pages) { (it as TotalCaloriesBurnedRecord).energy.inKilocalories } else SourceBreakdown.Empty
            HealthMetric.HEART_RATE -> if (can(HeartRateRecord::class)) sources(HeartRateRecord::class, range, pages) { (it as HeartRateRecord).samples.size.toDouble() } else SourceBreakdown.Empty
            HealthMetric.WEIGHT -> if (can(WeightRecord::class)) sources(WeightRecord::class, range, pages) { 1.0 } else SourceBreakdown.Empty
            HealthMetric.SLEEP -> if (can(SleepSessionRecord::class)) sessionSources(sleepEndingIn(start, end).map { it.source to it.duration }) else SourceBreakdown.Empty
            HealthMetric.EXERCISE -> if (can(ExerciseSessionRecord::class)) sessionSources(exerciseSessions(start, end).map { it.source to it.duration }) else SourceBreakdown.Empty
        }
    }

    // --- internals ---

    private suspend fun aggregate(granted: Set<String>, range: TimeRangeFilter) =
        AggregateValues(
            client.aggregate(
                AggregateRequest(
                    metrics = buildSet {
                        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) add(StepsRecord.COUNT_TOTAL)
                        if (HealthPermission.getReadPermission(DistanceRecord::class) in granted) add(DistanceRecord.DISTANCE_TOTAL)
                        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
                        if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
                            add(HeartRateRecord.BPM_MIN); add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MAX)
                        }
                    }.ifEmpty { return AggregateValues(null) },
                    timeRangeFilter = range,
                ),
            ),
        )

    private class AggregateValues(private val result: androidx.health.connect.client.aggregate.AggregationResult?) {
        operator fun <T : Any> get(metric: AggregateMetric<T>): T? = result?.get(metric)
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter, maxPages: Int = 5): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageToken = token),
            )
            out += response.records
            token = response.pageToken
            pages++
        } while (token != null && pages < maxPages)
        return out
    }

    private suspend fun <T : Record> sources(
        type: KClass<T>,
        range: TimeRangeFilter,
        maxPages: Int = 5,
        weight: (Record) -> Double,
    ): SourceBreakdown = SourceResolver.resolve(
        readAll(type, range, maxPages).map { record -> contribution(record.metadata, weight(record)) },
        appName = ::appName,
    )

    private fun sessionSources(sessions: List<Pair<DataSource?, Duration>>): SourceBreakdown =
        SourceResolver.resolve(
            sessions.mapNotNull { (source, duration) ->
                source?.let { Contribution(it.kind, it.packageName, it.deviceName, duration.seconds.toDouble()) }
            },
            appName = ::appName,
        )

    private fun contribution(metadata: Metadata, weight: Double): Contribution {
        val pkg = metadata.dataOrigin.packageName
        return Contribution(
            kind = SourceResolver.classify(metadata.device?.type, metadata.recordingMethod, pkg),
            packageName = pkg,
            deviceName = SourceResolver.deviceName(metadata.device?.manufacturer, metadata.device?.model),
            weight = weight,
        )
    }

    private fun dataSource(metadata: Metadata): DataSource {
        val c = contribution(metadata, 1.0)
        return DataSource(c.kind, c.packageName, appName(c.packageName), c.deviceName)
    }

    private suspend fun sleepEndingIn(start: Instant, end: Instant): List<SleepSession> =
        // Sessions can start the previous evening, so widen the read window backwards.
        readAll(SleepSessionRecord::class, TimeRangeFilter.between(start.minus(Duration.ofHours(18)), end))
            .filter { it.endTime >= start && it.endTime < end.plusSeconds(1) }
            .map { record ->
                SleepSession(
                    start = record.startTime,
                    end = record.endTime,
                    stages = HealthMath.stageTotals(record.stages.map { Triple(it.startTime, it.endTime, it.stage) }),
                    source = dataSource(record.metadata),
                    timeline = record.stages
                        .sortedBy { it.startTime }
                        .map { SleepSegment(it.startTime, it.endTime, HealthMath.mapStage(it.stage)) },
                    id = record.metadata.id,
                )
            }

    private suspend fun exerciseSessions(start: Instant, end: Instant): List<ExerciseSession> =
        readAll(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end)).map { record ->
            ExerciseSession(
                id = record.metadata.id,
                type = ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[record.exerciseType]
                    ?.replace('_', ' ')
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "Workout",
                title = record.title,
                start = record.startTime,
                end = record.endTime,
                source = dataSource(record.metadata),
                typeKey = ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[record.exerciseType].orEmpty(),
            )
        }

    private suspend fun latestRestingHr(start: Instant, end: Instant): Long? =
        client.readRecords(
            ReadRecordsRequest(
                recordType = RestingHeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()?.beatsPerMinute

    private suspend fun latestWeightKg(end: Instant): Double? =
        client.readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(end.minus(Duration.ofDays(30)), end),
                ascendingOrder = false,
                pageSize = 1,
            ),
        ).records.firstOrNull()?.weight?.inKilograms

    private fun seriesSpec(metric: HealthMetric): Pair<KClass<out Record>, AggregateMetric<*>> = when (metric) {
        HealthMetric.STEPS -> StepsRecord::class to StepsRecord.COUNT_TOTAL
        HealthMetric.DISTANCE -> DistanceRecord::class to DistanceRecord.DISTANCE_TOTAL
        HealthMetric.CALORIES_BURNED -> TotalCaloriesBurnedRecord::class to TotalCaloriesBurnedRecord.ENERGY_TOTAL
        HealthMetric.EXERCISE -> ExerciseSessionRecord::class to ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
        HealthMetric.HEART_RATE -> HeartRateRecord::class to HeartRateRecord.BPM_AVG
        HealthMetric.WEIGHT -> WeightRecord::class to WeightRecord.WEIGHT_AVG
        HealthMetric.SLEEP -> SleepSessionRecord::class to SleepSessionRecord.SLEEP_DURATION_TOTAL
    }

    /** Series units: steps, km, kcal, minutes, bpm, kg, hours. */
    private fun toSeriesValue(metric: HealthMetric, value: Any): Double? = when (value) {
        is Long -> value.toDouble()
        is androidx.health.connect.client.units.Length -> value.inKilometers
        is androidx.health.connect.client.units.Energy -> value.inKilocalories
        is androidx.health.connect.client.units.Mass -> value.inKilograms
        is Duration -> if (metric == HealthMetric.SLEEP) value.toMinutes() / 60.0 else value.toMinutes().toDouble()
        else -> null
    }

    private fun appName(packageName: String): String? = synchronized(appLabels) {
        appLabels.getOrPut(packageName) {
            SourceResolver.knownAppName(packageName) ?: runCatching {
                val pm = context.packageManager
                @Suppress("DEPRECATION") // ApplicationInfoFlags needs API 33
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrNull()
        }
    }
}

/** A weigh-in read from Health Connect. */
/** Which smart scale measured a weight being written, for Health Connect's device metadata. */
data class ScaleOrigin(val manufacturer: String?, val model: String, val clientRecordId: String)

data class HealthWeight(val recordId: String, val kg: Double, val at: Instant)
