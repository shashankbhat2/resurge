package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeighSessionTest {

    private fun frame(kg: Double, stable: Boolean = false, impedance: Int? = null) =
        ScaleFrame(kg = kg, stable = stable, impedanceOhm = impedance)

    @Test
    fun `stable flag settles once`() {
        val s = WeighSession(reportsStability = true)
        assertNull(s.offer(frame(70.4), 0))
        assertNull(s.offer(frame(70.2), 500))
        val r = s.offer(frame(70.234, stable = true, impedance = 500), 1_000)!!
        assertEquals(70.23, r.kg, 1e-9)
        assertEquals(500, r.impedanceOhm)
        assertEquals(1_000L, r.atMs)
    }

    @Test
    fun `repeated final broadcast does not log twice`() {
        val s = WeighSession(reportsStability = true)
        assertNotNull(s.offer(frame(70.2, stable = true), 0))
        for (t in 1..20) assertNull(s.offer(frame(70.2, stable = true), t * 500L))
    }

    @Test
    fun `idle longer than twenty seconds allows a new reading`() {
        val s = WeighSession(reportsStability = true)
        assertNotNull(s.offer(frame(70.2, stable = true), 0))
        assertNull(s.offer(frame(70.2, stable = true), WeighSession.IDLE_MS))
        assertNotNull(s.offer(frame(70.3, stable = true), 2 * WeighSession.IDLE_MS + 1))
    }

    @Test
    fun `a clearly different person re-arms the session`() {
        val s = WeighSession(reportsStability = true)
        assertNotNull(s.offer(frame(70.2, stable = true), 0))
        assertNull(s.offer(frame(71.5, stable = true), 1_000))
        val r = s.offer(frame(82.0, stable = true), 2_000)
        assertEquals(82.0, r!!.kg, 1e-9)
    }

    @Test
    fun `without a stability flag the weight settles after four repeats over a second and a half`() {
        val s = WeighSession(reportsStability = false)
        assertNull(s.offer(frame(70.20), 0))
        assertNull(s.offer(frame(70.22), 500))
        assertNull(s.offer(frame(70.21), 1_000))
        assertNull(s.offer(frame(70.20), 1_400))
        val r = s.offer(frame(70.20), 1_500)
        assertEquals(70.2, r!!.kg, 1e-9)
        assertNull(s.offer(frame(70.20), 2_000))
    }

    @Test
    fun `without a stability flag four quick repeats are not enough`() {
        val s = WeighSession(reportsStability = false)
        for (t in 0..3) assertNull(s.offer(frame(70.2), t * 100L))
    }

    @Test
    fun `without a stability flag a moving weight restarts the count`() {
        val s = WeighSession(reportsStability = false)
        assertNull(s.offer(frame(70.0), 0))
        assertNull(s.offer(frame(70.0), 500))
        assertNull(s.offer(frame(70.0), 1_000))
        assertNull(s.offer(frame(70.5), 1_500))
        assertNull(s.offer(frame(70.5), 2_000))
    }

    @Test
    fun `stable flag is ignored when the protocol does not report stability`() {
        val s = WeighSession(reportsStability = false)
        assertNull(s.offer(frame(70.2, stable = true), 0))
    }
}
