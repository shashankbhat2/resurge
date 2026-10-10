package com.resurgeai.core.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.auth.AuthUser
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes `users/{uid}` in the `resurge` Firestore database. */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun doc(uid: String) = firestore.collection(USERS).document(uid)

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = doc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    /**
     * Keeps identity fields in sync with the Google account. Uses a merge write (no read),
     * so it also works offline and never overwrites goals, targets or onboarding state.
     */
    suspend fun syncIdentity(user: AuthUser) {
        doc(user.uid).set(
            mapOf(
                "displayName" to user.displayName,
                "email" to user.email,
                "photoUrl" to user.photoUrl,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun completeOnboarding(uid: String, goals: Set<Goal>, targets: Targets) {
        doc(uid).set(
            mapOf(
                "goals" to goals.map { it.name },
                "targets" to targets.toMap(),
                "onboardingComplete" to true,
                "onboardedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun updateTargets(uid: String, targets: Targets) {
        doc(uid).update(
            mapOf(
                "targets" to targets.toMap(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun Targets.toMap() = mapOf("calories" to calories, "waterMl" to waterMl)

    private fun DocumentSnapshot.toProfile(): UserProfile {
        @Suppress("UNCHECKED_CAST")
        val targets = get("targets") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val goals = (get("goals") as? List<String>).orEmpty()
            .mapNotNull { name -> Goal.entries.firstOrNull { it.name == name } }
            .toSet()

        return UserProfile(
            uid = id,
            displayName = getString("displayName"),
            email = getString("email"),
            photoUrl = getString("photoUrl"),
            goals = goals,
            targets = Targets(
                calories = (targets?.get("calories") as? Number)?.toInt() ?: Targets.DEFAULT_CALORIES,
                waterMl = (targets?.get("waterMl") as? Number)?.toInt() ?: Targets.DEFAULT_WATER_ML,
            ),
            onboardingComplete = getBoolean("onboardingComplete") ?: false,
        )
    }

    private companion object {
        const val USERS = "users"
    }
}
