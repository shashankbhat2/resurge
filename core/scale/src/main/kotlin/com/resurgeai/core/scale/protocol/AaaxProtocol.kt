package com.resurgeai.core.scale.protocol

/**
 * The "AAA002 / AAA007 / AAA013 / AAA07A" broadcast family (e.g. Posture FG220LB, Fitdays app).
 * Manufacturer data of at least 12 bytes:
 *
 * `[0-5 advertiser MAC, reversed][6-11 payload XOR (companyId >> 8)]`
 *
 * Decoded payload: `[0-3 BE: final:1 | reserved:13 | grams:18][4 type][5 checksum]`, where type
 * 0xAD is weight and 0xA6 impedance, and `sum(payload[0..4]) & 0x1F == payload[5] & 0x1F`.
 *
 * Boards under other names are recognised by the frame itself: a MAC echo plus a valid checksum.
 * Ported from openScale's `AAAxHandler` (GPL-3.0).
 */
object AaaxProtocol : ScaleProtocol {
    override val id = "aaax"
    override val family = "AAA-series broadcast scale"

    private val KNOWN_NAMES = setOf("AAA002", "AAA007", "AAA013", "AAA07A")
    private const val TYPE_WEIGHT = 0xAD

    override fun recognise(ad: Advertisement): String? {
        val name = ad.name?.trim().orEmpty()
        val framed = ad.manufacturerData.any { (company, d) ->
            echoesAddress(d, ad.address) && payload(company, d) != null
        }
        if (name !in KNOWN_NAMES && !framed) return null
        return "AAA-series Broadcast Scale (${name.ifEmpty { "AAAx" }})"
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        for ((company, d) in ad.manufacturerData) {
            val p = payload(company, d) ?: continue
            if (p.u8(4) != TYPE_WEIGHT) continue
            val kg = (((p.u8(1) and 0x03) shl 16) or p.u16be(2)) / 1000.0
            if (!plausible(kg)) return null
            return ScaleFrame(kg = kg, stable = p.u8(0) and 0x80 != 0)
        }
        return null
    }

    internal fun payload(company: Int, d: ByteArray): ByteArray? {
        if (d.size < 12) return null
        val key = (company ushr 8) and 0xFF
        val p = ByteArray(6) { (d[6 + it].toInt() xor key).toByte() }
        val sum = (0 until 5).sumOf { p.u8(it) }
        return if (sum and 0x1F == p.u8(5) and 0x1F) p else null
    }

    private fun echoesAddress(d: ByteArray, address: String): Boolean {
        val mac = macOctets(address) ?: return false
        return d.size >= 6 && (0 until 6).all { d.u8(it) == mac[5 - it] }
    }
}
