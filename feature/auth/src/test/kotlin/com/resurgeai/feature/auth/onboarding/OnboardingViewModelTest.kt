package com.resurgeai.feature.auth.onboarding

import com.resurgeai.core.data.plan.PlanGenerationException
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.WorkoutPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val users = mockk<UserRepository>(relaxed = true)
    private val plans = mockk<PlanRepository>()
    private val weights = mockk<WeightRepository> {
        coEvery { latestKg() } returns 84.0
    }
    private val health = mockk<HealthConnectManager>(relaxed = true) {
        every { availability() } returns HealthConnectAvailability.AVAILABLE
        coEvery { grantedPermissions() } returns emptySet()
    }

    private val plan = WorkoutPlan(
        id = "p1",
        name = "Lean 12",
        summary = "",
        days = DayOfWeek.entries.map { PlanDay(it, rest = it !in TrainingPrefs().days, title = "Day") },
        progression = "",
        notes = "",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { plans.generate(any(), any(), any(), any(), any()) } returns plan
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingViewModel(users, plans, weights, health)

    @Test
    fun `cannot leave the goal step without a goal`() {
        val vm = vm()
        vm.next("uid")
        assertEquals(OnboardingStep.Goal, vm.state.value.step)
        vm.selectGoal(TrainingGoal.RUN_FURTHER)
        vm.next("uid")
        assertEquals(OnboardingStep.Target, vm.state.value.step)
    }

    @Test
    fun `weight goals start from the latest weigh-in and must move the right way`() {
        val vm = vm()
        vm.selectGoal(TrainingGoal.LOSE_FAT)
        assertEquals(84.0, vm.state.value.startKg, 0.0)
        assertEquals(78.0, vm.state.value.targetKg, 0.0)
        assertTrue(vm.state.value.targetValid)

        repeat(61) { vm.stepTarget(1) } // 78 -> 84.1 in tenths, above the start
        assertFalse(vm.state.value.targetValid)

        vm.selectGoal(TrainingGoal.BUILD_MUSCLE)
        assertEquals(87.0, vm.state.value.targetKg, 0.0)
        assertTrue(vm.state.value.targetValid)
    }

    @Test
    fun `custom duration steps by weeks, then months`() {
        val vm = vm()
        vm.useCustomWeeks()
        assertTrue(vm.state.value.customWeeks)
        vm.stepWeeks(1) // 12 -> 3 months
        assertEquals(13, vm.state.value.weeks)
        assertEquals("3 months", MajorGoal.durationLabel(13))
        vm.stepWeeks(1)
        assertEquals("4 months", MajorGoal.durationLabel(vm.state.value.weeks))
        vm.stepWeeks(-100)
        assertEquals(MajorGoal.DURATIONS.first(), vm.state.value.weeks)
        vm.setWeeks(12)
        assertFalse(vm.state.value.customWeeks)
    }

    @Test
    fun `days stay between two and six`() {
        val vm = vm()
        val days = vm.state.value.training.days.toList()
        vm.toggleDay(days[0]); vm.toggleDay(days[1]); vm.toggleDay(days[2])
        assertFalse(vm.state.value.training.validDays)
        DayOfWeek.entries.forEach { if (it !in vm.state.value.training.days) vm.toggleDay(it) }
        assertEquals(TrainingPrefs.DAYS_RANGE.last, vm.state.value.training.days.size)
    }

    @Test
    fun `reaching the plan step builds the plan once and keeps it until answers change`() {
        val vm = vm()
        vm.selectGoal(TrainingGoal.LOSE_FAT)
        repeat(3) { vm.next("uid") } // -> Plan
        assertEquals(OnboardingStep.Plan, vm.state.value.step)
        assertEquals("p1", vm.state.value.plan?.id)
        // Named in plain words from the answers, not by the plan service.
        assertEquals("Lose fat · 4 days a week", vm.state.value.plan?.name)
        coVerify(exactly = 1) { plans.generate(any(), any(), any(), null, null) }

        vm.back(); vm.next("uid") // same answers: no rebuild
        coVerify(exactly = 1) { plans.generate(any(), any(), any(), any(), any()) }

        vm.back(); vm.setSessionMin(30); vm.next("uid") // changed: rebuild
        coVerify(exactly = 2) { plans.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a change request rebuilds from the current plan`() {
        val vm = vm()
        vm.selectGoal(TrainingGoal.GET_IN_SHAPE)
        repeat(3) { vm.next("uid") }
        vm.buildPlan("No running")
        coVerify { plans.generate(any(), any(), any(), "No running", "p1") }
    }

    @Test
    fun `plan failure shows an error and blocks continuing`() {
        coEvery { plans.generate(any(), any(), any(), any(), any()) } throws PlanGenerationException("Couldn't build your plan. Try again.")
        val vm = vm()
        vm.selectGoal(TrainingGoal.RUN_FURTHER)
        repeat(3) { vm.next("uid") }
        assertEquals("Couldn't build your plan. Try again.", vm.state.value.planError)
        assertNull(vm.state.value.plan)
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `finishing saves the goal, schedule, plan and day goals`() = runTest {
        val goal = slot<MajorGoal>()
        val vm = vm()
        vm.selectGoal(TrainingGoal.LOSE_FAT)
        vm.setWeeks(16)
        repeat(3) { vm.next("uid") } // -> Plan
        vm.next("uid") // -> Day
        vm.setDayGoals(vm.state.value.dayGoals.copy(water = true, weeklyDays = 9))
        assertEquals(DayGoals.WEEKLY_RANGE.last, vm.state.value.dayGoals.weeklyDays)
        vm.next("uid") // -> Ready
        vm.next("uid") // save

        coVerify {
            users.completeOnboarding(
                "uid", capture(goal), TrainingPrefs(), "p1", Targets(steps = OnboardingViewModel.REST_DAY_STEPS),
                DayGoals(steps = false, meals = 2, water = true, weeklyDays = 7),
            )
        }
        assertEquals(TrainingGoal.LOSE_FAT, goal.captured.type)
        assertEquals(16, goal.captured.weeks)
        assertEquals(84.0, goal.captured.startKg!!, 0.0)
        assertEquals(78.0, goal.captured.targetKg!!, 0.0)
    }

    @Test
    fun `save failure surfaces an error and re-enables the button`() = runTest {
        coEvery { users.completeOnboarding(any(), any(), any(), any(), any(), any()) } throws IllegalStateException("offline")
        val vm = vm()
        vm.selectGoal(TrainingGoal.RUN_FURTHER)
        repeat(6) { vm.next("uid") }
        assertEquals("offline", vm.state.value.error)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `reports health connected when any read permission is granted`() {
        coEvery { health.grantedPermissions() } returns setOf("android.permission.health.READ_STEPS")
        assertTrue(vm().state.value.healthConnected)
    }
}
