package com.resurgeai.core.scale.protocol

import java.util.UUID

/**
 * One BLE advertisement, stripped of Android types so protocols can be tested on the JVM.
 *
 * [manufacturerData] is keyed by the 16-bit company id with those two bytes already
 * removed from the value, exactly as Android's `ScanRecord.getManufacturerSpecificData()`
 * hands them over.
 */
class Advertisement(
    val address: String,
    val name: String?,
    val rssi: Int = -60,
    val manufacturerData: Map<Int, ByteArray> = emptyMap(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val serviceUuids: List<UUID> = emptyList(),
)

/** What a scale said in one advertisement. [kg] is always kilograms, whatever the display unit. */
data class ScaleFrame(
    val kg: Double,
    val stable: Boolean,
    val impedanceOhm: Int? = null,
)

/**
 * A family of scales that broadcast their reading in advertisements, so no connection or
 * pairing with the scale itself is ever needed.
 *
 * Implementations are stateless: the same advertisement always decodes to the same frame.
 * Settling a reading across many advertisements is [WeighSession]'s job.
 *
 * Several protocols are ported from openScale (GPL-3.0, https://github.com/oliexdev/openScale),
 * which is the same licence as Resurge.
 */
interface ScaleProtocol {
    /** Stable wire id, persisted with a paired scale. Never rename one. */
    val id: String

    /** Shown in "Works with" lists, e.g. "Xiaomi Mi Scale". */
    val family: String

    /**
     * False when the scale never marks a reading as settled; [WeighSession] then waits for the
     * same weight to repeat instead.
     */
    val reportsStability: Boolean get() = true

    /** The model name to show if [ad] comes from a scale of this family, otherwise null. */
    fun recognise(ad: Advertisement): String?

    /** The reading in [ad], or null when it carries none (idle, wrong frame, implausible weight). */
    fun decode(ad: Advertisement): ScaleFrame?
}

/** Shorthand for the Bluetooth SIG base UUID with a 16-bit id, e.g. `uuid16(0x181B)`. */
fun uuid16(short: Int): UUID =
    UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short and 0xFFFF))

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
internal fun ByteArray.u16le(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.u16be(i: Int): Int = (u8(i) shl 8) or u8(i + 1)

internal const val KG_PER_LB = 0.45359237
internal const val MIN_KG = 2.0
internal const val MAX_KG = 300.0

internal fun plausible(kg: Double): Boolean = kg in MIN_KG..MAX_KG

internal fun ByteArray.u24le(i: Int): Int = u16le(i) or (u8(i + 2) shl 16)

/** The six octets of a colon-separated MAC such as "C0:FF:EE:12:34:56", or null if malformed. */
internal fun macOctets(address: String): IntArray? {
    val parts = address.split(":")
    if (parts.size != 6) return null
    val octets = parts.map { it.toIntOrNull(16) ?: return null }
    return octets.toIntArray()
}
