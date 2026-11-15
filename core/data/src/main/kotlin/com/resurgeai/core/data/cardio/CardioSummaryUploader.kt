package com.resurgeai.core.data.cardio

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.model.CardioStatus
import com.resurgeai.core.model.DailyCardioPoints
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies each recent day's cardio points into users/{uid}/dailySummaries/{date} (cardioPoints,
 * cardioEstimated), so the AI coach and MCP clients can read them. Only the day totals leave the
 * phone; the per-workout breakdown stays in the local cache.
 */
@Singleton
class CardioSummaryUploader @Inject constructor(private val firestore: FirebaseFirestore) {
    suspend fun upload(uid: String, days: List<DailyCardioPoints>) {
        val ready = days.filter { it.status == CardioStatus.READY || it.status == CardioStatus.LIMITED_DATA }.takeLast(DAYS)
        if (ready.isEmpty()) return
        runCatching {
            val batch = firestore.batch()
            val col = firestore.collection("users").document(uid).collection("dailySummaries")
            ready.forEach { batch.set(col.document(it.date.toString()), fields(it), SetOptions.merge()) }
            batch.commit().await()
        }.onFailure { Log.w("CardioSummaryUploader", "Upload failed", it) }
    }

    companion object {
        /** Recent days rewritten on each refresh (late watch data can change them). */
        const val DAYS = 7

        internal fun fields(day: DailyCardioPoints): Map<String, Any> = mapOf(
            "date" to day.date.toString(),
            "cardioPoints" to day.points,
            "cardioEstimated" to day.estimated,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }
}
