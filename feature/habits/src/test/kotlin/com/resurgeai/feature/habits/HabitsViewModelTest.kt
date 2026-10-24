package com.resurgeai.feature.habits

import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStatus
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    private val settings = HabitSettings()
    private val today = HabitStreaks.habitDay(Instant.now(), ZoneId.systemDefault(), settings.resetTime)
    private val profile = UserProfile("u", "Sam", null, null, setOf(Goal.MOVE_MORE), Targets(), true, settings)
    private fun habit(id: String, order: Int) = Habit(id, "Habit $id", "flame", "accent", order, false, today.minusDays(10))

    private val habits = MutableStateFlow(listOf(habit("a", 0), habit("b", 1)))
    private val days = MutableStateFlow<Map<LocalDate, Map<String, HabitStatus>>>(emptyMap())
    private val repo = mockk<HabitsRepository>(relaxed = true) {
        every { habits() } returns this@HabitsViewModelTest.habits
        every { days(any()) } returns this@HabitsViewModelTest.days
    }
    private val session = mockk<SessionRepository> { every { session } returns flowOf(SessionState.Ready(profile)) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = HabitsViewModel(session, repo)

    @Test
    fun `pending habits show the deck until all are logged`() {
        val vm = vm()
        assertEquals(listOf("a", "b"), vm.state.value.pending.map { it.habit.id })
        assertTrue(vm.state.value.showDeck)

        days.value = mapOf(today to mapOf("a" to HabitStatus.DONE, "b" to HabitStatus.MISSED))
        assertFalse(vm.state.value.showDeck)
        assertEquals(1, vm.state.value.doneToday)
    }

    @Test
    fun `swipe logs today and undo clears it`() {
        val vm = vm()
        vm.swipe("a", done = true)
        verify { repo.setStatus(today, "a", HabitStatus.DONE) }
        days.value = mapOf(today to mapOf("a" to HabitStatus.DONE))
        assertEquals("a", vm.state.value.lastSwiped)

        vm.undo()
        verify { repo.setStatus(today, "a", null) }
        assertNull(vm.state.value.lastSwiped)
    }

    @Test
    fun `days in the last week can be flipped, older ones can't`() {
        val vm = vm()
        vm.toggleDay("a", today.minusDays(1))
        verify { repo.setStatus(today.minusDays(1), "a", HabitStatus.DONE) }
        vm.toggleDay("a", today.minusDays(6))
        verify { repo.setStatus(today.minusDays(6), "a", HabitStatus.DONE) }
        vm.toggleDay("a", today.minusDays(7))
        verify(exactly = 0) { repo.setStatus(today.minusDays(7), any(), any()) }
    }

    @Test
    fun `the last active habit cannot be archived`() {
        habits.value = listOf(habit("a", 0))
        val vm = vm()
        assertFalse(vm.archive("a"))
        verify(exactly = 0) { repo.setArchived(any(), any()) }
    }

    @Test
    fun `adding stops at eight active habits`() {
        habits.value = (0 until 8).map { habit("h$it", it) }
        val vm = vm()
        assertFalse(vm.state.value.canAdd)
        vm.save(null, HabitDraft("Ninth", "flame", "accent")) {}
        coVerify(exactly = 0) { repo.add(any(), any(), any()) }
    }

    @Test
    fun `computed streaks are cached on the habit once`() {
        days.value = mapOf(today.minusDays(1) to mapOf("a" to HabitStatus.DONE))
        vm()
        verify(exactly = 1) { repo.cacheStreak("a", 1, 1) }
    }
}
