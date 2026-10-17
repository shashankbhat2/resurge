package com.resurgeai.core.data.weight

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.listen
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.healthconnect.HealthDataSource
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.model.WeightGoal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weight log, owned by Resurge (Health Connect is only an optional import source and mirror):
 *   users/{uid}/weights/{id}         kg, atMs, localDate, source, note?, hcId? (imports use id "hc_<record id>")
 *   users/{uid}/weightGoal/current   startKg, startDate, goalKg, paceKgPerWeek
 */
@Singleton
class WeightRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val healthData: HealthDataSource,
    private val preferences: PreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    /** Every weigh-in, oldest first. */
    fun weighIns(): Flow<List<WeighIn>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("weights").orderBy("atMs", Query.Direction.ASCENDING)) { docs -> docs.mapNotNull(::toWeighIn) }
    }

    fun goal(): Flow<WeightGoal?> {
        val ref = user()?.collection("weightGoal")?.document("current") ?: return flowOf(null)
        return callbackFlow {
            val reg = ref.addSnapshotListener { snap, error ->
                if (error != null) close(error) else trySend(snap?.let(::toGoal))
            }
            awaitClose { reg.remove() }
        }
    }

    /** Latest reading, for calorie estimates; falls back to Health Connect before the first weigh-in. */
    suspend fun latestKg(): Double? {
        val ref = user()
        val ours = ref?.let {
            runCatching {
                it.collection("weights").orderBy("atMs", Query.Direction.DESCENDING).limit(1).get().await().documents.firstOrNull()?.getDouble("kg")
            }.getOrNull()
        }
        return ours ?: runCatching { healthData.latestWeight() }.getOrNull()
    }

    /** Saves a new weigh-in (id null) or edits one; new manual ones are mirrored to Health Connect when that's on. */
    fun save(id: String?, kg: Double, at: Instant, note: String?, zone: ZoneId = ZoneId.systemDefault()) {
        val col = user()?.collection("weights") ?: return
        val doc = if (id == null) col.document() else col.document(id)
        val data = mapOf(
            "kg" to kg,
            "atMs" to at.toEpochMilli(),
            "localDate" to at.atZone(zone).toLocalDate().toString(),
            "note" to note?.trim()?.takeIf { it.isNotEmpty() }?.take(NOTE_MAX),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (id == null) {
            doc.set(data + ("source" to WeighInSource.MANUAL.wire))
            scope.launch {
                if (!preferences.weightWriteToHealthConnect.first()) return@launch
                runCatching { healthData.writeWeight(kg, at) }
                    .onSuccess { hcId -> hcId?.let { doc.update("hcId", it) } }
                    .onFailure { Log.w(TAG, "Writing weight to Health Connect failed", it) }
            }
        } else {
            doc.set(data, SetOptions.merge())
        }
    }

    fun delete(weighIn: WeighIn) {
        val doc = user()?.collection("weights")?.document(weighIn.id) ?: return
        scope.launch {
            val hcId = runCatching { doc.get().await().getString("hcId") }.getOrNull()
            doc.delete()
            if (hcId != null) runCatching { healthData.deleteWeight(hcId) }.onFailure { Log.w(TAG, "Removing weight from Health Connect failed", it) }
        }
    }

    fun restore(weighIn: WeighIn) {
        user()?.collection("weights")?.document(weighIn.id)?.set(
            mapOf(
                "kg" to weighIn.kg,
                "atMs" to weighIn.at.toEpochMilli(),
                "localDate" to weighIn.date().toString(),
                "note" to weighIn.note,
                "source" to weighIn.source.wire,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    fun setGoal(goal: WeightGoal) {
        user()?.collection("weightGoal")?.document("current")?.set(
            mapOf(
                "startKg" to goal.startKg,
                "startDate" to goal.startDate.toString(),
                "goalKg" to goal.goalKg,
                "paceKgPerWeek" to goal.paceKgPerWeek,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
    }

    fun clearGoal() {
        user()?.collection("weightGoal")?.document("current")?.delete()
    }

    // --- Health Connect import ---

    /** Weigh-ins in Health Connect (from other apps and scales) that could be imported. */
    suspend fun importableCount(): Int = runCatching { healthData.weightHistory(IMPORT_SINCE).size }.getOrDefault(0)

    /** Copies Health Connect's weight history in once. Re-running is harmless: ids are the record ids. */
    suspend fun importFromHealthConnect(): Int {
        val col = user()?.collection("weights") ?: return 0
        val zone = ZoneId.systemDefault()
        val records = healthData.weightHistory(IMPORT_SINCE)
        records.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { r ->
                batch.set(
                    col.document("hc_" + r.recordId.take(120)),
                    mapOf(
                        "kg" to r.kg,
                        "atMs" to r.at.toEpochMilli(),
                        "localDate" to r.at.atZone(zone).toLocalDate().toString(),
                        "source" to WeighInSource.HEALTH_CONNECT.wire,
                        "hcId" to r.recordId,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
            }
            batch.commit()
        }
        return records.size
    }

    private fun toWeighIn(d: DocumentSnapshot): WeighIn? {
        val kg = d.getDouble("kg") ?: return null
        val at = d.getLong("atMs")?.let(Instant::ofEpochMilli) ?: return null
        return WeighIn(d.id, kg, at, d.getString("note"), WeighInSource.fromWire(d.getString("source")))
    }

    private fun toGoal(d: DocumentSnapshot): WeightGoal? {
        if (!d.exists()) return null
        return WeightGoal(
            startKg = d.getDouble("startKg") ?: return null,
            startDate = d.getString("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            goalKg = d.getDouble("goalKg") ?: return null,
            paceKgPerWeek = d.getDouble("paceKgPerWeek") ?: 0.5,
        )
    }

    private companion object {
        const val TAG = "WeightRepository"
        const val NOTE_MAX = 120
        /** How far back the import looks. */
        val IMPORT_SINCE: Instant = Instant.parse("2015-01-01T00:00:00Z")
    }
}
