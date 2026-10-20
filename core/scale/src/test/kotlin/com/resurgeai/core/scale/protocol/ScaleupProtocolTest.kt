package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frames built to the layout in openScale's `ScaleupHandler`; 75.50 kg is 0x1D7E. */
class ScaleupProtocolTest {

    private fun record(lsb: Int, impedance: Int = 0) =
        bytes(lsb, impedance shr 8, impedance and 0xFF, 1, 2, 3, 4, 5, 6)

    private fun ad(company: Int, data: ByteArray, name: String? = null) =
        Advertisement("01:02:03:04:05:06", name, manufacturerData = mapOf(company to data))

    @Test
    fun `stabilised frame is stable`() {
        val f = ScaleupProtocol.decode(ad(0x1DE0, record(0x7E, impedance = 520)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `measuring frame is unstable`() {
        val f = ScaleupProtocol.decode(ad(0x1DD0, record(0x7E)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
    }

    @Test
    fun `short, unflagged and zero-weight frames are rejected`() {
        assertNull(ScaleupProtocol.decode(ad(0x1DD0, ByteArray(8))))
        assertNull(ScaleupProtocol.decode(ad(0x1DA0, record(0x7E))))
        assertNull(ScaleupProtocol.decode(ad(0x00D0, record(0x00))))
    }

    @Test
    fun `recognised by name or flag byte and kept behind PICOOC`() {
        assertEquals("Scale Up", ScaleupProtocol.recognise(ad(0x1DD0, ByteArray(9))))
        assertEquals("Scale Up", ScaleupProtocol.recognise(Advertisement("01:02:03:04:05:06", "da")))
        assertNull(ScaleupProtocol.recognise(ad(0x1DA0, ByteArray(9), name = "Other")))
        assertSame(ScaleupProtocol, ScaleProtocols.all.last())
    }
}
