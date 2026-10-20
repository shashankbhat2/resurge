package com.resurgeai.core.scale.protocol

/**
 * Sinocare scales (e.g. CW286) advertising as "Weight Scale". Manufacturer data under company
 * id 0xFF64 or 0x0000, at least 17 bytes:
 *
 * `[0-5 address]…[9-10 weight LE, 0.01 kg]…[16 XOR of bytes 6..15]`
 *
 * Recognised by name only: other scales, the Eufy P2 among them, also use company id 0xFF64.
 * There is no settled flag. Ported from openScale's `SinocareHandler` (GPL-3.0).
 */
object SinocareProtocol : ScaleProtocol {
    override val id = "sinocare"
    override val family = "Sinocare"
    override val reportsStability = false

    private const val COMPANY_ID = 0xFF64
    private const val COMPANY_ID_ALT = 0x0000
    private const val CHECKSUM = 16

    override fun recognise(ad: Advertisement): String? =
        if (ad.name.equals("Weight Scale", ignoreCase = true)) "Sinocare Weight Scale" else null

    override fun decode(ad: Advertisement): ScaleFrame? {
        val d = ad.manufacturerData[COMPANY_ID] ?: ad.manufacturerData[COMPANY_ID_ALT] ?: return null
        if (d.size <= CHECKSUM) return null
        if ((6 until CHECKSUM).fold(0) { acc, i -> acc xor d.u8(i) } != d.u8(CHECKSUM)) return null
        val kg = d.u16le(9) / 100.0
        return if (plausible(kg)) ScaleFrame(kg = kg, stable = false) else null
    }
}
