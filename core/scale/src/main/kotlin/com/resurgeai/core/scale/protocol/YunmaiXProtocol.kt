package com.resurgeai.core.scale.protocol

/**
 * Yunmai X (YMBS-M268, "YUNMAI-ADV"). Nameless; advertises service 0x1320 and a 16-byte
 * manufacturer structure whose first two bytes Android takes as the company id:
 *
 * `[0-5 MAC, reversed][6-8 0B 74 17][9 session][10 state][11-12 weight BE, 0.01 kg]
 *  [13-14 impedance BE, Ω][15 XOR of 9..14]`
 *
 * State 1 is live, 2 weight stable, 3 final with impedance. Only state 3 counts as settled so
 * the impedance isn't lost. Ported from openScale's `YunmaiXHandler` (GPL-3.0).
 */
object YunmaiXProtocol : ScaleProtocol {
    override val id = "yunmai_x"
    override val family = "Yunmai X"

    private const val STATE_FINAL = 0x03
    private val ADV_SERVICE = uuid16(0x1320)

    override fun recognise(ad: Advertisement): String? {
        val framed = ad.manufacturerData.any { (company, d) -> parse(company, d, ad.address) != null }
        return if (framed || ADV_SERVICE in ad.serviceUuids) "Yunmai X" else null
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        val p = ad.manufacturerData.firstNotNullOfOrNull { (company, d) -> parse(company, d, ad.address) }
            ?: return null
        val kg = p.u16be(9) / 100.0
        if (!plausible(kg)) return null
        val final = p.u8(8) == STATE_FINAL
        val impedance = if (final) p.u16be(11).takeIf { it > 0 } else null
        return ScaleFrame(kg = kg, stable = final, impedanceOhm = impedance)
    }

    /** The 14-byte payload if it carries the signature, checksum and an echo of [address]. */
    private fun parse(company: Int, p: ByteArray, address: String): ByteArray? {
        if (p.size != 14) return null
        if (p.u8(4) != 0x0B || p.u8(5) != 0x74 || p.u8(6) != 0x17) return null
        if ((7..12).fold(0) { acc, i -> acc xor p.u8(i) } != p.u8(13)) return null
        val mac = macOctets(address)
        if (mac != null) {
            val echoed = company == ((mac[4] shl 8) or mac[5]) &&
                p.u8(0) == mac[3] && p.u8(1) == mac[2] && p.u8(2) == mac[1] && p.u8(3) == mac[0]
            if (!echoed) return null
        }
        return p
    }
}
