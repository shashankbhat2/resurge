package com.resurgeai.feature.auth.onboarding

import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.Targets
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val health = mockk<HealthConnectManager>(relaxed = true) {
        every { availability() } returns HealthConnectAvailability.AVAILABLE
        coEvery { grantedPermissions() } returns emptySet()
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(users, health)

    @Test
    fun `cannot leave goals step without a goal`() {
        val vm = vm()
        assertFalse(vm.state.value.canContinue)
        vm.next("uid")
        assertEquals(OnboardingStep.Goals, vm.state.value.step)

        vm.toggleGoal(Goal.SLEEP_BETTER)
        assertTrue(vm.state.value.canContinue)
        vm.next("uid")
        assertEquals(OnboardingStep.Targets, vm.state.value.step)
    }

    @Test
    fun `toggling a goal twice removes it`() {
        val vm = vm()
        vm.toggleGoal(Goal.HYDRATE)
        vm.toggleGoal(Goal.HYDRATE)
        assertTrue(vm.state.value.goals.isEmpty())
    }

    @Test
    fun `back from first step is a no-op`() {
        val vm = vm()
        vm.back()
        assertEquals(OnboardingStep.Goals, vm.state.value.step)
    }

    @Test
    fun `finishing saves goals and targets`() = runTest {
        val vm = vm()
        vm.toggleGoal(Goal.MOVE_MORE)
        vm.next("uid") // -> Targets
        vm.setCalories(2200)
        vm.setWater(3000)
        vm.next("uid") // -> Health
        assertEquals(OnboardingStep.Health, vm.state.value.step)
        assertTrue("health step is skippable", vm.state.value.canContinue)
        vm.next("uid") // -> Ready
        vm.next("uid") // save

        coVerify { users.completeOnboarding("uid", setOf(Goal.MOVE_MORE), Targets(2200, 3000)) }
    }

    @Test
    fun `save failure surfaces an error and re-enables the button`() = runTest {
        coEvery { users.completeOnboarding(any(), any(), any()) } throws IllegalStateException("offline")
        val vm = vm()
        vm.toggleGoal(Goal.MOVE_MORE)
        repeat(4) { vm.next("uid") }

        assertEquals("offline", vm.state.value.error)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `reports health connected when any read permission is granted`() {
        coEvery { health.grantedPermissions() } returns setOf("android.permission.health.READ_STEPS")
        val vm = vm()
        assertTrue(vm.state.value.healthConnected)
    }
}
