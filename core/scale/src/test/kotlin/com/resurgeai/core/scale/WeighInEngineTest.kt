package com.resurgeai.core.scale

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeighInEngineTest {
    @Test
    fun `a normal day-to-day swing is yours`() {
        assertFalse(WeighInEngine.isUnusual(kg = 71.2, recentKg = 72.4))
        assertFalse(WeighInEngine.isUnusual(kg = 75.3, recentKg = 72.4))
    }

    @Test
    fun `small people get a 3 kg floor`() {
        assertFalse(WeighInEngine.isUnusual(kg = 47.5, recentKg = 45.0))
        assertTrue(WeighInEngine.isUnusual(kg = 48.5, recentKg = 45.0))
    }

    @Test
    fun `heavier people get 5 percent`() {
        assertFalse(WeighInEngine.isUnusual(kg = 125.5, recentKg = 120.0))
        assertTrue(WeighInEngine.isUnusual(kg = 127.0, recentKg = 120.0))
    }

    @Test
    fun `someone else on a shared scale is unusual`() {
        assertTrue(WeighInEngine.isUnusual(kg = 58.0, recentKg = 82.0))
    }
}
