package com.resurgeai.core.scale.protocol

import kotlin.math.abs
import kotlin.math.roundToInt

/** A settled reading, ready to save. */
data class SettledReading(
    val kg: Double,
    val impedanceOhm: Int?,
    val atMs: Long,
)

/**
 * Turns a stream of frames from one scale into at most one reading per time someone stands on it.
 *
 * - A frame marked stable settles the reading straight away.
 * - For scales that never say so ([ScaleProtocol.reportsStability] false), the reading settles
 *   once the same weight has been seen [REPEATS] times over at least [HOLD_MS].
 * - After settling, further frames are ignored until the scale has been quiet for [IDLE_MS] or
 *   shows a clearly different weight, so a scale that keeps broadcasting its final number for
 *   a while doesn't log it twice.
 *
 * Pure and clock-free: callers pass the time, which keeps it testable.
 */
class WeighSession(private val reportsStability: Boolean) {

    private var lastFrameAtMs = Long.MIN_VALUE
    private var settledKg: Double? = null

    private var candidateKg: Double? = null
    private var candidateSinceMs = 0L
    private var candidateCount = 0

    /** The latest weight seen, settled or not; handy for a live readout. */
    var liveKg: Double? = null
        private set

    fun offer(frame: ScaleFrame, atMs: Long): SettledReading? {
        if (lastFrameAtMs != Long.MIN_VALUE && atMs - lastFrameAtMs > IDLE_MS) reset()
        lastFrameAtMs = atMs
        liveKg = frame.kg

        settledKg?.let { done ->
            if (abs(frame.kg - done) < NEW_PERSON_KG) return null
            reset()
            liveKg = frame.kg
        }

        val settled = if (reportsStability) frame.stable else holdsSteady(frame.kg, atMs)
        if (!settled) return null

        settledKg = frame.kg
        return SettledReading(kg = round2(frame.kg), impedanceOhm = frame.impedanceOhm, atMs = atMs)
    }

    private fun holdsSteady(kg: Double, atMs: Long): Boolean {
        val candidate = candidateKg
        if (candidate == null || abs(kg - candidate) > STEADY_KG) {
            candidateKg = kg
            candidateSinceMs = atMs
            candidateCount = 1
            return false
        }
        candidateCount++
        return candidateCount >= REPEATS && atMs - candidateSinceMs >= HOLD_MS
    }

    private fun reset() {
        settledKg = null
        candidateKg = null
        candidateCount = 0
        liveKg = null
    }

    companion object {
        const val IDLE_MS = 20_000L
        const val NEW_PERSON_KG = 2.0
        const val STEADY_KG = 0.05
        const val REPEATS = 4
        const val HOLD_MS = 1_500L

        private fun round2(kg: Double) = (kg * 100).roundToInt() / 100.0
    }
}
