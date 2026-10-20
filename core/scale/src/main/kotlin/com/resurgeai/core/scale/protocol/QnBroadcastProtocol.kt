package com.resurgeai.core.scale.protocol

/**
 * QN-lineage scales in non-connectable broadcast mode (Renpho, FITINDEX and other private
 * labels), which put an "AABB" frame in manufacturer data under company id 0xFFFF:
 *
 * `[AA BB][address ×6]…[15 status]…[17-18 weight LE, 0.01 kg][19-21 model signature]`
 *
 * Settled is status bit 5, except on the FITINDEX FT-26R family (signature `51 0E 03`), which
 * latches bit 0 instead and never sets bit 5. Bit 0 is honoured only with that signature so an
 * unknown model can't log a moving weight.
 *
 * Recognised by the AABB magic only, never by name: "Renpho" and "QN-Scale" are also used by
 * connectable models this protocol can't read. Ported from openScale's `QNHandlerBroadcast`
 * (GPL-3.0).
 */
object QnBroadcastProtocol : ScaleProtocol {
    override val id = "qn_broadcast"
    override val family = "QN / Renpho / FITINDEX"

    private const val COMPANY_ID = 0xFFFF
    private const val MIN_LEN = 19
    private const val STATUS = 15
    private const val WEIGHT = 17
    private const val STABLE_BIT = 0x20
    private const val STABLE_BIT_FT26R = 0x01
    private const val SIGNATURE_AT = 19
    private val FT26R_SIGNATURE = byteArrayOf(0x51, 0x0E, 0x03)

    override fun recognise(ad: Advertisement): String? {
        val d = ad.manufacturerData[COMPANY_ID] ?: return null
        if (!hasMagic(d)) return null
        return if (isFt26r(d)) "FITINDEX FT-26R" else "QN Broadcast Scale"
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        val d = ad.manufacturerData[COMPANY_ID] ?: return null
        if (d.size < MIN_LEN || !hasMagic(d)) return null
        val kg = d.u16le(WEIGHT) / 100.0
        if (!plausible(kg)) return null
        val mask = if (isFt26r(d)) STABLE_BIT or STABLE_BIT_FT26R else STABLE_BIT
        return ScaleFrame(kg = kg, stable = d.u8(STATUS) and mask != 0)
    }

    private fun hasMagic(d: ByteArray) = d.size >= 2 && d.u8(0) == 0xAA && d.u8(1) == 0xBB

    private fun isFt26r(d: ByteArray) =
        d.size >= SIGNATURE_AT + FT26R_SIGNATURE.size &&
            FT26R_SIGNATURE.indices.all { d[SIGNATURE_AT + it] == FT26R_SIGNATURE[it] }
}
