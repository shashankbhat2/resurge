package com.resurgeai.core.scale.protocol

/** A recognised scale: which protocol speaks for it and what to call it. */
data class ScaleMatch(val protocol: ScaleProtocol, val model: String)

/** Every protocol Resurge understands, most specific first so a generic one can't shadow it. */
object ScaleProtocols {
    val all: List<ScaleProtocol> = listOf(
        MiScaleProtocol,
        // Exact "PICOOC-L" name; its embedded MAC also trips Scaleup's company-id match.
        PicoocMiniLiteProtocol,
        YunmaiXProtocol,
        // Name only; shares company id 0xFF64 with other scales.
        SinocareProtocol,
        AiLinkBroadcastProtocol,
        QnBroadcastProtocol,
        OkOkProtocol,
        OkOkUnflaggedProtocol,
        AaaxProtocol,
        // Company id 0x06D0 ends in 0xD0, which Scaleup also claims.
        EtekcityFit8SProtocol,
        EufyC20Protocol,
        // Generic: any company id whose low byte is 0xD0 or 0xE0.
        ScaleupProtocol,
    )

    fun byId(id: String): ScaleProtocol? = all.firstOrNull { it.id == id }

    fun match(ad: Advertisement): ScaleMatch? =
        all.firstNotNullOfOrNull { protocol -> protocol.recognise(ad)?.let { ScaleMatch(protocol, it) } }

    /** Family names for the "Works with" list, deduplicated, in order. */
    val families: List<String> get() = all.map { it.family }.distinct()
}
