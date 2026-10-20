package com.resurgeai.core.scale.protocol

/**
 * PICOOC Mini Lite, advertised as "PICOOC-L". 16-byte manufacturer structure whose first two
 * bytes Android takes as the company id (they are part of the MAC, not a fixed id):
 *
 * `[0-5 MAC][6 state][7 protocol][8-9 weight BE, 0.05 kg][10-11 impedance BE, 0.1 Ω, FFFF = none]
 *  [12-13 weight BE, 0.1 lb][14 display unit][15 ~sum(0..14)]`
 *
 * State 0x39 is the completed measurement. Ported from openScale's `PicoocBroadcastHandler`
 * (GPL-3.0); its impedance-anchor learner only feeds body composition, which isn't ported.
 */
object PicoocMiniLiteProtocol : ScaleProtocol {
    override val id = "picooc_mini_lite"
    override val family = "PICOOC Mini Lite"

    private const val NAME = "PICOOC-L"
    private const val COMPLETE = 0x39

    override fun recognise(ad: Advertisement): String? =
        if (ad.name?.trim().equals(NAME, ignoreCase = true)) "PICOOC Mini Lite" else null

    override fun decode(ad: Advertisement): ScaleFrame? {
        val f = ad.manufacturerData.firstNotNullOfOrNull { (company, d) -> frame(company, d) } ?: return null
        val kg = f.u16be(8) / 20.0
        if (!plausible(kg)) return null
        val impedance = f.u16be(10).takeIf { it != 0xFFFF }?.let { it / 10 }?.takeIf { it > 0 }
        return ScaleFrame(kg = kg, stable = f.u8(6) == COMPLETE, impedanceOhm = impedance)
    }

    /** The full 16-byte structure, or null if [payload] is the wrong size or fails the checksum. */
    private fun frame(company: Int, payload: ByteArray): ByteArray? {
        if (payload.size != 14) return null
        val f = byteArrayOf(company.toByte(), (company ushr 8).toByte()) + payload
        return if (f.sumOf { it.toInt() and 0xFF } and 0xFF == 0xFF) f else null
    }
}
