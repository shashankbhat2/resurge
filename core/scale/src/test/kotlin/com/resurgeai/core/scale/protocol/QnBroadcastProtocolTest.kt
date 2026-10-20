package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frames captured from a FITINDEX FT-26R-W, from openScale's `QNHandlerBroadcastFT26RTest`. */
class QnBroadcastProtocolTest {

    private val moving1 = "aabb00005e0053016d000000ffffff1400671b510e032100"
    private val moving2 = "aabb00005e0053016e000000ffffff14007b1b510e032300"
    private val settled1 = "aabb00005e00530172000000ffffff1500761b510e032300"
    private val settled2 = "aabb00005e0053017b000000ffffff1500761b510e032300"
    private val moving3 = "aabb00005e005301ef000000ffffff14007b1b510e034900"
    private val settled3 = "aabb00005e005301f0000000ffffff15007b1b510e034400"
    private val idle = "aabb00005e00530100f09f6affffff15a00000510e03500f"

    private fun ad(data: ByteArray, company: Int = 0xFFFF, name: String? = null) =
        Advertisement("00:00:5E:00:53:01", name, manufacturerData = mapOf(company to data))

    private fun decode(frame: String) = QnBroadcastProtocol.decode(ad(hex(frame)))

    @Test
    fun `settled FT-26R frames are stable at the displayed weight`() {
        assertEquals(70.30, decode(settled1)!!.kg, KG_TOLERANCE)
        assertEquals(70.30, decode(settled2)!!.kg, KG_TOLERANCE)
        assertEquals(70.35, decode(settled3)!!.kg, KG_TOLERANCE)
        listOf(settled1, settled2, settled3).forEach { assertTrue(decode(it)!!.stable) }
    }

    @Test
    fun `moving FT-26R frames are unstable`() {
        listOf(moving1, moving2, moving3).forEach { assertFalse(decode(it)!!.stable) }
        assertEquals(70.15, decode(moving1)!!.kg, KG_TOLERANCE)
    }

    @Test
    fun `zero-weight idle advert is rejected even though bit 0 is set`() {
        assertNull(decode(idle))
    }

    @Test
    fun `bit 0 is ignored without the FT-26R signature`() {
        val foreign = hex(settled1).also { it[19] = 0; it[20] = 0; it[21] = 0 }
        val f = QnBroadcastProtocol.decode(ad(foreign))!!
        assertFalse(f.stable)
        assertEquals(70.30, f.kg, KG_TOLERANCE)
    }

    @Test
    fun `bit 5 marks stable on other models`() {
        val other = hex(settled1).also { it[19] = 0; it[20] = 0; it[21] = 0; it[15] = 0x20 }
        assertTrue(QnBroadcastProtocol.decode(ad(other))!!.stable)
    }

    @Test
    fun `malformed payloads are rejected`() {
        assertNull(QnBroadcastProtocol.decode(ad(hex(settled1), company = 0x1234)))
        assertNull(QnBroadcastProtocol.decode(ad(hex("aabb00"))))
        assertNull(QnBroadcastProtocol.decode(ad(hex(settled1).also { it[0] = 0 })))
    }

    @Test
    fun `recognises by AABB magic, never by name`() {
        assertEquals("FITINDEX FT-26R", QnBroadcastProtocol.recognise(ad(hex(settled1))))
        assertEquals("QN Broadcast Scale", QnBroadcastProtocol.recognise(ad(bytes(0xAA, 0xBB, 0, 0, 0, 0))))
        assertNull(QnBroadcastProtocol.recognise(Advertisement("00:00:00:00:00:01", "QN-Scale")))
        assertNull(QnBroadcastProtocol.recognise(ad(bytes(0x00, 0xBB, 0, 0), name = "Renpho")))
    }
}
