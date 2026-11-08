package com.resurgeai.core.data.cardio

import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.google.firebase.auth.FirebaseAuth
import com.resurgeai.core.data.di.ApplicationScope
import com.resurgeai.core.healthconnect.CardioHealthDataSource
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.CardioStatus
import com.resurgeai.core.model.DailyCardioPoints
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class CardioPointsState(
    val days: List<DailyCardioPoints> = emptyList(),
    val loading: Boolean = true,
    val status: CardioStatus = CardioStatus.LIMITED_DATA,
    val refreshing: Boolean = false,
    val zone: String = ZoneId.systemDefault().id,
)

@Singleton
class CardioPointsRepository @Inject constructor(
    private val health: CardioHealthDataSource,
    private val manager: HealthConnectManager,
    private val cache: CardioPointsCache,
    private val auth: FirebaseAuth,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(CardioPointsState())
    val state: StateFlow<CardioPointsState> = mutableState.asStateFlow()
    private val mutex = Mutex()
    @Volatile private var refreshJob: Job? = null
    private var currentUid = auth.currentUser?.uid

    init {
        auth.addAuthStateListener { firebase ->
            val next = firebase.currentUser?.uid
            if (next != currentUid) {
                val previous = currentUid
                currentUid = next
                refreshJob?.cancel()
                mutableState.value = CardioPointsState()
                if (previous != null) scope.launch {
                    try {
                        mutex.withLock { cache.clearForUser(previous) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Cardio cache cleanup failed after account change", e)
                    }
                }
            }
        }
    }

    /** Called with exactly the merged workouts displayed by Fitness, including local edits. */
    suspend fun refresh(workouts: List<Workout>, workoutReadComplete: Boolean = true, force: Boolean = false, cacheOnly: Boolean = false) = mutex.withLock {
        val uid = auth.currentUser?.uid ?: return@withLock
        refreshJob = currentCoroutineContext()[Job]
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        val first = today.minusDays(CardioPointsEngine.HISTORY_DAYS - 1)
        var cached = CardioCacheSnapshot(emptyList(), null)
        mutableState.value = CardioPointsState(loading = true, zone = zone.id)
        try {
            // Permission checks precede even cached display. Changed access invalidates cached HC data.
            val availability = manager.availability()
            val permissions = manager.grantedPermissions()
            val steps = HealthPermission.getReadPermission(StepsRecord::class) in permissions
            val exercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class) in permissions
            val access = "${availability.name}:$steps:$exercise"
            cached = cache.load(uid, zone.id, access, first, today)
            ensureUser(uid)
            val baseStatus = when {
                availability != HealthConnectAvailability.AVAILABLE -> CardioStatus.UNAVAILABLE
                !steps || !exercise -> CardioStatus.PERMISSION_REQUIRED
                else -> CardioStatus.READY
            }
            val cachedDays = cached.days.map { it.summary }
            mutableState.value = CardioPointsState(cachedDays, cachedDays.isEmpty(), summaryStatus(baseStatus, cachedDays), !cacheOnly, zone.id)
            if (cacheOnly) return@withLock
            val effective = workouts.filter { it.source == WorkoutSource.MANUAL || exercise }
            val fingerprints = (0L until CardioPointsEngine.HISTORY_DAYS).associate { offset ->
                val date = first.plusDays(offset)
                date to workoutFingerprint(effective, date, zone)
            }
            val byDate = cached.days.associateBy { it.summary.date }.toMutableMap()
            val full = force || cached.fullRefreshDate != today
            val dates = fingerprints.keys.filter { date ->
                full || date >= today.minusDays(2) || byDate[date]?.workoutFingerprint != fingerprints[date]
            }
            val calculated = try {
                check(workoutReadComplete) { "Incomplete workout read" }
                val readFirst = dates.minOrNull() ?: today
                val readLast = dates.maxOrNull() ?: today
                val from = readFirst.atStartOfDay(zone).toInstant()
                val to = minOf(readLast.plusDays(1).atStartOfDay(zone).toInstant(), now)
                val evidence = if (steps && from < to) health.evidence(from, to) else emptyList()
                CardioPointsEngine.calculate(readFirst, readLast, zone, now, evidence, effective, baseStatus).associateBy { it.date }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val failed = calculated == null
            for (date in dates) {
                currentCoroutineContext().ensureActive(); ensureUser(uid)
                val summary = calculated?.get(date)
                if (summary != null) {
                    byDate[date] = CachedCardioDay(summary, fingerprints.getValue(date))
                } else {
                    val previous = byDate[date]
                    byDate[date] = previous?.copy(summary = previous.summary.copy(status = CardioStatus.STALE))
                        ?: CachedCardioDay(CardioPointsEngine.calculate(date, date, zone, now, emptyList(), effective, CardioStatus.STALE).single(), "")
                }
                ensureUser(uid)
                val days = byDate.values.sortedBy { it.summary.date }
                // Persist each completed day atomically; a later cancellation cannot lose earlier days.
                cache.save(uid, zone.id, access, days, cached.fullRefreshDate)
                mutableState.value = CardioPointsState(days.map { it.summary }, false,
                    if (failed) CardioStatus.STALE else summaryStatus(baseStatus, days.map { it.summary }), true, zone.id)
            }
            ensureUser(uid)
            val days = byDate.values.sortedBy { it.summary.date }
            cache.save(uid, zone.id, access, days, if (full && !failed) today else cached.fullRefreshDate)
            mutableState.value = CardioPointsState(days.map { it.summary }, false,
                if (failed) CardioStatus.STALE else summaryStatus(baseStatus, days.map { it.summary }),
                zone = zone.id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ensureUser(uid)
            // Never reuse an unchecked HC cache if permission discovery itself failed.
            mutableState.value = CardioPointsState(
                cached.days.map { it.summary.copy(status = CardioStatus.STALE) }, false, CardioStatus.STALE, zone = zone.id,
            )
        } finally {
            refreshJob = null
        }
    }

    private fun ensureUser(uid: String) { if (auth.currentUser?.uid != uid) throw CancellationException("Account changed") }

    /** Cached quality must survive a cache-only load and incremental refresh publications. */
    private fun summaryStatus(access: CardioStatus, days: List<DailyCardioPoints>): CardioStatus = when {
        days.any { it.status == CardioStatus.STALE } -> CardioStatus.STALE
        access == CardioStatus.READY && days.any { it.status == CardioStatus.LIMITED_DATA } -> CardioStatus.LIMITED_DATA
        else -> access
    }

    private companion object {
        const val TAG = "CardioPointsRepository"
    }
}

internal fun workoutFingerprint(workouts: List<Workout>, date: LocalDate, zone: ZoneId): String {
    val start = date.atStartOfDay(zone).toInstant(); val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val text = workouts.filter { it.start < end && it.end > start }.sortedBy { it.id }.joinToString("\n") {
        listOf(it.id, it.activityId, it.start, it.end, it.source, it.edited, it.title, it.sourceName).joinToString("|")
    }
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
