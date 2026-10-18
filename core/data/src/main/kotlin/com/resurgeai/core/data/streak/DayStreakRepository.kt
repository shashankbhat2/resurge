package com.resurgeai.core.data.streak

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.resurgeai.core.data.listen
import com.resurgeai.core.model.DayResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settled days for the day streak, and the history used to settle them:
 *   users/{uid}/dayResults/{yyyy-MM-dd}   met, done, total
 * A day is saved once it's past yesterday's grace period, judged by the goals chosen then, so
 * changing goals later never rewrites past days.
 */
@Singleton
class DayStreakRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    fun results(since: LocalDate): Flow<List<DayResult>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("dayResults").whereGreaterThanOrEqualTo("date", since.toString())) { docs ->
            docs.mapNotNull { d ->
                val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                DayResult(date, d.getBoolean("met") ?: false, d.getLong("done")?.toInt() ?: 0, d.getLong("total")?.toInt() ?: 0)
            }
        }
    }

    suspend fun save(results: List<DayResult>) {
        val ref = user() ?: return
        results.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { r ->
                batch.set(
                    ref.collection("dayResults").document(r.date.toString()),
                    mapOf("date" to r.date.toString(), "met" to r.met, "done" to r.done, "total" to r.total, "updatedAt" to FieldValue.serverTimestamp()),
                )
            }
            batch.commit().await()
        }
    }

    /** Sleep minutes and steps per day from the daily summaries the app uploads. */
    suspend fun summaries(since: LocalDate): Map<LocalDate, Pair<Int?, Long?>> {
        val ref = user() ?: return emptyMap()
        return ref.collection("dailySummaries").whereGreaterThanOrEqualTo("date", since.toString()).get().await().documents
            .mapNotNull { d ->
                val date = (d.getString("date") ?: d.id).let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                date to ((d.getLong("asleepMin") ?: d.getLong("sleepMin"))?.toInt() to d.getLong("steps"))
            }.toMap()
    }

    /** Meals logged per day. */
    suspend fun mealCounts(since: LocalDate): Map<LocalDate, Int> {
        val ref = user() ?: return emptyMap()
        return ref.collection("meals").whereGreaterThanOrEqualTo("localDate", since.toString()).get().await().documents
            .mapNotNull { d -> d.getString("localDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
            .groupingBy { it }.eachCount()
    }

    /** Water per day, in ml. */
    suspend fun water(since: LocalDate): Map<LocalDate, Int> {
        val ref = user() ?: return emptyMap()
        return ref.collection("waterDays").whereGreaterThanOrEqualTo("date", since.toString()).get().await().documents
            .mapNotNull { d ->
                val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                date to (d.getLong("ml")?.toInt() ?: 0)
            }.toMap()
    }
}
