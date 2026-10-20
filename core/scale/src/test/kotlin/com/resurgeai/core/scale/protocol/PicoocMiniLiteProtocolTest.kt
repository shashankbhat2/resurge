package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Captures from a physical PICOOC Mini Lite, from openScale's `PicoocBroadcastHandlerTest`. */
class PicoocMiniLiteProtocolTest {

    private val company = 0x49D0
    private val packetA = hex("004B4F1E391F065E13388704009C")
    private val packetB = hex("004B4F1E391F066013748706005C")
    private val packetC = hex("004B4F1E391F0676136087200040")

    private fun ad(payload: ByteArray, name: String? = "PICOOC-L", id: Int = company) =
        Advertisement("D0:49:00:4B:4F:1E", name, manufacturerData = mapOf(id to payload))

    /** Rewrites the checksum so [p] is valid again after an edit. */
    private fun resum(p: ByteArray, id: Int = company): ByteArray {
        val sum = (id and 0xFF) + (id shr 8) + (0 until 13).sumOf { p[it].toInt() and 0xFF }
        return p.also { it[13] = (sum and 0xFF).inv().toByte() }
    }

    @Test
    fun `captured completed measurements are stable with impedance`() {
        val a = PicoocMiniLiteProtocol.decode(ad(packetA))!!
        assertEquals(81.5, a.kg, KG_TOLERANCE)
        assertTrue(a.stable)
        assertEquals(492, a.impedanceOhm)
        assertEquals(81.6, PicoocMiniLiteProtocol.decode(ad(packetB))!!.kg, KG_TOLERANCE)
        val c = PicoocMiniLiteProtocol.decode(ad(packetC))!!
        assertEquals(82.7, c.kg, KG_TOLERANCE)
        assertEquals(496, c.impedanceOhm)
    }

    @Test
    fun `a checksummed non-final state is unstable`() {
        val inProgress = resum(packetC.copyOf().also { it[4] = 0x38 })
        assertFalse(PicoocMiniLiteProtocol.decode(ad(inProgress))!!.stable)
    }

    @Test
    fun `missing impedance is reported as none`() {
        val noOhms = resum(packetC.copyOf().also { it[8] = 0xFF.toByte(); it[9] = 0xFF.toByte() })
        assertNull(PicoocMiniLiteProtocol.decode(ad(noOhms))!!.impedanceOhm)
    }

    @Test
    fun `corrupted, truncated and stale-checksum frames are rejected`() {
        assertNull(PicoocMiniLiteProtocol.decode(ad(packetC.copyOf().also { it[13] = (it[13] + 1).toByte() })))
        assertNull(PicoocMiniLiteProtocol.decode(ad(packetC.copyOf(13))))
        assertNull(PicoocMiniLiteProtocol.decode(ad(packetC.copyOf().also { it[7] = 0x77 })))
    }

    @Test
    fun `does not hardcode the company id or MAC`() {
        val moved = resum(packetC.copyOf().also { it[0] = 0x56; it[1] = 0x78; it[2] = 0x11; it[3] = 0x22 }, id = 0x3412)
        assertEquals(82.7, PicoocMiniLiteProtocol.decode(ad(moved, id = 0x3412))!!.kg, KG_TOLERANCE)
    }

    @Test
    fun `recognised by the exact name and ahead of Scaleup`() {
        assertEquals("PICOOC Mini Lite", PicoocMiniLiteProtocol.recognise(ad(packetC)))
        assertEquals("PICOOC Mini Lite", PicoocMiniLiteProtocol.recognise(ad(packetC, name = "picooc-l")))
        assertNull(PicoocMiniLiteProtocol.recognise(ad(packetC, name = "PICOOC-L1")))
        assertNull(PicoocMiniLiteProtocol.recognise(ad(packetC, name = null)))
        assertSame(PicoocMiniLiteProtocol, ScaleProtocols.match(ad(packetC))!!.protocol)
    }
}
