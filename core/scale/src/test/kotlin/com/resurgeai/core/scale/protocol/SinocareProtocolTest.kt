package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The captured CW286 advert from openScale's `SinocareHandlerTest`. */
class SinocareProtocolTest {

    private val cw286 = hex("000000000000CE0000061D0000000001D4")

    private fun ad(data: ByteArray, company: Int = 0x0000, name: String? = "Weight Scale") =
        Advertisement("00:11:22:33:44:55", name, manufacturerData = mapOf(company to data))

    @Test
    fun `decodes the CW286 capture under the alternate company id`() {
        val f = SinocareProtocol.decode(ad(cw286))!!
        assertEquals(74.30, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertFalse(SinocareProtocol.reportsStability)
    }

    @Test
    fun `decodes under the primary company id`() {
        assertEquals(74.30, SinocareProtocol.decode(ad(cw286, company = 0xFF64))!!.kg, KG_TOLERANCE)
    }

    @Test
    fun `bad checksum, short and zero-weight frames are rejected`() {
        assertNull(SinocareProtocol.decode(ad(cw286.copyOf().also { it[16] = 0 })))
        assertNull(SinocareProtocol.decode(ad(cw286.copyOf(16))))
        assertNull(SinocareProtocol.decode(ad(hex("000000000000CE000000000000000001CF"))))
    }

    @Test
    fun `recognised by name only, never by the shared 0xFF64 company id`() {
        assertEquals("Sinocare Weight Scale", SinocareProtocol.recognise(ad(cw286, name = "weight scale")))
        assertNull(SinocareProtocol.recognise(ad(cw286, company = 0xFF64, name = "eufy T9148")))
        assertSame(SinocareProtocol, ScaleProtocols.match(ad(ByteArray(12), company = 0xFF64))!!.protocol)
    }
}
