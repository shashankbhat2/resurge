package com.resurgeai.feature.progress

import com.resurgeai.core.model.ProgressMath
import com.resurgeai.core.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ProgressFormatTest {
    @Test
    fun `changes carry a real minus sign and the user's units`() {
        Locale.setDefault(Locale.UK)
        assertEquals("−3.1 kg", formatChange(-3.1, UnitSystem.METRIC))
        assertEquals("+0.4 kg", formatChange(0.42, UnitSystem.METRIC))
        assertEquals("0.0 kg", formatChange(0.01, UnitSystem.METRIC))
        assertEquals("−2.2 lb", formatChange(-1.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `labels count days in the first week, then weeks`() {
        val start = LocalDate.of(2026, 10, 1)
        assertEquals("Day 1", ProgressMath.label(start, start))
        assertEquals("Day 7", ProgressMath.label(start, start.plusDays(6)))
        assertEquals("Week 2", ProgressMath.label(start, start.plusDays(7)))
        assertEquals("Week 9", ProgressMath.label(start, start.plusDays(60)))
    }
}
