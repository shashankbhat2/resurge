package com.resurgeai.feature.coach

import androidx.lifecycle.SavedStateHandle
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.model.ChatEvent
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.TurnRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {
    private val turns = MutableStateFlow<List<CoachTurn>>(emptyList())
    private val stream = MutableSharedFlow<ChatEvent>()
    private val repo = mockk<CoachRepository>(relaxed = true) {
        every { insights(any()) } returns flowOf(emptyList())
        every { threads(any()) } returns flowOf(emptyList())
        every { turns(any()) } returns turns
        every { send(any(), any(), any()) } returns stream
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(tab: String? = null) = CoachViewModel(SavedStateHandle(mapOf(CoachViewModel.ARG_TAB to tab)), repo)

    @Test
    fun `opens on the requested tab`() {
        assertEquals(CoachTab.CHAT, vm("chat").state.value.tab)
        assertEquals(CoachTab.INSIGHTS, vm("insights").state.value.tab)
        assertEquals(CoachTab.INSIGHTS, vm(null).state.value.tab)
    }

    @Test
    fun `sending streams text, then hands over to the saved turns`() = runTest {
        val vm = vm("insights")
        vm.selectModel(CoachModel.SMART)
        vm.send("  Why am I tired?  ")
        verify { repo.send(null, "Why am I tired?", CoachModel.SMART) }
        assertEquals(CoachTab.CHAT, vm.state.value.tab)
        assertEquals("Why am I tired?", vm.state.value.pending?.userText)

        stream.emit(ChatEvent.Thread("t1"))
        assertEquals("t1", vm.state.value.threadId)
        stream.emit(ChatEvent.Delta("You slept "))
        stream.emit(ChatEvent.Delta("6h."))
        assertEquals("You slept 6h.", vm.state.value.pending?.streamedText)

        // A saved tool round arrives mid-stream: still hidden while pending.
        turns.value = listOf(CoachTurn("0", TurnRole.USER, "Why am I tired?", null, false))
        assertTrue(vm.state.value.visibleTurns.isEmpty())

        stream.emit(ChatEvent.Done("t1", "You slept 6h.", refused = false))
        turns.value = turns.value + CoachTurn("1", TurnRole.COACH, "You slept 6h.", CoachModel.SMART, false)
        assertNull(vm.state.value.pending)
        assertEquals(2, vm.state.value.visibleTurns.size)
    }

    @Test
    fun `failures surface an error and clear the pending reply`() = runTest {
        val vm = vm("chat")
        vm.send("Hi")
        stream.emit(ChatEvent.Failed("Daily limit reached."))
        assertNull(vm.state.value.pending)
        assertEquals("Daily limit reached.", vm.state.value.error)
    }

    @Test
    fun `blank messages are ignored`() {
        val vm = vm()
        vm.send("   ")
        verify(exactly = 0) { repo.send(any(), any(), any()) }
    }

    @Test
    fun `opens the most recent thread on load`() {
        every { repo.threads(any()) } returns flowOf(listOf(CoachThread("recent", "Sleep", null, null)))
        assertEquals("recent", vm("chat").state.value.threadId)
    }
}
