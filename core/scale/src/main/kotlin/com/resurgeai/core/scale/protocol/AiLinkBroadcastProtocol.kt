package com.resurgeai.core.scale.protocol

/**
 * AiLink / eLink broadcast body-fat scales (vendor app "AiLink", often advertised as "EL1").
 * Recognised by service 0xF0A0 plus a manufacturer record that decrypts to a known status.
 *
 * Android's company id is really `VID << 8 | CID`. The 18-byte record is
 * `[0 PID][1-6 MAC LE][7 sum of 8..17][8-17 payload]`, and the first 8 payload bytes are TEA
 * encrypted with a key derived from CID, VID and PID alone. Decrypted:
 *
 * `[0 sequence][1 status][2 flags: unit bits 3-5, decimals bits 1-2][3-4 weight BE, bit 15 =
 *  negative][5-6 impedance BE, Ω][7 algorithm]`
 *
 * Status 0xFF is the completed measurement; impedance is stale until then. Only kilograms have
 * been observed, so other units are refused. Ported from openScale's `AiLinkBroadcastHandler`
 * and `AiLinkLib` (GPL-3.0).
 */
object AiLinkBroadcastProtocol : ScaleProtocol {
    override val id = "ailink_broadcast"
    override val family = "AiLink broadcast scale"

    private val BROADCAST_SERVICE = uuid16(0xF0A0)
    private const val RECORD_SIZE = 18
    private const val STATUS_COMPLETE = 0xFF
    private val KNOWN_STATUSES = setOf(0x00, 0x01, 0x02, 0x03, 0x04, STATUS_COMPLETE)
    private const val UNIT_KG = 0

    private const val DELTA = 0x9E3779B9.toInt()
    private const val SUM_INITIAL = 0xC6EF3720.toInt()
    private const val KEY_PID = 0x41493000
    private const val KEY_VID = 0x4C327900
    private const val KEY_CID = 0x31783100
    private const val KEY_3 = 0x306C6531

    override fun recognise(ad: Advertisement): String? {
        if (BROADCAST_SERVICE !in ad.serviceUuids) return null
        return if (firstPayload(ad) != null) "AiLink Body Fat Scale" else null
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        if (BROADCAST_SERVICE !in ad.serviceUuids) return null
        val p = firstPayload(ad) ?: return null
        val flags = p.u8(2)
        if ((flags shr 3) and 0x07 != UNIT_KG) return null
        if (p.u8(3) and 0x80 != 0) return null
        val raw = ((p.u8(3) and 0x7F) shl 8) or p.u8(4)
        val kg = when ((flags shr 1) and 0x03) {
            1 -> raw / 10.0
            2 -> raw / 100.0
            3 -> raw / 1000.0
            else -> raw.toDouble()
        }
        if (!plausible(kg)) return null
        val complete = p.u8(1) == STATUS_COMPLETE
        val impedance = if (complete) p.u16be(5).takeIf { it > 0 } else null
        return ScaleFrame(kg = kg, stable = complete, impedanceOhm = impedance)
    }

    private fun firstPayload(ad: Advertisement): ByteArray? =
        ad.manufacturerData.firstNotNullOfOrNull { (company, d) -> payload(company, d) }

    /** The decrypted 10-byte payload, or null if the checksum fails or the status is unknown. */
    internal fun payload(company: Int, d: ByteArray): ByteArray? {
        if (d.size < RECORD_SIZE) return null
        val encrypted = d.copyOfRange(8, 18)
        if (encrypted.sumOf { it.toInt() and 0xFF } and 0xFF != d.u8(7)) return null
        val p = decrypt(encrypted, cid = company and 0xFF, vid = (company shr 8) and 0xFF, pid = d.u8(0))
        return if (p.u8(1) in KNOWN_STATUSES) p else null
    }

    /** 32-round TEA over the first 8 bytes only (two LE words); the trailing bytes pass through. */
    private fun decrypt(payload: ByteArray, cid: Int, vid: Int, pid: Int): ByteArray {
        val k = intArrayOf(fold(pid) + KEY_PID, fold(vid) + KEY_VID, fold(cid) + KEY_CID, KEY_3)
        val out = payload.copyOf()
        var v0 = out.le32(0)
        var v1 = out.le32(4)
        var sum = SUM_INITIAL
        repeat(32) {
            v1 -= ((v0 shl 4) + k[2]) xor (v0 + sum) xor ((v0 ushr 5) + k[3])
            v0 -= ((v1 shl 4) + k[0]) xor (v1 + sum) xor ((v1 ushr 5) + k[1])
            sum -= DELTA
        }
        for (i in 0 until 4) {
            out[i] = (v0 shr (8 * i)).toByte()
            out[4 + i] = (v1 shr (8 * i)).toByte()
        }
        return out
    }

    private fun fold(id: Int) = if (id >= 0x10000) id - 0xFFFF else id

    private fun ByteArray.le32(i: Int): Int = u16le(i) or (u16le(i + 2) shl 16)
}
