package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TargetsTest {
    @Test
    fun `sleep goals under 7 hours get a gentle note`() {
        assertNotNull(Targets.sleepGoalNote(6 * 60 + 30))
        assertNotNull(Targets.sleepGoalNote(Targets.SLEEP_RANGE.first))
        assertNull(Targets.sleepGoalNote(7 * 60))
        assertNull(Targets.sleepGoalNote(9 * 60))
    }

    @Test
    fun `sleep goals format as hours and minutes`() {
        assertEquals("7h", Targets.formatSleep(420))
        assertEquals("6h 30m", Targets.formatSleep(390))
    }

    @Test
    fun `defaults are 7 hours of sleep and 10,000 steps`() {
        assertEquals(420, Targets().sleepMinutes)
        assertEquals(10_000, Targets().steps)
    }
}
