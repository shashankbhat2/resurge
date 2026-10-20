package com.resurgeai.core.scale.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic TEA-encrypted records from openScale's `AiLinkLibTest` (company id 0x0301). */
class AiLinkBroadcastProtocolTest {

    private val company = 0x0301
    private val completed = hex("01 5f4e3d2c1b0a 8f 10e69cf7cb452cccffff")
    private val completed684 = hex("01 5f4e3d2c1b0a 7e 945ab8103c0b750effff")
    private val measuring = hex("01 5f4e3d2c1b0a 33 31c5f84954a1b455ffff")
    private val idle = hex("01 5f4e3d2c1b0a 81 cd9244ffe6049c5bffff")
    private val noImpedance = hex("01 5f4e3d2c1b0a f9 95fd662927ee4283ffff")

    private fun ad(record: ByteArray, id: Int = company, withService: Boolean = true) = Advertisement(
        "0A:1B:2C:3D:4E:5F", "EL1",
        manufacturerData = mapOf(id to record),
        serviceUuids = if (withService) listOf(uuid16(0xF0A0)) else emptyList(),
    )

    @Test
    fun `completed frame is stable with impedance`() {
        val f = AiLinkBroadcastProtocol.decode(ad(completed))!!
        assertEquals(70.0, f.kg, KG_TOLERANCE)
        assertTrue(f.stable)
        assertEquals(500, f.impedanceOhm)
        assertEquals(68.4, AiLinkBroadcastProtocol.decode(ad(completed684))!!.kg, KG_TOLERANCE)
    }

    @Test
    fun `completed frame whose BIA pass failed carries no impedance`() {
        val f = AiLinkBroadcastProtocol.decode(ad(noImpedance))!!
        assertTrue(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `measuring frame is unstable`() {
        val f = AiLinkBroadcastProtocol.decode(ad(measuring))!!
        assertEquals(70.0, f.kg, KG_TOLERANCE)
        assertFalse(f.stable)
        assertNull(f.impedanceOhm)
    }

    @Test
    fun `idle, corrupted, short and wrong-key records are rejected`() {
        assertNull(AiLinkBroadcastProtocol.decode(ad(idle)))
        assertNull(AiLinkBroadcastProtocol.decode(ad(completed.copyOf().also { it[7] = (it[7] + 1).toByte() })))
        assertNull(AiLinkBroadcastProtocol.decode(ad(completed.copyOf().also { it[8] = (it[8] + 1).toByte() })))
        assertNull(AiLinkBroadcastProtocol.decode(ad(completed.copyOf(17))))
        assertNull(AiLinkBroadcastProtocol.decode(ad(completed, id = 0x0405)))
        assertNull(AiLinkBroadcastProtocol.decode(ad(ByteArray(18), id = 0x00FF)))
    }

    @Test
    fun `recognised only with the broadcast service and a decryptable record`() {
        assertEquals("AiLink Body Fat Scale", AiLinkBroadcastProtocol.recognise(ad(completed)))
        assertEquals("AiLink Body Fat Scale", AiLinkBroadcastProtocol.recognise(ad(idle)))
        assertNull(AiLinkBroadcastProtocol.recognise(ad(completed, withService = false)))
        assertNull(AiLinkBroadcastProtocol.recognise(ad(completed.copyOf().also { it[7] = (it[7] + 1).toByte() })))
        assertNull(AiLinkBroadcastProtocol.recognise(ad(ByteArray(18), id = 0x00FF)))
    }
}
