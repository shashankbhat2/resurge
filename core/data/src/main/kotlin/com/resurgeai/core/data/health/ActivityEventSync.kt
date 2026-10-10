package com.resurgeai.core.data.health

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthDataSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds workouts and nights that finished recently and posts each one once to
 * users/{uid}/events/{healthConnectId}. The analyzeEvent Cloud Function turns each into an insight.
 *
 * Scans a recent window rather than using a Changes token: it's naturally idempotent (document ID =
 * Health Connect record ID, remembered locally once posted) and picks up late-syncing watch data.
 */
@Singleton
class ActivityEventSync @Inject constructor(
    private val healthData: HealthDataSource,
    private val healthConnect: HealthConnectManager,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferences: PreferencesRepository,
) {
    private val mutex = Mutex()

    suspend fun syncFinished(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Int = mutex.withLock {
        val uid = auth.currentUser?.uid ?: return 0
        if (healthConnect.grantedPermissions().isEmpty()) return 0

        val posted = preferences.postedEventIds(uid)
        // First run: don't flood the feed with history — only the last 12h get insights.
        val firstRun = posted.isEmpty() && !preferences.hasPostedEvents(uid)
        val windowStart = now.minus(if (firstRun) FIRST_RUN_WINDOW else SCAN_WINDOW)

        val workouts = healthData.exerciseSessionsBetween(now.minus(SCAN_WINDOW), now)
            .filter { EventPayloads.isPostableWorkout(it, now) && it.id !in posted }
        val nights = healthData.sleepSessions(now.minus(SCAN_WINDOW), now)
            .filter { EventPayloads.isPostableSleep(it, now) && it.id !in posted }

        val newlyPosted = mutableSetOf<String>()
        var count = 0
        for (w in workouts.sortedBy { it.end }) {
            if (w.end.isBefore(windowStart)) { newlyPosted += w.id; continue }
            val payload = EventPayloads.workout(w, healthData.metricsBetween(w.start, w.end), zone)
            if (post(uid, w.id, payload)) count++
            newlyPosted += w.id
        }
        for (n in nights.sortedBy { it.end }) {
            if (n.end.isBefore(windowStart)) { newlyPosted += n.id; continue }
            val payload = EventPayloads.sleep(n, healthData.metricsBetween(n.start, n.end), zone)
            if (post(uid, n.id, payload)) count++
            newlyPosted += n.id
        }
        preferences.addPostedEventIds(uid, newlyPosted)
        count
    }

    /** Returns true if created; an existing doc (rules deny overwrite) counts as already posted. */
    private suspend fun post(uid: String, id: String, payload: Map<String, Any?>): Boolean = try {
        firestore.collection("users").document(uid).collection("events").document(id)
            .set(payload + ("createdAt" to FieldValue.serverTimestamp()))
            .await()
        true
    } catch (e: FirebaseFirestoreException) {
        if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) Log.w(TAG, "Event post failed", e)
        false
    }

    private companion object {
        const val TAG = "ActivityEventSync"
        val SCAN_WINDOW: Duration = Duration.ofHours(48)
        val FIRST_RUN_WINDOW: Duration = Duration.ofHours(12)
    }
}
