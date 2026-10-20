package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frames from openScale's `AAAxHandlerTest` (AAA07A layout, company id 0xA0AC). */
class AaaxProtocolTest {

    private val address = "C0:FF:EE:12:34:56"
    private val company = 0xA0AC
    private val macEcho = "563412EEFFC0"
    private val settling = hex("$macEcho A0CD84660DA4")
    private val stable = hex("$macEcho 20CD84580DB6")
    private val impedance = hex("$macEcho A0A0A0A206A8")

    private fun ad(record: ByteArray?, name: String? = "AAA0ZZ", address: String = this.address) = Advertisement(
        address, name,
        manufacturerData = record?.let { mapOf(company to it) } ?: emptyMap(),
        serviceUuids = listOf(uuid16(0xFFB0)),
    )

    @Test
    fun `final weight frame is stable`() {
        val f = AaaxProtocol.decode(ad(stable))!!
        assertEquals(75.0, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
    }

    @Test
    fun `settling frame is unstable`() {
        val f = AaaxProtocol.decode(ad(settling))!!
        assertEquals(74.95, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
    }

    @Test
    fun `impedance frame and broken checksum are rejected`() {
        assertNull(AaaxProtocol.decode(ad(impedance)))
        assertNull(AaaxProtocol.decode(ad(stable.copyOf().also { it[9] = (it[9] + 1).toByte() })))
    }

    @Test
    fun `recognised by frame fingerprint or known name`() {
        assertEquals("AAA-series Broadcast Scale (AAA0ZZ)", AaaxProtocol.recognise(ad(stable)))
        assertEquals("AAA-series Broadcast Scale (AAAx)", AaaxProtocol.recognise(ad(impedance, name = "")))
        assertEquals("AAA-series Broadcast Scale (AAA07A)", AaaxProtocol.recognise(ad(null, name = "AAA07A")))
        assertNull(AaaxProtocol.recognise(ad(stable, address = "11:22:33:44:55:66")))
        assertNull(AaaxProtocol.recognise(ad(null, name = "swan")))
    }
}
