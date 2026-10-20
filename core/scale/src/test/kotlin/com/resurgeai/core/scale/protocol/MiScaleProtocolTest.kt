package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiScaleProtocolTest {

    private fun bodyComp(unit: Int, ctrl: Int, rawWeight: Int, impedance: Int = 0) = bytes(
        unit, ctrl, 0xE8, 0x07, 10, 20, 7, 30, 0,
        impedance and 0xFF, impedance shr 8, rawWeight and 0xFF, rawWeight shr 8,
    )

    private fun weightOnly(ctrl: Int, rawWeight: Int) = bytes(
        ctrl, rawWeight and 0xFF, rawWeight shr 8, 0xE8, 0x07, 10, 20, 7, 30, 0,
    )

    private fun ad13(data: ByteArray, name: String = "MIBCS") =
        Advertisement("C8:47:8C:00:00:01", name, serviceData = mapOf(uuid16(0x181B) to data))

    private fun ad10(data: ByteArray, name: String = "MI SCALE2") =
        Advertisement("C8:47:8C:00:00:02", name, serviceData = mapOf(uuid16(0x181D) to data))

    @Test
    fun `body composition frame decodes stable weight and impedance`() {
        val f = MiScaleProtocol.decode(ad13(bodyComp(0x02, 0x20 or 0x02, 15_000, impedance = 480)))!!
        assertEquals(75.0, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertEquals(480, f.impedanceOhm)
    }

    @Test
    fun `body composition frame without stable bit is unstable and has no impedance`() {
        val f = MiScaleProtocol.decode(ad13(bodyComp(0x02, 0x00, 14_900, impedance = 480)))!!
        assertEquals(74.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `body composition frame in pounds converts to kilograms`() {
        val f = MiScaleProtocol.decode(ad13(bodyComp(0x01, 0x20, 16_535)))!!
        assertEquals(165.35 * KG_PER_LB, f.kg, KG_TOLERANCE)
    }

    @Test
    fun `weight removed or empty frames are rejected`() {
        assertNull(MiScaleProtocol.decode(ad13(bodyComp(0x02, 0x80 or 0x20, 15_000))))
        assertNull(MiScaleProtocol.decode(ad13(bodyComp(0x02, 0x20, 0))))
    }

    @Test
    fun `weight scale frame decodes stable and unstable`() {
        val stable = MiScaleProtocol.decode(ad10(weightOnly(0x20, 14_000)))!!
        assertEquals(70.0, stable.kg, KG_TOLERANCE)
        assertTrue(stable.stable)
        assertFalse(MiScaleProtocol.decode(ad10(weightOnly(0x00, 14_000)))!!.stable)
    }

    @Test
    fun `weight scale frame in catty converts to kilograms`() {
        val f = MiScaleProtocol.decode(ad10(weightOnly(0x20 or 0x10, 14_000)))!!
        assertEquals(70.0, f.kg, KG_TOLERANCE)
    }

    @Test
    fun `weight scale history frame is rejected`() {
        assertNull(MiScaleProtocol.decode(ad10(weightOnly(0x80 or 0x20, 14_000))))
    }

    @Test
    fun `recognises models by name`() {
        assertEquals("Mi Body Composition Scale", MiScaleProtocol.recognise(ad13(bodyComp(0, 0, 0))))
        assertEquals("Mi Smart Scale", MiScaleProtocol.recognise(ad10(weightOnly(0, 0))))
        assertNull(MiScaleProtocol.recognise(Advertisement("00:00:00:00:00:00", "Unrelated")))
    }
}
