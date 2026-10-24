package com.resurgeai.core.data.plan

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WorkoutPlan
import com.resurgeai.core.model.wire
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class PlanGenerationException(message: String) : Exception(message)

/**
 * Training plans:
 *   users/{uid}/plans/{id}  name, summary, days[7] (Monday first), progression, notes, request, status
 * Plans are written only by the generatePlan function; the profile's activePlanId points at the one in use.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    /**
     * Builds a plan for [goal] and [training]. With [change] and [previousPlanId], rewrites that plan
     * with the change applied ("Swap a session").
     */
    suspend fun generate(
        goal: MajorGoal,
        training: TrainingPrefs,
        units: UnitSystem,
        change: String? = null,
        previousPlanId: String? = null,
    ): WorkoutPlan {
        val data = buildMap<String, Any> {
            put("goal", goal.type.wire)
            put("weeks", goal.weeks)
            goal.startKg?.let { put("startKg", it) }
            goal.targetKg?.let { put("targetKg", it) }
            goal.raceKm?.let { put("raceKm", it) }
            put("days", training.days.sorted().map { it.wire })
            put("location", training.location.wire)
            put("sessionMin", training.sessionMin)
            put("experience", training.experience.wire)
            put("style", training.style.wire)
            put("runs", training.runs.wire)
            put("units", units.wire)
            if (!change.isNullOrBlank() && previousPlanId != null) {
                put("change", change.trim().take(200))
                put("previousPlanId", previousPlanId)
            }
        }
        val result = try {
            functions.getHttpsCallable("generatePlan").withTimeout(120, TimeUnit.SECONDS).call(data).await()
        } catch (e: FirebaseFunctionsException) {
            Log.w(TAG, "generatePlan failed: ${e.code} ${e.message}")
            throw PlanGenerationException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> e.message ?: "Daily plan limit reached."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
                    else -> "Couldn't build your plan. Try again."
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "generatePlan call failed", e)
            throw PlanGenerationException("Couldn't reach the server. Check your connection and try again.")
        }
        val map = result.getData() as? Map<*, *>
        val plan = (map?.get("id") as? String)?.let { id -> map.let { WorkoutPlan.fromMap(id, it) } }
        if (plan == null) {
            Log.w(TAG, "generatePlan returned an unusable plan: keys=${map?.keys}")
            throw PlanGenerationException("Couldn't build your plan. Try again.")
        }
        return plan
    }

    private companion object {
        const val TAG = "PlanRepository"
    }

    suspend fun plan(id: String): WorkoutPlan? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = firestore.collection("users").document(uid).collection("plans").document(id).get().await()
        return snap.data?.let { WorkoutPlan.fromMap(snap.id, it) }
    }
}
