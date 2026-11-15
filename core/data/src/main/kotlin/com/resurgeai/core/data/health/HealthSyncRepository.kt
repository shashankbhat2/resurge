package com.resurgeai.core.data.health

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.model.DailyHealth
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes one aggregate document per day to `users/{uid}/dailySummaries/{yyyy-MM-dd}`.
 * Only daily totals are uploaded — never raw samples. These summaries feed the AI coach.
 */
@Singleton
class HealthSyncRepository @Inject constructor(
    private val healthData: HealthDataSource,
    private val healthConnect: HealthConnectManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferences: PreferencesRepository,
) {
    private val mutex = Mutex()

    /**
     * Syncs recent days. The first sync for a user backfills [BACKFILL_DAYS]; later syncs only
     * refresh the last couple of days (late-arriving watch data usually lands within a day).
     */
    suspend fun syncRecent(zone: ZoneId = ZoneId.systemDefault()): Result<Int> = mutex.withLock {
        runCatching {
            val uid = auth.currentUser?.uid ?: return@runCatching 0
            if (healthConnect.grantedPermissions().isEmpty()) return@runCatching 0

            val backfilled = preferences.isHealthBackfillDone(uid)
            val days = if (backfilled) RECENT_DAYS else BACKFILL_DAYS
            val today = LocalDate.now(zone)
            val weekExerciseDays = HealthMath.exerciseDays(healthData.exerciseThisWeek(zone), zone)

            var written = 0
            for (offset in 0 until days) {
                val date = today.minusDays(offset.toLong())
                val day = healthData.readDay(date, zone)
                if (day.hasAnyData()) {
                    write(uid, day, exerciseDay = date in weekExerciseDays || day.exerciseDuration >= HealthMath.MIN_EXERCISE_FOR_DAY, zone)
                    written++
                }
            }
            if (!backfilled) preferences.setHealthBackfillDone(uid)
            written
        }.onFailure { Log.w(TAG, "Health sync failed", it) }
    }

    private suspend fun write(uid: String, day: DailyHealth, exerciseDay: Boolean, zone: ZoneId) {
        val data = buildMap<String, Any?> {
            put("date", day.date.toString())
            put("steps", day.steps)
            put("distanceM", day.distanceMeters?.let { Math.round(it) })
            put("kcalTotal", day.caloriesTotal?.let { Math.round(it) })
            put("kcalActive", day.caloriesActive?.let { Math.round(it) })
            put("sleepMin", day.sleepDuration?.toMinutes())
            // Time actually asleep, which is what the sleep goal checks.
            put("asleepMin", day.asleepDuration?.toMinutes())
            put("sleepStages", SummaryFields.sleepStages(day.sleep))
            put("exerciseMin", day.exerciseDuration.toMinutes())
            put("exerciseDay", exerciseDay)
            put("exerciseTypes", day.exercise.map { it.type }.distinct())
            // Each session's type and length, so the coach and MCP clients see more than the day's total.
            put("exerciseSessions", SummaryFields.exerciseSessions(day.exercise, zone))
            put("hrAvg", day.heartRate?.avgBpm)
            put("hrMin", day.heartRate?.minBpm)
            put("hrMax", day.heartRate?.maxBpm)
            put("restingHr", day.heartRate?.restingBpm)
            put("weightKg", day.weightKg)
            put(
                "sources",
                day.sources
                    .filterValues { it.primary != null }
                    .map { (metric, breakdown) -> metric.key to breakdown.primary!!.kind.name }
                    .toMap(),
            )
            put("updatedAt", FieldValue.serverTimestamp())
        }

        firestore.collection("users").document(uid)
            .collection("dailySummaries").document(day.date.toString())
            .set(data, SetOptions.merge())
            .await()
    }

    private fun DailyHealth.hasAnyData() =
        steps != null || distanceMeters != null || caloriesTotal != null || caloriesActive != null ||
            sleep.isNotEmpty() || exercise.isNotEmpty() || heartRate != null || weightKg != null

    private val HealthMetric.key get() = name.lowercase()

    companion object {
        const val BACKFILL_DAYS = 30
        const val RECENT_DAYS = 3
        private const val TAG = "HealthSync"
    }
}

/** Pure builders for the less obvious daily summary fields. Must match DailySummary in functions/src/lib/types.ts. */
internal object SummaryFields {
    /** Far more than any real day; keeps a runaway source from bloating the document. */
    const val MAX_EXERCISE_SESSIONS = 20

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Minutes per stage across the night, or null when the source didn't record deep/light/REM
     * (a plain "asleep" span would otherwise read as a night of all zeros).
     */
    fun sleepStages(sleep: List<SleepSession>): Map<String, Long>? {
        val minutes = sleep
            .flatMap { it.stages.entries }
            .groupBy({ it.key }, { it.value.toMinutes() })
            .mapValues { it.value.sum() }
        if (listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM).none { (minutes[it] ?: 0L) > 0 }) return null
        return listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE)
            .associate { it.name.lowercase() to (minutes[it] ?: 0L) }
    }

    /** One entry per session, in time order: type, optional title, local start time and length. */
    fun exerciseSessions(sessions: List<ExerciseSession>, zone: ZoneId): List<Map<String, Any?>> =
        sessions.sortedBy { it.start }.take(MAX_EXERCISE_SESSIONS).map {
            mapOf(
                "type" to it.type,
                "title" to it.title?.take(80),
                "start" to it.start.atZone(zone).format(CLOCK),
                "durationMin" to it.duration.toMinutes(),
            )
        }
}
