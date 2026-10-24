package com.resurgeai.core.data.scores

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.resurgeai.core.model.Score
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One day's three scores as the coach reads them. */
data class DayScoreRecord(val date: LocalDate, val recovery: Score, val effort: Score, val fuel: Score) {
    fun toMap(): Map<String, Any?> = mapOf(
        "date" to date.toString(),
        "recovery" to recovery.value,
        "effort" to effort.value,
        "fuel" to fuel.value,
        "recoveryWhy" to recovery.reason.take(MAX_WHY),
        "effortWhy" to effort.reason.take(MAX_WHY),
        "fuelWhy" to fuel.reason.take(MAX_WHY),
        "effortLow" to effort.band?.first?.coerceIn(0, 100),
        "effortHigh" to effort.band?.last?.coerceIn(0, 100),
        "soFar" to (effort.soFar || fuel.soFar),
    )

    private companion object {
        const val MAX_WHY = 300
    }
}

/**
 * Daily scores for the coach and the weekly review (the app works them out itself):
 *   users/{uid}/dayScores/{yyyy-MM-dd}   recovery, effort, fuel (0-100 or null), a sentence on each
 */
@Singleton
class DayScoresRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    suspend fun save(days: List<DayScoreRecord>) {
        val uid = auth.currentUser?.uid ?: return
        val ref = firestore.collection("users").document(uid).collection("dayScores")
        days.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { d -> batch.set(ref.document(d.date.toString()), d.toMap() + ("updatedAt" to FieldValue.serverTimestamp())) }
            batch.commit().await()
        }
    }
}
