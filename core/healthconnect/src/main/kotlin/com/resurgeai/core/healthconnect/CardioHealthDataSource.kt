package com.resurgeai.core.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.resurgeai.core.model.CardioEvidenceInterval
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.ExerciseSession
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class CardioHealthDataSource @Inject constructor(
    private val manager: HealthConnectManager,
    private val appLabels: HealthAppLabels,
) {
    suspend fun evidence(from: Instant, to: Instant): List<CardioEvidenceInterval> {
        if (from >= to) return emptyList()
        val sampleStart = from.minusSeconds(60)
        val range = TimeRangeFilter.between(sampleStart, to)
        // Raw record filters use record start times, not sample times. Keep the lookback bounded
        // to retained history so a recent-day refresh still includes overnight cadence series.
        val zone = ZoneId.systemDefault()
        val historyStart = to.atZone(zone).toLocalDate()
            .minusDays(CardioPointsEngine.HISTORY_DAYS - 1).atStartOfDay(zone).toInstant()
        val cadenceRange = TimeRangeFilter.between(minOf(sampleStart, historyStart), to)
        val cadence = all(StepsCadenceRecord::class, cadenceRange)
            .filter { !manual(it.metadata) && it.startTime < to && it.endTime > from }
            .flatMap { record ->
                CardioEvidenceMath.cadence(
                    start = maxOf(record.startTime, sampleStart),
                    end = minOf(record.endTime, to),
                    samples = record.samples.map { it.time to it.rate },
                    source = source(record.metadata),
                    id = record.metadata.id,
                )
            }
        val steps = all(StepsRecord::class, range).filter { !manual(it.metadata) }.mapNotNull { record ->
            CardioEvidenceMath.steps(record.startTime, record.endTime, record.count, source(record.metadata), record.metadata.id)
        }
        return cadence + steps
    }

    /** Match workout history: include sessions whose start falls inside the requested period. */
    suspend fun workouts(from: Instant, to: Instant): List<ExerciseSession> {
        if (from >= to || HealthPermission.getReadPermission(ExerciseSessionRecord::class) !in manager.grantedPermissions()) return emptyList()
        return all(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))
            .filter { it.startTime >= from && it.startTime < to }
            .map { record ->
                val key = ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[record.exerciseType].orEmpty()
                ExerciseSession(record.metadata.id, key.replace('_', ' ').replaceFirstChar { it.uppercase() }, record.title,
                    record.startTime, record.endTime, source(record.metadata), key)
            }
    }

    private suspend fun <T : Record> all(type: KClass<T>, range: TimeRangeFilter): List<T> = readCardioPages { token ->
        val response = manager.client.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageToken = token))
        response.records to response.pageToken
    }

    private fun manual(metadata: Metadata) = metadata.recordingMethod == Metadata.RECORDING_METHOD_MANUAL_ENTRY
    private fun source(metadata: Metadata): DataSource {
        val pkg = metadata.dataOrigin.packageName
        val device = metadata.device
        return DataSource(SourceResolver.classify(device?.type, metadata.recordingMethod, pkg), pkg,
            appLabels.label(pkg), SourceResolver.deviceName(device?.manufacturer, device?.model))
    }
}
