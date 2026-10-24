package com.resurgeai.core.data.coach

import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.StreamResponse
import com.resurgeai.core.model.ChatEvent
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.InsightHighlight
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.PlanChangeOffer
import com.resurgeai.core.model.TurnRole
import com.resurgeai.core.model.WeeklyArea
import com.resurgeai.core.model.WeeklyDetails
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.reactive.asFlow
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coach data: insights feed + chat threads (read live from Firestore, written by Cloud Functions)
 * and sending chat messages through the streaming `coachChat` callable.
 */
@Singleton
class CoachRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    fun insights(limit: Long = 50): Flow<List<CoachInsight>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("insights").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)) { docs ->
            docs.mapNotNull(::toInsight)
        }
    }

    /** Marks an insight seen (dismissed from Today). The rules let the client change only `seen` and `seenAt`. */
    /** One insight, live (for its own page). */
    fun insight(id: String): Flow<CoachInsight?> {
        val ref = user() ?: return flowOf(null)
        return callbackFlow {
            val reg = ref.collection("insights").document(id).addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.takeIf { it.exists() }?.let(::toInsight))
            }
            awaitClose { reg.remove() }
        }
    }

    fun markSeen(insightId: String) {
        user()?.collection("insights")?.document(insightId)?.update(mapOf("seen" to true, "seenAt" to FieldValue.serverTimestamp()))
    }

    fun threads(limit: Long = 30): Flow<List<CoachThread>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("coachThreads").orderBy("updatedAt", Query.Direction.DESCENDING).limit(limit)) { docs ->
            docs.map {
                CoachThread(
                    id = it.id,
                    title = it.getString("title") ?: "Conversation",
                    updatedAt = it.getTimestamp("updatedAt")?.toInstant(),
                    preview = it.getString("lastPreview"),
                    insightId = it.getString("insightId"),
                    insightTitle = it.getString("insightTitle"),
                )
            }
        }
    }

    /** Displayable turns of a thread, in order (tool results and context snapshots are skipped). */
    fun turns(threadId: String): Flow<List<CoachTurn>> {
        val ref = user() ?: return emptyFlow()
        return listen(ref.collection("coachThreads").document(threadId).collection("turns").orderBy("seq")) { docs ->
            docs.mapNotNull { d ->
                val text = d.getString("display")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CoachTurn(
                    id = d.id,
                    role = if (d.getString("role") == "user") TurnRole.USER else TurnRole.COACH,
                    text = text,
                    model = CoachModel.fromWire(d.getString("model")),
                    notice = d.getString("kind") == "notice",
                    offer = (d.get("action") as? Map<*, *>)?.takeIf { it["type"] == "plan_change" }?.let { a ->
                        val change = a["change"] as? String ?: return@let null
                        PlanChangeOffer(change, a["label"] as? String ?: "Update my plan")
                    },
                )
            }
        }
    }

    /** Sends a message; emits the thread id, streamed text, tool use, then Done (or Failed). */
    fun send(threadId: String?, text: String, model: CoachModel, insightId: String? = null): Flow<ChatEvent> {
        val data = buildMap {
            put("text", text)
            put("model", model.wire)
            if (threadId != null) put("threadId", threadId) else if (insightId != null) put("insightId", insightId)
        }
        return functions.getHttpsCallable("coachChat")
            .withTimeout(5, TimeUnit.MINUTES)
            .stream(data)
            .asFlow()
            .mapNotNull { response ->
                when (response) {
                    is StreamResponse.Message -> chunk(response.message.getData())
                    is StreamResponse.Result -> {
                        val result = response.result.getData() as? Map<*, *>
                        ChatEvent.Done(
                            threadId = result?.get("threadId") as? String ?: threadId.orEmpty(),
                            text = result?.get("text") as? String ?: "",
                            refused = result?.get("refused") == true,
                        )
                    }
                    else -> null
                }
            }
            .catch { e -> emit(ChatEvent.Failed(friendlyError(e))) }
    }

    private fun chunk(data: Any?): ChatEvent? {
        val map = data as? Map<*, *> ?: return null
        return when (map["type"]) {
            "thread" -> (map["threadId"] as? String)?.let { ChatEvent.Thread(it) }
            "delta" -> (map["text"] as? String)?.let { ChatEvent.Delta(it) }
            "status" -> (map["name"] as? String)?.let { ChatEvent.UsingTool(it) }
            else -> null
        }
    }

    private fun friendlyError(e: Throwable): String = when ((e as? FirebaseFunctionsException)?.code) {
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> e.message ?: "Daily limit reached."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "The coach couldn't reply. Try again."
        else -> "Couldn't reach the coach. Check your connection and try again."
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

    private fun toInsight(d: DocumentSnapshot): CoachInsight? {
        val kind = when (d.getString("kind")) {
            "activity" -> InsightKind.ACTIVITY
            "sleep" -> InsightKind.SLEEP
            "weekly" -> InsightKind.WEEKLY
            else -> return null
        }
        // Server timestamps are null until committed; fall back to "now" for that brief moment.
        val createdAt = (d.get("createdAt") as? Timestamp)?.toInstant() ?: Instant.now()
        @Suppress("UNCHECKED_CAST")
        val highlights = (d.get("highlights") as? List<Map<String, Any?>>).orEmpty().mapNotNull {
            val label = it["label"] as? String ?: return@mapNotNull null
            InsightHighlight(label, it["value"] as? String ?: "")
        }
        val weekly = if (kind == InsightKind.WEEKLY) {
            @Suppress("UNCHECKED_CAST")
            WeeklyDetails(
                summary = d.getString("summary").orEmpty(),
                wins = (d.get("wins") as? List<String>).orEmpty(),
                areas = (d.get("areas") as? List<Map<String, Any?>>).orEmpty().map {
                    WeeklyArea(it["area"] as? String ?: "", it["trend"] as? String ?: "steady", it["note"] as? String ?: "", it["value"] as? String ?: "")
                },
                focus = (d.get("focus") as? List<String>).orEmpty(),
                weekStart = d.getString("weekStart"),
                weekEnd = d.getString("weekEnd"),
                habits = d.getString("habits").orEmpty(),
                days = (d.get("days") as? List<Map<String, Any?>>).orEmpty().map { it["met"] as? Boolean },
                goodDays = d.getLong("goodDays")?.toInt(),
                target = d.getLong("target")?.toInt(),
            )
        } else null
        return CoachInsight(
            id = d.id,
            kind = kind,
            category = d.getString("category"),
            title = d.getString("title") ?: d.getString("headline") ?: return null,
            body = d.getString("body") ?: weekly?.summary.orEmpty(),
            highlights = highlights,
            suggestion = d.getString("suggestion")?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            weekly = weekly,
            seen = d.getBoolean("seen") ?: false,
        )
    }
}
