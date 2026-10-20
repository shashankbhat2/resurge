package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 75.500 kg / 500 Ω fixture from openScale's `ScaleFactoryTest`. */
class EtekcityFit8SProtocolTest {

    private val service = uuid16(0xFFD0)

    private fun payload(stable: Boolean = true, grams: Int = 75_500, ohm: Int = 500) = ByteArray(20).apply {
        this[0] = 0x01
        this[10] = grams.toByte(); this[11] = (grams shr 8).toByte(); this[12] = (grams shr 16).toByte()
        this[13] = ohm.toByte(); this[14] = (ohm shr 8).toByte()
        this[15] = if (stable) 1 else 0
    }

    private fun ad(data: ByteArray, company: Int = 0x06D0, services: List<java.util.UUID> = listOf(service)) =
        Advertisement("AA:BB:CC:DD:EE:FF", null, manufacturerData = mapOf(company to data), serviceUuids = services)

    @Test
    fun `stable frame decodes grams and impedance`() {
        val f = EtekcityFit8SProtocol.decode(ad(payload()))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertEquals(500, f.impedanceOhm)
    }

    @Test
    fun `unstable frame without impedance`() {
        val f = EtekcityFit8SProtocol.decode(ad(payload(stable = false, grams = 74_900, ohm = 0)))!!
        assertEquals(74.9, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `zero weight and wrong length are rejected`() {
        assertNull(EtekcityFit8SProtocol.decode(ad(payload(grams = 0))))
        assertNull(EtekcityFit8SProtocol.decode(ad(payload().copyOf(19))))
    }

    @Test
    fun `recognised only with both company id and service`() {
        assertEquals("Etekcity Fit 8S", EtekcityFit8SProtocol.recognise(ad(payload())))
        assertNull(EtekcityFit8SProtocol.recognise(ad(payload(), services = emptyList())))
        assertNull(EtekcityFit8SProtocol.recognise(ad(payload(), company = 0xFF64)))
    }

    @Test
    fun `wins over the generic Scaleup match on 0x06D0`() {
        assertEquals("Scale Up", ScaleupProtocol.recognise(ad(payload())))
        assertSame(EtekcityFit8SProtocol, ScaleProtocols.match(ad(payload()))!!.protocol)
    }
}
