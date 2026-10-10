package com.resurgeai.feature.coach

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class CoachViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(tab: String? = null) = CoachViewModel(SavedStateHandle(mapOf(CoachViewModel.ARG_TAB to tab)))

    @Test
    fun `opens on the requested tab`() {
        assertEquals(CoachTab.CHAT, vm("chat").state.value.tab)
        assertEquals(CoachTab.INSIGHTS, vm("insights").state.value.tab)
        assertEquals(CoachTab.INSIGHTS, vm(null).state.value.tab)
    }

    @Test
    fun `sending from insights jumps to chat and gets a reply`() = runTest(dispatcher) {
        val vm = vm("insights")
        vm.send("  Why am I tired?  ")
        assertEquals(CoachTab.CHAT, vm.state.value.tab)
        assertEquals("Why am I tired?", vm.state.value.messages.single().text)
        assertTrue(vm.state.value.replying)

        advanceTimeBy(CoachViewModel.REPLY_DELAY_MS + 1)
        assertFalse(vm.state.value.replying)
        assertEquals(Author.COACH, vm.state.value.messages.last().author)
    }

    @Test
    fun `blank messages are ignored`() {
        val vm = vm()
        vm.send("   ")
        assertTrue(vm.state.value.messages.isEmpty())
    }
}
