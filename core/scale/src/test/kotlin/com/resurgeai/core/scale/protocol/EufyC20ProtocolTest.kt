package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic frames built to the layout in openScale's `EufyC20Handler`. */
class EufyC20ProtocolTest {

    private fun payload(flags: Int, centiKg: Int, deciOhm: Int = 0) = ByteArray(19).apply {
        this[10] = flags.toByte()
        this[12] = centiKg.toByte(); this[13] = (centiKg shr 8).toByte()
        this[17] = deciOhm.toByte(); this[18] = (deciOhm shr 8).toByte()
    }

    private fun ad(data: ByteArray?, name: String? = null) = Advertisement(
        "AA:BB:CC:DD:EE:FF", name,
        manufacturerData = data?.let { mapOf(0xBC64 to it) } ?: emptyMap(),
    )

    @Test
    fun `weight frame decodes and never claims to be settled`() {
        val f = EufyC20Protocol.decode(ad(payload(0x01, 7_550)))!!
        assertEquals(75.5, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertNull(f.impedanceOhm)
        assertFalse(EufyC20Protocol.reportsStability)
    }

    @Test
    fun `impedance is read when flagged`() {
        val f = EufyC20Protocol.decode(ad(payload(0x01 or 0x40, 7_550, deciOhm = 4_870)))!!
        assertEquals(487, f.impedanceOhm)
    }

    @Test
    fun `frames without a weight or with zero weight are rejected`() {
        assertNull(EufyC20Protocol.decode(ad(payload(0x40, 7_550, deciOhm = 4_870))))
        assertNull(EufyC20Protocol.decode(ad(payload(0x01, 0))))
        assertNull(EufyC20Protocol.decode(ad(ByteArray(13))))
    }

    @Test
    fun `recognised by company id or name`() {
        assertEquals("Eufy C20 (T9130)", EufyC20Protocol.recognise(ad(ByteArray(14))))
        assertEquals("Eufy C20 (T9130)", EufyC20Protocol.recognise(ad(null, name = "eufy T9130")))
        assertEquals("Eufy C20 (T9130)", EufyC20Protocol.recognise(ad(null, name = "EUFY C20")))
        assertNull(EufyC20Protocol.recognise(ad(null, name = "eufy T9148")))
    }
}
