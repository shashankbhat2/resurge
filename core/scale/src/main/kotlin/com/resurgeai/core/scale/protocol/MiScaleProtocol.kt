package com.resurgeai.core.scale.protocol

import java.util.Locale

/**
 * Xiaomi Mi Smart Scale (v1, 2) and Mi Body Composition Scale (2). They put the live reading in
 * service data: 10 bytes under Weight Scale (0x181D) or 13 bytes under Body Composition (0x181B).
 * Byte layouts follow openScale's `MiScaleHandler`.
 */
object MiScaleProtocol : ScaleProtocol {
    override val id = "xiaomi_mi"
    override val family = "Xiaomi Mi Scale"

    private val WEIGHT_SERVICE = uuid16(0x181D)
    private val BODY_COMP_SERVICE = uuid16(0x181B)

    override fun recognise(ad: Advertisement): String? {
        val name = ad.name?.uppercase(Locale.ROOT).orEmpty()
        val known = name.startsWith("MIBCS") || name.startsWith("MIBFS") ||
            name == "MI SCALE2" || name.startsWith("MI_SCALE")
        if (!known) return null
        return when {
            ad.serviceData[BODY_COMP_SERVICE]?.size == 13 || name.startsWith("MIBCS") || name.startsWith("MIBFS") ->
                "Mi Body Composition Scale"
            else -> "Mi Smart Scale"
        }
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        ad.serviceData[BODY_COMP_SERVICE]?.takeIf { it.size == 13 }?.let { return live13(it) }
        ad.serviceData[WEIGHT_SERVICE]?.takeIf { it.size == 10 }?.let { return live10(it) }
        return null
    }

    /** `[unit][ctrl][year LE][mon][day][h][m][s][impedance LE][weight LE]` */
    private fun live13(d: ByteArray): ScaleFrame? {
        val c0 = d.u8(0)
        val c1 = d.u8(1)
        if (c1 and 0x80 != 0) return null // weight removed: the reading is history, not live
        val raw = d.u16le(11)
        val kg = toKg(raw, lbs = c0 and 0x01 != 0, catty = c1 and 0x40 != 0)
        if (!plausible(kg)) return null
        val impedance = if (c1 and 0x02 != 0) d.u16le(9).takeIf { it in 1..2999 } else null
        return ScaleFrame(kg = kg, stable = c1 and 0x20 != 0, impedanceOhm = impedance)
    }

    /** `[ctrl][weight LE][year LE][mon][day][h][m][s]` */
    private fun live10(d: ByteArray): ScaleFrame? {
        val c = d.u8(0)
        if (c and 0x80 != 0) return null
        val kg = toKg(d.u16le(1), lbs = c and 0x01 != 0, catty = c and 0x10 != 0)
        if (!plausible(kg)) return null
        return ScaleFrame(kg = kg, stable = c and 0x20 != 0)
    }

    private fun toKg(raw: Int, lbs: Boolean, catty: Boolean): Double = when {
        lbs -> raw / 100.0 * KG_PER_LB
        catty -> raw / 100.0 / 2.0
        else -> raw / 200.0
    }
}
