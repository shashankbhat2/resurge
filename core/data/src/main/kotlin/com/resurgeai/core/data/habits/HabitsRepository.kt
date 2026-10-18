package com.resurgeai.core.data.habits

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.progress.PhotoKeyLockedException
import com.resurgeai.core.data.progress.PhotoKeys
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.PhotoKeyState
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Habits and daily check-ins:
 *   users/{uid}/habits/{id}           name, icon, color, order, archived, createdDate, cached streaks,
 *                                     private?, nameSealed? (a private habit's real name, encrypted)
 *   users/{uid}/habitDays/{yyyy-MM-dd} { date, entries: { habitId: "done" | "missed" } }
 * Writes go through Firestore's offline cache, so swipes work without a connection. A private
 * habit's real name is end-to-end encrypted with the same key as progress photos ([PhotoKeys]);
 * the stored `name` is its cover name.
 */
@Singleton
class HabitsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val keys: PhotoKeys,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    fun habits(): Flow<List<Habit>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("habits").orderBy("order")) { docs -> docs.mapNotNull(::toHabit) }
    }

    /** Check-ins from [since] onwards, keyed by habit day then habit id. */
    fun days(since: LocalDate): Flow<Map<LocalDate, Map<String, HabitStatus>>> {
        val ref = user() ?: return flowOf(emptyMap())
        val query = ref.collection("habitDays").whereGreaterThanOrEqualTo("date", since.toString())
        return listen(query) { docs ->
            docs.mapNotNull { d ->
                val date = runCatching { LocalDate.parse(d.id) }.getOrNull() ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val entries = (d.get("entries") as? Map<String, Any?>).orEmpty()
                    .mapNotNull { (id, v) -> HabitStatus.fromWire(v)?.let { id to it } }
                    .toMap()
                date to entries
            }.toMap()
        }
    }

    /** One-shot read (cache or server) of a single day, for the reminder. */
    suspend fun dayOnce(date: LocalDate): Map<String, HabitStatus> {
        val ref = user() ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        val entries = ref.collection("habitDays").document(date.toString()).get().await().get("entries") as? Map<String, Any?>
        return entries.orEmpty().mapNotNull { (id, v) -> HabitStatus.fromWire(v)?.let { id to it } }.toMap()
    }

    suspend fun habitsOnce(): List<Habit> =
        user()?.collection("habits")?.get()?.await()?.documents?.mapNotNull(::toHabit).orEmpty()

    /** Logs (or with null, clears) a habit's status for a habit day. */
    fun setStatus(date: LocalDate, habitId: String, status: HabitStatus?) {
        val ref = user()?.collection("habitDays")?.document(date.toString()) ?: return
        val value: Any = status?.wire ?: FieldValue.delete()
        ref.set(
            mapOf("date" to date.toString(), "entries" to mapOf(habitId to value), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    /** Adds a habit. A private one needs the key, so this can fail (offline the first time). */
    suspend fun add(draft: HabitDraft, order: Int, today: LocalDate) {
        val doc = user()?.collection("habits")?.document() ?: return
        val sealed = if (draft.private) seal(doc.id, draft.realName ?: draft.name) else null
        doc.set(newHabitMap(draft, order, today) + privacyFields(draft, sealed))
    }

    suspend fun update(habitId: String, draft: HabitDraft) {
        val doc = user()?.collection("habits")?.document(habitId) ?: return
        val base = mapOf<String, Any>("name" to draft.name.trim(), "icon" to draft.icon, "color" to draft.color, "updatedAt" to FieldValue.serverTimestamp())
        val real = draft.realName?.trim()
        val privacy: Map<String, Any> = when {
            !draft.private -> mapOf("private" to false, "nameSealed" to FieldValue.delete())
            // Editing a private habit without retyping its name keeps the stored one.
            real.isNullOrEmpty() -> mapOf("private" to true)
            else -> mapOf("private" to true, "nameSealed" to Blob.fromBytes(seal(habitId, real)))
        }
        doc.update(base + privacy)
    }

    /** A private habit's real name, or null when it can't be read on this phone. */
    suspend fun realName(habitId: String): String? {
        val uid = auth.currentUser?.uid ?: return null
        val sealed = user()?.collection("habits")?.document(habitId)?.get()?.await()?.getBlob("nameSealed")?.toBytes() ?: return null
        return runCatching { String(keys.aead().decrypt(sealed, ad(uid, habitId)), Charsets.UTF_8) }.getOrNull()
    }

    /** Encrypts a real name, making the key first if this is the user's first private thing. */
    private suspend fun seal(habitId: String, name: String): ByteArray {
        val uid = auth.currentUser?.uid ?: throw PhotoKeyLockedException()
        if (keys.refresh() == PhotoKeyState.NOT_SET_UP) keys.setUp()
        return keys.aead().encrypt(name.trim().toByteArray(Charsets.UTF_8), ad(uid, habitId))
    }

    private fun privacyFields(draft: HabitDraft, sealed: ByteArray?): Map<String, Any> =
        if (draft.private && sealed != null) mapOf("private" to true, "nameSealed" to Blob.fromBytes(sealed)) else emptyMap()

    private fun ad(uid: String, habitId: String) = "$uid/habit/$habitId/name".toByteArray()

    fun setArchived(habitId: String, archived: Boolean) {
        user()?.collection("habits")?.document(habitId)?.update(
            mapOf("archived" to archived, "updatedAt" to FieldValue.serverTimestamp()),
        )
    }

    /** Caches computed streaks on the habit so the coach (backend) can read them cheaply. */
    fun cacheStreak(habitId: String, current: Int, best: Int) {
        user()?.collection("habits")?.document(habitId)?.update(mapOf("currentStreak" to current, "bestStreak" to best))
    }

    private fun toHabit(d: DocumentSnapshot): Habit? {
        val name = d.getString("name") ?: return null
        return Habit(
            id = d.id,
            name = name,
            icon = d.getString("icon") ?: "flame",
            color = d.getString("color") ?: "accent",
            order = (d.getLong("order") ?: 0L).toInt(),
            archived = d.getBoolean("archived") ?: false,
            createdDate = d.getString("createdDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
            private = d.getBoolean("private") ?: false,
        )
    }

    private fun <T> listen(query: Query, map: (List<DocumentSnapshot>) -> T): Flow<T> = callbackFlow {
        val registration = query.addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap != null) trySend(map(snap.documents))
        }
        awaitClose { registration.remove() }
    }

    companion object {
        fun newHabitMap(draft: HabitDraft, order: Int, today: LocalDate) = mapOf(
            "name" to draft.name.trim(),
            "icon" to draft.icon,
            "color" to draft.color,
            "order" to order,
            "archived" to false,
            "createdDate" to today.toString(),
            "currentStreak" to 0,
            "bestStreak" to 0,
            "createdAt" to FieldValue.serverTimestamp(),
        )
    }
}
