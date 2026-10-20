package com.resurgeai.core.scale.protocol

/**
 * Scaleup-protocol scales (advertised as "da"). The 11-byte manufacturer structure is
 * `[flag][weight BE, 0.01 kg][impedance BE][MAC ×6]`, so Android's company id is
 * `weightMsb << 8 | flag` and the value starts at the weight's low byte.
 *
 * Flag 0xD0 is measuring, 0xE0 stabilised. The impedance unit is unknown, so it is not reported.
 * Matching any company id ending in 0xD0/0xE0 is broad (Etekcity's 0x06D0 and PICOOC's embedded
 * MAC both hit it), so this must stay last in [ScaleProtocols.all]. Ported from openScale's
 * `ScaleupHandler` (GPL-3.0).
 */
object ScaleupProtocol : ScaleProtocol {
    override val id = "scaleup"
    override val family = "Scale Up"

    private const val FLAG_MEASURING = 0xD0
    private const val FLAG_STABLE = 0xE0
    private const val MIN_LEN = 9

    override fun recognise(ad: Advertisement): String? {
        val named = ad.name?.trim().equals("da", ignoreCase = true)
        return if (named || ad.manufacturerData.keys.any { isFlag(it and 0xFF) }) "Scale Up" else null
    }

    override fun decode(ad: Advertisement): ScaleFrame? {
        for ((company, d) in ad.manufacturerData) {
            val flag = company and 0xFF
            if (d.size < MIN_LEN || !isFlag(flag)) continue
            val kg = ((company and 0xFF00) or d.u8(0)) / 100.0
            if (!plausible(kg)) continue
            return ScaleFrame(kg = kg, stable = flag == FLAG_STABLE)
        }
        return null
    }

    private fun isFlag(flag: Int) = flag == FLAG_MEASURING || flag == FLAG_STABLE
}
