package com.resurgeai.core.designsystem

import com.resurgeai.core.designsystem.components.TypedNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class TypedNumbersTest {
    @Test
    fun `decimals accept a comma or a point`() {
        assertEquals(72.4, TypedNumbers.parse("72.4")!!, 1e-9)
        assertEquals(72.4, TypedNumbers.parse(" 72,4 ")!!, 1e-9)
        assertNull(TypedNumbers.parse("abc"))
        assertEquals(724, TypedNumbers.parseTenths("72.4"))
        assertEquals("72.4", TypedNumbers.tenths(724))
    }

    @Test
    fun `whole numbers read commas as thousands`() {
        assertEquals(2000, TypedNumbers.parseInt("2,000"))
        assertEquals(10500, TypedNumbers.parseInt("10 500"))
        assertNull(TypedNumbers.parseInt(""))
    }

    @Test
    fun `clock times in the common forms`() {
        assertEquals(LocalTime.of(21, 30), TypedNumbers.parseClock("21:30"))
        assertEquals(LocalTime.of(21, 30), TypedNumbers.parseClock("21.30"))
        assertEquals(LocalTime.of(21, 30), TypedNumbers.parseClock("2130"))
        assertEquals(LocalTime.of(9, 30), TypedNumbers.parseClock("930"))
        assertEquals(LocalTime.of(9, 0), TypedNumbers.parseClock("9"))
        assertNull(TypedNumbers.parseClock("25:00"))
        assertNull(TypedNumbers.parseClock("12:75"))
        assertNull(TypedNumbers.parseClock("12345"))
    }
}
