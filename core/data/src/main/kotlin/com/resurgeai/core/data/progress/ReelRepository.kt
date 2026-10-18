package com.resurgeai.core.data.progress

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.listen
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.model.ProgressMath
import com.resurgeai.core.model.ProgressPhoto
import com.resurgeai.core.model.ProgressReel
import com.resurgeai.core.model.ReelDecision
import com.resurgeai.core.model.ReelInterval
import com.resurgeai.core.model.ReelKind
import com.resurgeai.core.model.ReelPlanner
import com.resurgeai.core.model.ReelSettings
import com.resurgeai.core.model.WeightMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress reels: the made reels and the schedule, both in Firestore so every phone agrees on what's
 * been made (the photos stay encrypted; a reel only lists photo ids):
 *   users/{uid}/progressReels/{kind_dueDate}     kind, createdAtMs, from, to, frameIds, newFrames, milestoneKg?, watched
 *   users/{uid}/progressSettings/current          reelsEnabled, intervalDays, lastDue, lastMilestoneKg, goalReelFor
 * Ids come from the kind and day, so two phones checking on the same morning write the same reel.
 */
@Singleton
class ReelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val photos: ProgressRepository,
    private val weights: WeightRepository,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }
    private fun settingsDoc() = user()?.collection("progressSettings")?.document("current")

    fun settings(): Flow<ReelSettings> {
        val ref = settingsDoc() ?: return flowOf(ReelSettings())
        return callbackFlow {
            val reg = ref.addSnapshotListener { snap, error ->
                if (error != null) close(error) else trySend(snap?.let(::toSettings) ?: ReelSettings())
            }
            awaitClose { reg.remove() }
        }
    }

    /** Newest first. */
    fun reels(): Flow<List<ProgressReel>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("progressReels").orderBy("createdAtMs", Query.Direction.DESCENDING).limit(50)) { docs -> docs.mapNotNull(::toReel) }
    }

    suspend fun reel(id: String): ProgressReel? = user()?.collection("progressReels")?.document(id)?.get()?.await()?.let(::toReel)

    fun setInterval(interval: ReelInterval) {
        settingsDoc()?.set(mapOf("intervalDays" to interval.days, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    fun setEnabled(on: Boolean) {
        settingsDoc()?.set(mapOf("reelsEnabled" to on, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
    }

    /** Removes every reel, when starting over with a new photo key. */
    suspend fun deleteAll() {
        val ref = user() ?: return
        ref.collection("progressReels").get().await().documents.forEach { it.reference.delete().await() }
    }

    fun markWatched(id: String) {
        user()?.collection("progressReels")?.document(id)?.update(mapOf("watched" to true, "updatedAt" to FieldValue.serverTimestamp()))
    }

    /**
     * The daily check: makes a reel if one is due (or a milestone or the goal was just reached) and
     * there are new photos, and records what was handled. Returns the new reel, or null.
     */
    suspend fun check(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): ProgressReel? {
        val ref = user() ?: return null
        val settings = settingsDoc()?.get()?.await()?.let(::toSettings) ?: ReelSettings()
        val all = ref.collection("progressPhotos").get().await().documents.mapNotNull { d ->
            val at = d.getLong("takenAtMs")?.let(Instant::ofEpochMilli) ?: return@mapNotNull null
            ProgressPhoto(d.id, at, 0, 0, d.getBoolean("uploaded") ?: true)
        }.sortedBy { it.takenAt }
        // Holding a weight has no milestones or end to make reels of.
        val goal = runCatching { weights.goal().first() }.getOrNull()?.takeIf { !it.maintaining }
        val trend = WeightMath.trend(runCatching { weights.weighIns().first() }.getOrDefault(emptyList()), today, zone)
        val current = trend.lastOrNull()?.kg
        val lastReelAt = ref.collection("progressReels").orderBy("createdAtMs", Query.Direction.DESCENDING).limit(1).get().await()
            .documents.firstOrNull()?.getLong("createdAtMs")?.let(Instant::ofEpochMilli)

        val decision = ReelPlanner.decide(
            today = today,
            anchor = ProgressMath.anchor(goal?.startDate, all, zone),
            photos = all,
            lastReelAt = lastReelAt,
            settings = settings,
            milestoneKg = goal?.let { WeightMath.newMilestone(it, trend, today) },
            goalReached = goal?.takeIf { current != null && it.reached(current) }?.startDate,
            zone = zone,
        )
        val update = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        return when (decision) {
            is ReelDecision.Skip -> {
                decision.handledDue?.let {
                    update["lastDue"] = it.toString()
                    settingsDoc()?.set(update, SetOptions.merge())?.await()
                }
                null
            }
            is ReelDecision.Make -> {
                val id = "${decision.kind.wire}_${decision.handledDue ?: today}"
                val now = Instant.now()
                val reel = ProgressReel(
                    id = id,
                    kind = decision.kind,
                    createdAt = now,
                    from = decision.frames.first().date(zone),
                    to = decision.frames.last().date(zone),
                    frameIds = decision.frames.map { it.id },
                    newFrames = decision.newFrames,
                    milestoneKg = decision.milestoneKg,
                )
                decision.handledDue?.let { update["lastDue"] = it.toString() }
                decision.milestoneKg?.let { update["lastMilestoneKg"] = it }
                if (decision.kind == ReelKind.GOAL) goal?.let { update["goalReelFor"] = it.startDate.toString() }
                val batch = firestore.batch()
                batch.set(
                    ref.collection("progressReels").document(id),
                    buildMap {
                        put("kind", reel.kind.wire)
                        put("createdAtMs", now.toEpochMilli())
                        put("from", reel.from.toString())
                        put("to", reel.to.toString())
                        put("frameIds", reel.frameIds)
                        put("newFrames", reel.newFrames)
                        reel.milestoneKg?.let { put("milestoneKg", it) }
                        put("watched", false)
                        put("updatedAt", FieldValue.serverTimestamp())
                    },
                )
                batch.set(settingsDoc()!!, update, SetOptions.merge())
                batch.commit().await()
                reel
            }
        }
    }

    private fun toSettings(d: DocumentSnapshot): ReelSettings = ReelSettings(
        enabled = d.getBoolean("reelsEnabled") ?: true,
        interval = ReelInterval.fromDays(d.getLong("intervalDays")?.toInt()),
        lastDue = d.getString("lastDue")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        lastMilestoneKg = d.getDouble("lastMilestoneKg"),
        goalReelFor = d.getString("goalReelFor")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    )

    @Suppress("UNCHECKED_CAST")
    private fun toReel(d: DocumentSnapshot): ProgressReel? {
        if (!d.exists()) return null
        return ProgressReel(
            id = d.id,
            kind = ReelKind.fromWire(d.getString("kind")),
            createdAt = d.getLong("createdAtMs")?.let(Instant::ofEpochMilli) ?: return null,
            from = d.getString("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            to = d.getString("to")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            frameIds = (d.get("frameIds") as? List<String>).orEmpty(),
            newFrames = d.getLong("newFrames")?.toInt() ?: 0,
            milestoneKg = d.getDouble("milestoneKg"),
            watched = d.getBoolean("watched") ?: false,
        )
    }
}
