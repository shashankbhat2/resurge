package com.resurgeai.core.scale.protocol

import java.util.Locale

/**
 * Eufy C20 (T9130). Company id 0xBC64, or a name like "eufy C20" / "eufy T9130". Manufacturer
 * data of at least 14 bytes:
 *
 * `…[10 flags][11][12-13 weight LE, 0.01 kg][14][15 heart rate][16][17-18 impedance LE, 0.1 Ω]`
 *
 * Flags bit 0 = weight present, bit 6 = impedance present, bit 7 = heart rate present. There is
 * no settled flag. Ported from openScale's `EufyC20Handler` (GPL-3.0).
 */
object EufyC20Protocol : ScaleProtocol {
    override val id = "eufy_c20"
    override val family = "Eufy Smart Scale C20"
    override val reportsStability = false

    private const val COMPANY_ID = 0xBC64
    private const val MIN_LEN = 14

    override fun recognise(ad: Advertisement): String? {
        val name = ad.name?.uppercase(Locale.ROOT).orEmpty()
        val named = name.startsWith("EUFY") && (name.contains("C20") || name.contains("T9130"))
        return if (COMPANY_ID in ad.manufacturerData || named) "Eufy C20 (T9130)" else null
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        val d = ad.manufacturerData[COMPANY_ID]?.takeIf { it.size >= MIN_LEN }
            ?: ad.manufacturerData.values.firstOrNull { it.size >= MIN_LEN }
            ?: return null
        val flags = d.u8(10)
        if (flags and 0x01 == 0) return null
        val kg = d.u16le(12) / 100.0
        if (!plausible(kg)) return null
        val impedance = if (flags and 0x40 != 0 && d.size >= 19) (d.u16le(17) / 10).takeIf { it > 0 } else null
        return ScaleFrame(kg = kg, stable = false, impedanceOhm = impedance)
    }
}
