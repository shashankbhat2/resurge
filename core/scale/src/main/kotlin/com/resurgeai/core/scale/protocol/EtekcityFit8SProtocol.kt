package com.resurgeai.core.scale.protocol

/**
 * Etekcity Fit 8S. Nameless; recognised by Etekcity's company id 0x06D0 together with service
 * 0xFFD0, since either alone is shared with devices this can't read. 20-byte manufacturer data:
 *
 * `[0 header][1-6 MAC]…[10-12 weight LE, grams][13-14 impedance LE, Ω][15 stable = 1][16 unit]`
 *
 * Weight is always in grams whatever the display unit. Ported from openScale's
 * `EtekcityFit8SHandler` (GPL-3.0), itself based on ronnnnnnnnnnnnn/etekcity_esf551_ble.
 */
object EtekcityFit8SProtocol : ScaleProtocol {
    override val id = "etekcity_fit8s"
    override val family = "Etekcity Fit 8S"

    private const val COMPANY_ID = 0x06D0
    private const val SIZE = 20
    private val SCALE_SERVICE = uuid16(0xFFD0)

    override fun recognise(ad: Advertisement): String? =
        if (COMPANY_ID in ad.manufacturerData && SCALE_SERVICE in ad.serviceUuids) "Etekcity Fit 8S" else null

    override fun decode(ad: Advertisement): ScaleFrame? {
        val d = ad.manufacturerData[COMPANY_ID]?.takeIf { it.size == SIZE } ?: return null
        val kg = d.u24le(10) / 1000.0
        if (!plausible(kg)) return null
        return ScaleFrame(kg = kg, stable = d.u8(15) == 1, impedanceOhm = d.u16le(13).takeIf { it != 0 })
    }
}
