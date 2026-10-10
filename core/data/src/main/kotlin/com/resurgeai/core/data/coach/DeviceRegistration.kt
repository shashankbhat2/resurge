package com.resurgeai.core.data.coach

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Stores this device's FCM token at users/{uid}/devices/{token} so insight notifications reach it. */
@Singleton
class DeviceRegistration @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    suspend fun registerCurrentToken() {
        runCatching { register(FirebaseMessaging.getInstance().token.await()) }
            .onFailure { Log.w(TAG, "FCM token registration failed", it) }
    }

    suspend fun register(token: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).collection("devices").document(token)
            .set(mapOf("platform" to "android", "updatedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    private companion object {
        const val TAG = "DeviceRegistration"
    }
}
