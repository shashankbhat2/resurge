package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frames built like openScale's `YunmaiXHandlerTest` for a placeholder device. */
class YunmaiXProtocolTest {

    private val address = "C0:FF:EE:12:34:56"
    private val company = 0x3456

    private fun frame(state: Int, weightRaw: Int, impedance: Int = 0, session: Int = 0x04): ByteArray {
        val body = bytes(
            0x12, 0xEE, 0xFF, 0xC0, 0x0B, 0x74, 0x17, session, state,
            weightRaw shr 8, weightRaw and 0xFF, impedance shr 8, impedance and 0xFF,
        )
        return body + (7..12).fold(0) { acc, i -> acc xor (body[i].toInt() and 0xFF) }.toByte()
    }

    private fun ad(payload: ByteArray?, address: String = this.address, services: List<Int> = emptyList()) =
        Advertisement(
            address, null,
            manufacturerData = payload?.let { mapOf(company to it) } ?: emptyMap(),
            serviceUuids = services.map { uuid16(it) },
        )

    @Test
    fun `final frame is stable with impedance`() {
        val f = YunmaiXProtocol.decode(ad(frame(0x03, 8_000, impedance = 500, session = 0x10)))!!
        assertEquals(80.0, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertEquals(500, f.impedanceOhm)
    }

    @Test
    fun `live and weight-stable frames are not yet settled`() {
        val live = YunmaiXProtocol.decode(ad(frame(0x01, 7_955)))!!
        assertEquals(79.55, live.kg, KG_TOLERANCE)
        assertFalse(live.stable)
        assertFalse(YunmaiXProtocol.decode(ad(frame(0x02, 8_000)))!!.stable)
    }

    @Test
    fun `idle, corrupted, wrong-signature, wrong-size and relocated frames are rejected`() {
        assertNull(YunmaiXProtocol.decode(ad(frame(0x01, 0))))
        assertNull(YunmaiXProtocol.decode(ad(frame(0x03, 8_000, 500).also { it[9] = 0x1C })))
        assertNull(YunmaiXProtocol.decode(ad(frame(0x03, 8_000, 500).also { it[5] = 0x75 })))
        assertNull(YunmaiXProtocol.decode(ad(frame(0x03, 8_000).copyOf(13))))
        assertNull(YunmaiXProtocol.decode(ad(frame(0x03, 8_000, 500), address = "AA:BB:CC:DD:EE:FF")))
    }

    @Test
    fun `recognised by frame or service 0x1320`() {
        assertEquals("Yunmai X", YunmaiXProtocol.recognise(ad(frame(0x01, 0))))
        assertEquals("Yunmai X", YunmaiXProtocol.recognise(ad(null, services = listOf(0x1320))))
        assertNull(YunmaiXProtocol.recognise(ad(bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14))))
        assertNull(YunmaiXProtocol.recognise(ad(null, services = listOf(0x180D, 0x180F))))
        assertNull(YunmaiXProtocol.recognise(ad(frame(0x03, 8_000, 500), address = "AA:BB:CC:DD:EE:FF")))
    }
}
