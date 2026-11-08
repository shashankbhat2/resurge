package com.resurgeai.core.data.auth

import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.resurgeai.core.data.cardio.CardioPointsCache
import com.resurgeai.core.data.cardio.CardioPointsRepository
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AuthRepositoryTest {
    private val firebaseAuth = mockk<FirebaseAuth>()
    private val credentials = mockk<CredentialManager>()
    private val cache = mockk<CardioPointsCache>()
    private val user = mockk<FirebaseUser>()
    private var signedOut = false
    private val repository get() = AuthRepository(firebaseAuth, credentials, cache, "test-client-id")

    @Before fun setup() {
        every { user.uid } returns "previous-user"
        every { firebaseAuth.currentUser } answers { if (signedOut) null else user }
        every { firebaseAuth.signOut() } answers { signedOut = true }
        coEvery { cache.clearForUser(any()) } just Runs
        coEvery { credentials.clearCredentialState(any()) } just Runs
    }

    @Test fun signOutClearsThePreviousUsersCacheAndCredentials() = runTest {
        repository.signOut()
        assertTrue(signedOut)
        coVerify(exactly = 1) { cache.clearForUser("previous-user") }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }

    @Test fun cacheFailureDoesNotSkipCredentialCleanup() = runTest {
        coEvery { cache.clearForUser(any()) } throws IOException("Cache storage unavailable")
        repository.signOut()
        assertTrue(signedOut)
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }

    @Test fun noCurrentUserStillClearsCredentials() = runTest {
        every { firebaseAuth.currentUser } returns null
        repository.signOut()
        assertTrue(signedOut)
        coVerify(exactly = 0) { cache.clearForUser(any()) }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }

    @Test fun credentialFailureDoesNotUndoSignOutOrCacheCleanup() = runTest {
        coEvery { credentials.clearCredentialState(any()) } throws IllegalStateException("Credential provider unavailable")
        repository.signOut()
        assertTrue(signedOut)
        coVerify(exactly = 1) { cache.clearForUser("previous-user") }
    }

    @Test fun cacheCancellationPropagatesAfterCredentialCleanup() = runTest {
        val cancellation = CancellationException("Cache operation cancelled")
        coEvery { cache.clearForUser(any()) } throws cancellation
        try {
            repository.signOut()
            throw AssertionError("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }

    @Test fun callerCancellationAfterFirebaseSignOutDoesNotInterruptCleanup() = runTest {
        val signOut = launch(start = CoroutineStart.LAZY) { repository.signOut() }
        every { firebaseAuth.signOut() } answers {
            signedOut = true
            signOut.cancel()
        }
        coEvery { cache.clearForUser(any()) } coAnswers { yield() }
        coEvery { credentials.clearCredentialState(any()) } coAnswers { yield() }
        signOut.start()
        signOut.join()
        assertTrue(signedOut)
        assertTrue(signOut.isCancelled)
        coVerify(exactly = 1) { cache.clearForUser("previous-user") }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cacheFailureInTheAccountListenerDoesNotFailSignOut() = runTest {
        val listener = slot<FirebaseAuth.AuthStateListener>()
        every { firebaseAuth.addAuthStateListener(capture(listener)) } just Runs
        val points = CardioPointsRepository(mockk(), mockk(), cache, firebaseAuth, backgroundScope)
        every { firebaseAuth.signOut() } answers {
            signedOut = true
            listener.captured.onAuthStateChanged(firebaseAuth)
        }
        coEvery { cache.clearForUser(any()) } throws IOException("Cache storage unavailable")
        repository.signOut()
        runCurrent()
        assertTrue(signedOut)
        assertTrue(points.state.value.days.isEmpty())
        coVerify(exactly = 2) { cache.clearForUser("previous-user") }
        coVerify(exactly = 1) { credentials.clearCredentialState(any()) }
    }
}
