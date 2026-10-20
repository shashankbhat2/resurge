package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic frames built to the layouts in openScale's `OkOkHandler`. */
class OkOkProtocolTest {

    private fun ad(name: String?, vararg records: Pair<Int, ByteArray>) =
        Advertisement("11:22:33:44:55:66", name, manufacturerData = mapOf(*records))

    private fun v20(flags: Int, weight: Int, deciOhm: Int = 0): ByteArray {
        val d = ByteArray(19)
        d[6] = flags.toByte()
        d[8] = (weight shr 8).toByte(); d[9] = weight.toByte()
        d[10] = (deciOhm shr 8).toByte(); d[11] = deciOhm.toByte()
        d[12] = (0 until 12).fold(0x20) { acc, i -> acc xor (d[i].toInt() and 0xFF) }.toByte()
        return d
    }

    private fun v11(props: Int, msb: Int, lsb: Int): ByteArray {
        val d = ByteArray(23)
        d[3] = msb.toByte(); d[4] = lsb.toByte(); d[9] = props.toByte()
        d[16] = (0 until 16).fold(0xCA xor 0x11) { acc, i -> acc xor (d[i].toInt() and 0xFF) }.toByte()
        return d
    }

    private fun c0(attrib: Int, msb: Int, lsb: Int, deciOhm: Int = 0) = ByteArray(13).also {
        it[0] = msb.toByte(); it[1] = lsb.toByte()
        it[2] = (deciOhm shr 8).toByte(); it[3] = deciOhm.toByte()
        it[6] = attrib.toByte()
    }

    @Test
    fun `V20 final frame is stable with impedance`() {
        val f = OkOkProtocol.decode(ad("ADV", 0x20CA to v20(0x01 or 0x04, 7_550, deciOhm = 5_000)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertEquals(500, f.impedanceOhm)
    }

    @Test
    fun `V20 live frame is unstable at 0_1 kg resolution`() {
        val f = OkOkProtocol.decode(ad("Chipsea-BLE", 0x20CA to v20(0x00, 755)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `V20 bad checksum and zero weight are rejected`() {
        val bad = v20(0x01, 755).also { it[12] = (it[12] + 1).toByte() }
        assertNull(OkOkProtocol.decode(ad("ADV", 0x20CA to bad)))
        assertNull(OkOkProtocol.decode(ad("ADV", 0x20CA to v20(0x01, 0))))
        assertNull(OkOkProtocol.decode(ad("ADV", 0x20CA to ByteArray(16))))
    }

    @Test
    fun `nameless C0 frame decodes stable kg and pounds and stones`() {
        val kg = OkOkProtocol.decode(ad(null, 0x12C0 to c0(0x01, 0x02, 0xF3, deciOhm = 4_800)))!!
        assertEquals(75.5, kg.kg, KG_TOLERANCE)
        assertTrue(kg.stable)
        assertEquals(480, kg.impedanceOhm)

        val lb = OkOkProtocol.decode(ad(null, 0x12C0 to c0(0x01 or (2 shl 3), 0x06, 0x82)))!!
        assertEquals(166.6 * KG_PER_LB, lb.kg, KG_TOLERANCE)

        val st = OkOkProtocol.decode(ad(null, 0x12C0 to c0(0x01 or (3 shl 3), 11, 125)))!!
        assertEquals(11 * 14 * KG_PER_LB + 12.5 * KG_PER_LB, st.kg, KG_TOLERANCE)
    }

    @Test
    fun `C0 jin halves the value and unstable frames are flagged`() {
        val f = OkOkProtocol.decode(ad(null, 0x12C0 to c0(1 shl 3, 0x05, 0xE6)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
    }

    @Test
    fun `V11 decodes with unit and resolution and is unflagged`() {
        val f = OkOkUnflaggedProtocol.decode(ad("ADV", 0x11CA to v11(2 shl 1, 0x1D, 0x7E)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertFalse(OkOkUnflaggedProtocol.reportsStability)
        val bad = v11(0, 0x02, 0xF3).also { it[16] = (it[16] + 1).toByte() }
        assertNull(OkOkUnflaggedProtocol.decode(ad("ADV", 0x11CA to bad)))
    }

    @Test
    fun `VF0 decodes little-endian tenths and rejects zero`() {
        val f = OkOkUnflaggedProtocol.decode(ad("Yoda1x", 0xF0FF to bytes(0, 0, 0xF3, 0x02)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertNull(OkOkUnflaggedProtocol.decode(ad("ADV", 0xF0FF to ByteArray(16))))
    }

    @Test
    fun `recognises each variant under the right protocol`() {
        assertEquals("OKOK V20", OkOkProtocol.recognise(ad("ADV", 0x20CA to ByteArray(16))))
        assertEquals("OKOK C0", OkOkProtocol.recognise(ad(null, 0x01C0 to ByteArray(13))))
        assertEquals("OKOK V11", OkOkUnflaggedProtocol.recognise(ad("ADV", 0x11CA to ByteArray(16))))
        assertEquals("OKOK VF0", OkOkUnflaggedProtocol.recognise(ad("yoda0", 0xF0FF to ByteArray(16))))
        assertNull(OkOkProtocol.recognise(ad("ADV", 0x11CA to ByteArray(16))))
        assertNull(OkOkUnflaggedProtocol.recognise(ad("ADV", 0x20CA to ByteArray(16))))
        assertNull(OkOkProtocol.recognise(ad("Some Scale", 0x20CA to ByteArray(16))))
        assertNull(OkOkProtocol.recognise(ad(null, 0x20CA to ByteArray(16))))
        assertSame(OkOkProtocol, ScaleProtocols.match(ad("ADV", 0x20CA to ByteArray(16)))!!.protocol)
    }
}
