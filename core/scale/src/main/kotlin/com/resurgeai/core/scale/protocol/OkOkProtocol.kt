package com.resurgeai.core.scale.protocol

/**
 * OKOK (Chipsea) broadcast scales that mark a settled reading. They advertise as "ADV",
 * "Chipsea-BLE", "Yoda0…"/"Yoda1…", or nameless with a C0 record.
 *
 * - V20, company id 0x20CA, 19 bytes: `[6 flags][8-9 weight BE][10-11 impedance BE, 0.1 Ω]
 *   [12 XOR of 0x20 and bytes 0..11]`. Flags bit 0 = final, bit 2 = 0.01 kg (else 0.1 kg).
 * - C0, any company id whose low byte is 0xC0, ≥13 bytes: `[0-1 weight BE][2-3 impedance BE,
 *   0.1 Ω]…[6 attrib]`. Attrib bit 0 = stable, bits 1-2 resolution, bits 3-4 unit.
 *
 * The V11 and VF0 variants, which carry no settled flag, are [OkOkUnflaggedProtocol]. Ported
 * from openScale's `OkOkHandler` (GPL-3.0).
 */
object OkOkProtocol : ScaleProtocol {
    override val id = "okok"
    override val family = OKOK_FAMILY

    private const val V20 = 0x20CA
    private const val V20_SIZE = 19

    override fun recognise(ad: Advertisement): String? = when (okOkVariant(ad)) {
        OkOkVariant.V20 -> "OKOK V20"
        OkOkVariant.C0 -> "OKOK C0"
        else -> null
    }

    override fun decode(ad: Advertisement): ScaleFrame? = when (okOkVariant(ad)) {
        OkOkVariant.V20 -> v20(ad.manufacturerData.getValue(V20))
        OkOkVariant.C0 -> c0(ad.manufacturerData.entries.first { it.key and 0xFF == 0xC0 }.value)
        else -> null
    }

    private fun v20(d: ByteArray): ScaleFrame? {
        if (d.size != V20_SIZE) return null
        if ((0 until 12).fold(0x20) { acc, i -> acc xor d.u8(i) } != d.u8(12)) return null
        val flags = d.u8(6)
        val kg = d.u16be(8) / if (flags and 0x04 != 0) 100.0 else 10.0
        if (!plausible(kg)) return null
        return ScaleFrame(kg = kg, stable = flags and 0x01 != 0, impedanceOhm = ohms(d.u16be(10)))
    }

    private fun c0(d: ByteArray): ScaleFrame? {
        if (d.size < 13) return null
        val attrib = d.u8(6)
        val kg = okOkKg(d.u8(0), d.u8(1), attrib)
        if (!plausible(kg)) return null
        return ScaleFrame(kg = kg, stable = attrib and 0x01 != 0, impedanceOhm = ohms(d.u16be(2)))
    }

    private fun ohms(deciOhm: Int): Int? = (deciOhm / 10).takeIf { it > 0 }
}

/**
 * OKOK (Chipsea) variants with no settled flag, so [WeighSession] waits for repeats.
 *
 * - V11, company id 0x11CA, 23 bytes: `[3-4 weight BE]…[9 props]…[16 XOR of 0xCA^0x11 and
 *   bytes 0..15]`. Props bits 1-2 resolution, bits 3-4 unit.
 * - VF0, company id 0xF0FF, ≥4 bytes: `[2-3 weight LE, 0.1 kg]`.
 *
 * Ported from openScale's `OkOkHandler` (GPL-3.0).
 */
object OkOkUnflaggedProtocol : ScaleProtocol {
    override val id = "okok_unflagged"
    override val family = OKOK_FAMILY
    override val reportsStability = false

    private const val V11 = 0x11CA
    private const val VF0 = 0xF0FF
    private const val V11_SIZE = 23

    override fun recognise(ad: Advertisement): String? = when (okOkVariant(ad)) {
        OkOkVariant.V11 -> "OKOK V11"
        OkOkVariant.VF0 -> "OKOK VF0"
        else -> null
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        val kg = when (okOkVariant(ad)) {
            OkOkVariant.V11 -> v11(ad.manufacturerData.getValue(V11))
            OkOkVariant.VF0 -> vf0(ad.manufacturerData.getValue(VF0))
            else -> null
        } ?: return null
        return if (plausible(kg)) ScaleFrame(kg = kg, stable = false) else null
    }

    private fun v11(d: ByteArray): Double? {
        if (d.size != V11_SIZE) return null
        if ((0 until 16).fold(0xCA xor 0x11) { acc, i -> acc xor d.u8(i) } != d.u8(16)) return null
        return okOkKg(d.u8(3), d.u8(4), d.u8(9))
    }

    private fun vf0(d: ByteArray): Double? = if (d.size < 4) null else d.u16le(2) / 10.0
}

private const val OKOK_FAMILY = "OKOK"
private const val KG_PER_ST = 14 * KG_PER_LB

internal enum class OkOkVariant { V20, V11, VF0, C0 }

/** Which OKOK variant [ad] is, in openScale's priority order, or null if it isn't OKOK. */
internal fun okOkVariant(ad: Advertisement): OkOkVariant? {
    val m = ad.manufacturerData
    val hasC0 = m.keys.any { it and 0xFF == 0xC0 }
    val name = ad.name.orEmpty()
    val named = (name.isEmpty() && hasC0) || name == "ADV" || name == "Chipsea-BLE" ||
        name.startsWith("Yoda0", ignoreCase = true) || name.startsWith("Yoda1", ignoreCase = true)
    if (!named) return null
    return when {
        0x20CA in m -> OkOkVariant.V20
        0x11CA in m -> OkOkVariant.V11
        0xF0FF in m -> OkOkVariant.VF0
        hasC0 -> OkOkVariant.C0
        else -> null
    }
}

/** Weight from the shared OKOK attribute byte: bits 1-2 resolution, bits 3-4 kg/jin/lb/st:lb. */
private fun okOkKg(msb: Int, lsb: Int, attrib: Int): Double {
    val divider = when ((attrib shr 1) and 0x3) {
        1 -> 1.0
        2 -> 100.0
        else -> 10.0
    }
    val raw = (msb shl 8) or lsb
    return when ((attrib shr 3) and 0x3) {
        0 -> raw / divider
        1 -> raw / divider / 2.0
        2 -> raw / divider * KG_PER_LB
        else -> msb * KG_PER_ST + lsb / divider * KG_PER_LB
    }
}
