package com.resurgeai.core.scale.protocol

/** Hex string to bytes; whitespace is ignored. */
internal fun hex(s: String): ByteArray =
    s.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

internal const val KG_TOLERANCE = 0.005
