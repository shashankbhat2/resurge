package com.resurgeai.core.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.resurgeai.core.data.di.WebClientId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)

sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class Failure(val message: String) : SignInResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @param:WebClientId private val webClientId: String,
) {
    val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(
                auth.currentUser?.let {
                    AuthUser(it.uid, it.displayName, it.email, it.photoUrl?.toString())
                },
            )
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    /**
     * Shows the Sign in with Google sheet, then exchanges the Google ID token for a Firebase session.
     * [activityContext] must be an Activity context; it is only used for the duration of this call.
     */
    suspend fun signInWithGoogle(activityContext: Context): SignInResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (e: GetCredentialCancellationException) {
            return SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            return SignInResult.Failure(e.message ?: "Couldn't reach Google sign-in.")
        }

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return SignInResult.Failure("Unexpected credential type.")
        }

        return try {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            SignInResult.Success
        } catch (e: Exception) {
            SignInResult.Failure(e.message ?: "Sign-in failed. Please try again.")
        }
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
