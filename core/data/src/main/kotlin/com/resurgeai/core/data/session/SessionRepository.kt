package com.resurgeai.core.data.session

import android.util.Log
import com.resurgeai.core.data.auth.AuthRepository
import com.resurgeai.core.data.auth.AuthUser
import com.resurgeai.core.data.coach.DeviceRegistration
import com.resurgeai.core.data.di.ApplicationScope
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Combines Firebase auth state with the user's Firestore profile into a single [SessionState]. */
@Singleton
class SessionRepository @Inject constructor(
    authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val deviceRegistration: DeviceRegistration,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val session: Flow<SessionState> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(SessionState.SignedOut)
        } else {
            // Fire-and-forget: the merge write is queued offline and the snapshot listener picks it up.
            appScope.launch {
                runCatching { userRepository.syncIdentity(user) }
                    .onFailure { Log.w(TAG, "Identity sync failed", it) }
                deviceRegistration.registerCurrentToken()
            }
            userRepository.observeProfile(user.uid)
                .map { profile -> profile ?: user.toFallbackProfile() }
                .map { profile ->
                    if (profile.onboardingComplete) SessionState.Ready(profile)
                    else SessionState.NeedsOnboarding(profile)
                }
                .catch { error ->
                    Log.w(TAG, "Profile listener failed", error)
                    emit(SessionState.NeedsOnboarding(user.toFallbackProfile()))
                }
        }
    }.onStart { emit(SessionState.Loading) }

    private fun AuthUser.toFallbackProfile() = UserProfile(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl,
        goals = emptySet(),
        targets = Targets(),
        onboardingComplete = false,
    )

    private companion object {
        const val TAG = "SessionRepository"
    }
}
