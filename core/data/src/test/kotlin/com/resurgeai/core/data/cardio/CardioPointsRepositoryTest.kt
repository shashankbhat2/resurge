package com.resurgeai.core.data.cardio

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.resurgeai.core.healthconnect.CardioHealthDataSource
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.model.*
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CardioPointsRepositoryTest {
    private val health = mockk<CardioHealthDataSource>()
    private val manager = mockk<HealthConnectManager>()
    private val cache = mockk<CardioPointsCache>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val steps = HealthPermission.getReadPermission(StepsRecord::class)
    private val exercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val at = today.atStartOfDay(zone).toInstant()
    private val workout = Workout("manual", WorkoutSource.MANUAL, "cycle", null, at.minusSeconds(1200), at, WorkoutMetricsInput())

    private fun setup() {
        every { auth.currentUser } returns user
        every { user.uid } returns "a"
        every { auth.addAuthStateListener(any()) } just Runs
        every { manager.availability() } returns HealthConnectAvailability.AVAILABLE
        coEvery { manager.grantedPermissions() } returns setOf(steps, exercise)
        coEvery { health.evidence(any(), any()) } returns emptyList()
        coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(emptyList(), null)
        coEvery { cache.save(any(), any(), any(), any(), any()) } just Runs
        coEvery { cache.clearForUser(any()) } just Runs
    }
    private fun TestScope.repository() = CardioPointsRepository(health, manager, cache, auth, backgroundScope)

    @Test fun `cache-only loads preserve stale and limited quality without reading sensors`() = runTest {
        for (quality in listOf(CardioStatus.READY, CardioStatus.LIMITED_DATA, CardioStatus.STALE)) {
            setup()
            val summary = DailyCardioPoints(today,
                listOf(CardioContribution(CardioMethod.CADENCE, "Watch", null, null, 60_000, 60_000)),
                status = quality, calculatedAt = Instant.EPOCH)
            coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(listOf(CachedCardioDay(summary, "old")), today)
            val repo = repository()
            repo.refresh(emptyList(), workoutReadComplete = false, cacheOnly = true)
            assertEquals(quality, repo.state.value.status)
            assertEquals(summary, repo.state.value.days.single())
            assertFalse(repo.state.value.loading)
            assertFalse(repo.state.value.refreshing)
        }
        coVerify(exactly = 0) { health.evidence(any(), any()) }
        coVerify(exactly = 0) { cache.save(any(), any(), any(), any(), any()) }
    }

    @Test fun `cache quality does not hide the permission-required state`() = runTest {
        setup()
        coEvery { manager.grantedPermissions() } returns emptySet()
        val summary = DailyCardioPoints(today, status = CardioStatus.LIMITED_DATA, calculatedAt = Instant.EPOCH)
        coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(listOf(CachedCardioDay(summary, "old")), today)
        val repo = repository()
        repo.refresh(emptyList(), cacheOnly = true)
        assertEquals(CardioStatus.PERMISSION_REQUIRED, repo.state.value.status)
    }

    @Test fun `successful full refresh replaces stale cached quality`() = runTest {
        setup()
        val summary = DailyCardioPoints(today, status = CardioStatus.STALE, calculatedAt = Instant.EPOCH)
        coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(listOf(CachedCardioDay(summary, "old")), today)
        val repo = repository()
        repo.refresh(emptyList(), force = true)
        assertEquals(CardioStatus.LIMITED_DATA, repo.state.value.status)
        assertTrue(repo.state.value.days.none { it.status == CardioStatus.STALE })
    }

    @Test fun `manual-only scoring works without Health Connect and no evidence is read`() = runTest {
        setup()
        every { manager.availability() } returns HealthConnectAvailability.NOT_SUPPORTED
        coEvery { manager.grantedPermissions() } returns emptySet()
        val repo = repository()
        repo.refresh(listOf(workout))
        assertEquals(CardioStatus.UNAVAILABLE, repo.state.value.status)
        assertEquals(40L, repo.state.value.days.sumOf { it.points })
        coVerify(exactly = 0) { health.evidence(any(), any()) }
        assertEquals(30, repo.state.value.days.size)
    }
    @Test fun `revoked permissions ignore imported workouts and preserve manual estimates`() = runTest {
        setup()
        coEvery { manager.grantedPermissions() } returns emptySet()
        val repo = repository()
        repo.refresh(listOf(workout, workout.copy(id = "imported", source = WorkoutSource.HEALTH_CONNECT, start = at.minusSeconds(2400), end = at.minusSeconds(1200))))
        assertEquals(CardioStatus.PERMISSION_REQUIRED, repo.state.value.status)
        assertEquals(40L, repo.state.value.days.sumOf { it.points })
        coVerify { cache.load("a", zone.id, "AVAILABLE:false:false", any(), any()) }
    }
    @Test fun `read failure preserves cached points and marks them stale`() = runTest {
        setup()
        val old = DailyCardioPoints(today, listOf(CardioContribution(CardioMethod.CADENCE, "Watch", null, null, 60_000, 60_000)), calculatedAt = Instant.EPOCH)
        coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(listOf(CachedCardioDay(old, "old")), today)
        coEvery { health.evidence(any(), any()) } throws IllegalStateException("offline")
        val repo = repository()
        repo.refresh(emptyList())
        assertEquals(CardioStatus.STALE, repo.state.value.status)
        val cached = repo.state.value.days.single { it.date == today }
        assertEquals(1L, cached.points)
        assertEquals(Instant.EPOCH, cached.calculatedAt)
    }
    @Test fun `normal resume refreshes three days while editing older workout refreshes affected days`() = runTest {
        setup()
        val entries = (0L..29L).map { offset ->
            val date = today.minusDays(offset)
            CachedCardioDay(DailyCardioPoints(date, calculatedAt = Instant.EPOCH), workoutFingerprint(emptyList(), date, zone))
        }
        coEvery { cache.load(any(), any(), any(), any(), any()) } returns CardioCacheSnapshot(entries, today)
        val repo = repository()
        repo.refresh(emptyList())
        coVerify { health.evidence(today.minusDays(2).atStartOfDay(zone).toInstant(), any()) }
        val edited = workout.copy(start = today.minusDays(10).atStartOfDay(zone).toInstant(), end = today.minusDays(10).atStartOfDay(zone).toInstant().plusSeconds(1200))
        repo.refresh(listOf(edited))
        assertEquals(40L, repo.state.value.days.single { it.date == today.minusDays(10) }.points)
    }
    @Test fun `cancellation is propagated and never persisted as a successful score`() = runTest {
        setup()
        coEvery { health.evidence(any(), any()) } throws CancellationException("cancelled")
        val repo = repository()
        try { repo.refresh(emptyList()); fail("Must propagate cancellation") } catch (_: CancellationException) { }
        coVerify(exactly = 0) { cache.save(any(), any(), any(), any(), any()) }
    }
    @Test fun `account change cancels old read and clears previous user cache`() = runTest {
        setup()
        val listener = slot<FirebaseAuth.AuthStateListener>()
        every { auth.addAuthStateListener(capture(listener)) } just Runs
        coEvery { health.evidence(any(), any()) } coAnswers { awaitCancellation() }
        val repo = repository()
        val read = async { repo.refresh(emptyList()) }
        runCurrent()
        every { auth.currentUser } returns null
        listener.captured.onAuthStateChanged(auth)
        runCurrent()
        assertTrue(read.isCancelled)
        assertTrue(repo.state.value.days.isEmpty())
        coVerify { cache.clearForUser("a") }
    }
}
