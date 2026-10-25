package com.resurgeai.core.data.fitness

import android.content.Context
import kotlinx.coroutines.flow.first
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.resurgeai.core.data.listen
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.model.ActivityCatalog
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutDraft
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** A saved workout document: a manual log, or the user's edits to (or removal of) a Health Connect session. */
data class WorkoutRecord(
    val id: String,
    val source: WorkoutSource,
    val activityId: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val metrics: WorkoutMetricsInput,
    val notes: String?,
    val deleted: Boolean,
)

/**
 * Fitness feed data:
 *   users/{uid}/workouts/{id}  manual workouts (generated id) and overrides of Health Connect sessions
 *                             (id = Health Connect record id; `deleted` hides the session)
 * Health Connect sessions are read live on the device and never copied wholesale to Firestore.
 */
@Singleton
class WorkoutsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val healthData: HealthDataSource,
    private val weights: WeightRepository,
    @param:ApplicationContext private val context: Context,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    /** Distance, calories and heart rate per session; sessions don't change once finished. */
    private val metricsCache = ConcurrentHashMap<String, WorkoutMetricsInput>()

    /**
     * The same totals kept across launches, so a cold start doesn't aggregate every session again
     * (each one is a slow Health Connect call). Only sessions a day old are kept, by when late watch
     * syncs have landed.
     */
    private val savedMetrics by lazy { context.getSharedPreferences(METRICS_PREFS, Context.MODE_PRIVATE) }

    private fun savedMetrics(id: String): WorkoutMetricsInput? =
        savedMetrics.getString(id, null)?.split(',')?.takeIf { it.size == 3 }?.let { (distance, hr, kcal) ->
            WorkoutMetricsInput(distanceM = distance.toDoubleOrNull(), avgHr = hr.toIntOrNull(), kcal = kcal.toIntOrNull())
        }

    private fun saveMetrics(id: String, m: WorkoutMetricsInput) {
        savedMetrics.edit().putString(id, listOf(m.distanceM, m.avgHr, m.kcal).joinToString(",") { it?.toString().orEmpty() }).apply()
    }

    fun records(since: Instant): Flow<List<WorkoutRecord>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("workouts").whereGreaterThanOrEqualTo("startMs", since.toEpochMilli())) { docs ->
            docs.mapNotNull(::toRecord)
        }
    }

    /** Health Connect sessions in the range, each with its window totals. */
    suspend fun deviceWorkouts(from: Instant, to: Instant): List<Workout> = coroutineScope {
        healthData.exerciseSessionsBetween(from, to).map { s -> async { s.toWorkout() } }.awaitAll()
    }

    /** One workout for the editor: a saved record, a device session, or the session with edits applied. */
    suspend fun find(id: String, lookback: Duration = Duration.ofDays(60)): Workout? {
        val record = runCatching { user()?.collection("workouts")?.document(id)?.get()?.await()?.let(::toRecord) }.getOrNull()
        if (record != null && record.source == WorkoutSource.MANUAL) return merge(emptyList(), listOf(record)).firstOrNull()
        val now = Instant.now()
        val from = record?.start?.minus(Duration.ofDays(1)) ?: now.minus(lookback)
        // Read the neighbours too, so a merged session comes back with its duplicates.
        val device = deviceWorkouts(from, now)
        val records = runCatching { records(from).first() }.getOrDefault(listOfNotNull(record))
        val merged = merge(device, records)
        return merged.firstOrNull { it.id == id } ?: merged.flatMap { it.duplicates }.firstOrNull { it.id == id }
    }

    suspend fun latestWeightKg(): Double? = weights.latestKg()

    private suspend fun ExerciseSession.toWorkout(): Workout {
        val now = Instant.now()
        val metrics = metricsCache[id] ?: savedMetrics(id)?.also { metricsCache[id] = it }
            ?: runCatching { healthData.metricsBetween(start, end) }.getOrNull()?.let { m ->
                WorkoutMetricsInput(
                    distanceM = m.distanceMeters?.takeIf { it >= 50 },
                    avgHr = m.avgBpm?.toInt(),
                    kcal = (m.activeKcal ?: m.totalKcal)?.roundToInt(),
                ).also {
                    if (end.isBefore(now)) metricsCache[id] = it
                    if (end.isBefore(now.minus(Duration.ofDays(1)))) saveMetrics(id, it)
                }
            } ?: WorkoutMetricsInput()
        return Workout(
            id = id,
            source = WorkoutSource.HEALTH_CONNECT,
            activityId = ActivityCatalog.forHealthConnect(typeKey).id,
            title = title,
            start = start,
            end = end,
            metrics = metrics,
            sourceName = source?.deviceName ?: source?.appName,
        )
    }

    /** Saves a manual workout (id null = new) or the user's edits to a Health Connect session. */
    fun save(draft: WorkoutDraft, id: String?, source: WorkoutSource, zone: ZoneId = ZoneId.systemDefault()) {
        val col = user()?.collection("workouts") ?: return
        val doc = if (id == null) col.document() else col.document(id)
        val end = draft.start.plusSeconds(draft.durationMin * 60L)
        doc.set(
            mapOf(
                "source" to if (source == WorkoutSource.HEALTH_CONNECT) HC else MANUAL,
                "activityId" to draft.activityId,
                "title" to draft.title?.trim()?.takeIf { it.isNotEmpty() },
                "startMs" to draft.start.toEpochMilli(),
                "endMs" to end.toEpochMilli(),
                "localDate" to draft.start.atZone(zone).toLocalDate().toString(),
                "metrics" to draft.metrics.toMap(),
                "notes" to draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                "deleted" to false,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    /**
     * Manual workouts are deleted; Health Connect sessions are hidden with a tombstone (they belong
     * to the app that recorded them). Hiding a merged session hides its duplicates too, so the
     * other recording doesn't reappear on its own.
     */
    fun delete(workout: Workout) {
        workout.duplicates.forEach(::delete)
        val doc = user()?.collection("workouts")?.document(workout.id) ?: return
        if (workout.source == WorkoutSource.MANUAL) {
            doc.delete()
        } else {
            doc.set(tombstone(workout))
        }
    }

    /** Hides only this recording, so its duplicate shows instead ("Use this one instead"). */
    fun hideOnly(workout: Workout) {
        user()?.collection("workouts")?.document(workout.id)?.set(tombstone(workout))
    }

    private fun tombstone(workout: Workout) = mapOf(
        "source" to HC,
        "activityId" to workout.activityId,
        // The security rules read title and notes on every write; a missing key fails the rule,
        // so the tombstone must carry them even when they're null.
        "title" to workout.title,
        "startMs" to workout.start.toEpochMilli(),
        "endMs" to workout.end.toEpochMilli(),
        "localDate" to workout.date().toString(),
        "metrics" to workout.metrics.toMap(),
        "notes" to workout.notes,
        "deleted" to true,
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    /** Brings back a deleted workout: re-saves a manual one, or drops the tombstone of a device one. */
    fun restore(workout: Workout) {
        workout.duplicates.forEach(::restore)
        if (workout.source == WorkoutSource.HEALTH_CONNECT && !workout.edited) {
            user()?.collection("workouts")?.document(workout.id)?.delete()
        } else {
            save(
                WorkoutDraft(workout.activityId, workout.start, workout.duration.toMinutes().toInt(), workout.metrics, workout.title, workout.notes),
                workout.id,
                workout.source,
            )
        }
    }

    /** Drops the user's edits to a Health Connect session. */
    fun resetToDevice(id: String) {
        user()?.collection("workouts")?.document(id)?.delete()
    }

    private fun toRecord(d: DocumentSnapshot): WorkoutRecord? {
        val start = d.getLong("startMs")?.let(Instant::ofEpochMilli) ?: return null
        val end = d.getLong("endMs")?.let(Instant::ofEpochMilli) ?: return null
        @Suppress("UNCHECKED_CAST")
        val m = d.get("metrics") as? Map<String, Any?> ?: emptyMap()
        return WorkoutRecord(
            id = d.id,
            source = if (d.getString("source") == HC) WorkoutSource.HEALTH_CONNECT else WorkoutSource.MANUAL,
            activityId = d.getString("activityId") ?: "other",
            title = d.getString("title"),
            start = start,
            end = end,
            metrics = metricsFrom(m),
            notes = d.getString("notes"),
            deleted = d.getBoolean("deleted") ?: false,
        )
    }

    companion object {
        private const val METRICS_PREFS = "workout_metrics"
        private const val HC = "health_connect"
        private const val MANUAL = "manual"

        /**
         * Combines device sessions with saved records: edits replace a session's fields, tombstones
         * hide it, and manual workouts are added. Newest first.
         */
        fun merge(device: List<Workout>, records: List<WorkoutRecord>): List<Workout> {
            val byId = records.associateBy { it.id }
            val fromDevice = device.mapNotNull { w ->
                val r = byId[w.id] ?: return@mapNotNull w
                if (r.deleted) null else w.copy(
                    activityId = r.activityId,
                    title = r.title,
                    start = r.start,
                    end = r.end,
                    metrics = r.metrics,
                    notes = r.notes,
                    edited = true,
                )
            }
            val deviceIds = device.map { it.id }.toSet()
            val folded = foldDuplicates(fromDevice)
            val manual = records
                .filter { it.source == WorkoutSource.MANUAL && !it.deleted && it.id !in deviceIds }
                .map { r -> Workout(r.id, WorkoutSource.MANUAL, r.activityId, r.title, r.start, r.end, r.metrics, r.notes) }
            return (folded + manual).sortedByDescending { it.start }
        }

        /**
         * Two device recordings are the same session when they're the same activity (a run, not a
         * run and a walk) and overlap for at least half of the shorter one. Each group keeps the recording the user
         * edited, else the one with the most data, else the longest; the rest ride along in
         * [Workout.duplicates].
         */
        internal fun foldDuplicates(workouts: List<Workout>): List<Workout> {
            val ranked = workouts.sortedWith(
                compareByDescending<Workout> { it.edited }
                    .thenByDescending { dataScore(it) }
                    .thenByDescending { it.duration },
            )
            val kept = mutableListOf<Workout>()
            ranked.forEach { w ->
                val i = kept.indexOfFirst { k -> k.activityId == w.activityId && overlapsMostly(k, w) }
                if (i >= 0) kept[i] = kept[i].copy(duplicates = kept[i].duplicates + w) else kept += w
            }
            return kept
        }

        private fun dataScore(w: Workout): Int = listOfNotNull(w.metrics.distanceM, w.metrics.avgHr, w.metrics.kcal).size

        private fun overlapsMostly(a: Workout, b: Workout): Boolean {
            val overlap = Duration.between(maxOf(a.start, b.start), minOf(a.end, b.end))
            val shorter = minOf(a.duration, b.duration)
            return !overlap.isNegative && !shorter.isZero && overlap.toMillis() * 2 >= shorter.toMillis()
        }

        fun WorkoutMetricsInput.toMap(): Map<String, Any?> = buildMap {
            distanceM?.let { put("distanceM", it) }
            elevationM?.let { put("elevationM", it) }
            avgHr?.let { put("avgHr", it) }
            kcal?.let { put("kcal", it) }
            laps?.let { put("laps", it) }
            poolLengthM?.let { put("poolLengthM", it) }
            reps?.let { put("reps", it) }
            rpe?.let { put("rpe", it) }
            if (exercises.isNotEmpty()) {
                put("exercises", exercises.map { e ->
                    mapOf("name" to e.name, "sets" to e.sets.map { s -> mapOf("reps" to s.reps, "weightKg" to s.weightKg) })
                })
            }
        }

        fun metricsFrom(m: Map<String, Any?>): WorkoutMetricsInput {
            fun num(key: String) = (m[key] as? Number)
            @Suppress("UNCHECKED_CAST")
            val exercises = (m["exercises"] as? List<Map<String, Any?>>).orEmpty().map { e ->
                StrengthExercise(
                    name = e["name"] as? String ?: "Exercise",
                    sets = (e["sets"] as? List<Map<String, Any?>>).orEmpty().map { s ->
                        StrengthSet((s["reps"] as? Number)?.toInt() ?: 0, (s["weightKg"] as? Number)?.toDouble())
                    },
                )
            }
            return WorkoutMetricsInput(
                distanceM = num("distanceM")?.toDouble(),
                elevationM = num("elevationM")?.toDouble(),
                avgHr = num("avgHr")?.toInt(),
                kcal = num("kcal")?.toInt(),
                laps = num("laps")?.toInt(),
                poolLengthM = num("poolLengthM")?.toInt(),
                reps = num("reps")?.toInt(),
                rpe = num("rpe")?.toInt(),
                exercises = exercises,
            )
        }
    }
}
