package com.resurgeai.core.scale

import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.scale.protocol.Advertisement
import com.resurgeai.core.scale.protocol.SettledReading
import com.resurgeai.core.scale.protocol.WeighSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/** Someone is standing on [scale] and it currently reads [kg]. */
data class LiveWeighing(val scale: PairedScale, val kg: Double, val settled: Boolean)

/** What became of a settled reading. */
sealed interface ScaleOutcome {
    val scale: PairedScale
    val kg: Double
    val at: Instant

    data class Saved(override val scale: PairedScale, override val kg: Double, override val at: Instant, val weighInId: String) : ScaleOutcome

    /** Far from this user's recent weight, so probably someone else on a shared scale. Not saved yet. */
    data class Unusual(
        override val scale: PairedScale,
        override val kg: Double,
        override val at: Instant,
        val impedanceOhm: Int?,
        val recentKg: Double,
    ) : ScaleOutcome

    /** The same reading was logged moments ago. */
    data class Repeat(override val scale: PairedScale, override val kg: Double, override val at: Instant) : ScaleOutcome
}

/**
 * Turns advertisements from paired scales into weigh-ins. Every way of listening (the screen,
 * the weigh-in service, background wake-ups) feeds this one engine, so a reading heard twice is
 * still logged once.
 */
@Singleton
class WeighInEngine @Inject constructor(
    private val store: ScaleStore,
    private val weights: WeightRepository,
) {
    private val mutex = Mutex()
    private val sessions = HashMap<String, WeighSession>()

    private val _live = MutableStateFlow<LiveWeighing?>(null)
    val live: StateFlow<LiveWeighing?> = _live.asStateFlow()

    private val _outcomes = MutableSharedFlow<ScaleOutcome>(extraBufferCapacity = 8)
    val outcomes: SharedFlow<ScaleOutcome> = _outcomes.asSharedFlow()

    private val _uiWatching = MutableStateFlow(0)

    /**
     * How many screens are showing live readings right now. While above zero the app shows each
     * weigh-in in the UI, so it needn't also post a notification.
     */
    val uiWatching: StateFlow<Int> = _uiWatching.asStateFlow()

    /** Call when a screen starts showing live readings; pair every call with [unwatchUi]. */
    fun watchUi() {
        _uiWatching.update { it + 1 }
    }

    fun unwatchUi() {
        _uiWatching.update { (it - 1).coerceAtLeast(0) }
    }

    /** Feeds one advertisement in; returns the outcome when it completes a reading. */
    suspend fun offer(ad: Advertisement, nowMs: Long = System.currentTimeMillis()): ScaleOutcome? {
        val settings = store.current()
        val scale = settings.scales.firstOrNull { it.address.equals(ad.address, ignoreCase = true) } ?: return null
        val protocol = scale.protocol ?: return null
        // Below this it's a child, a pet or a bag of shopping; above, not a person's weight either.
        val frame = protocol.decode(ad)?.takeIf { it.kg in MIN_PERSON_KG..MAX_PERSON_KG } ?: return null

        val settled = mutex.withLock {
            val session = sessions.getOrPut(scale.address) { WeighSession(protocol.reportsStability) }
            session.offer(frame, nowMs).also { _live.value = LiveWeighing(scale, frame.kg, settled = it != null) }
        } ?: return null

        return decide(scale, settled, settings.askWhenUnusual).also { _outcomes.emit(it) }
    }

    /** Logs a reading the user confirmed was theirs. */
    suspend fun confirm(unusual: ScaleOutcome.Unusual): ScaleOutcome {
        val reading = SettledReading(unusual.kg, unusual.impedanceOhm, unusual.at.toEpochMilli())
        return save(unusual.scale, reading).also { _outcomes.emit(it) }
    }

    fun clearLive() {
        _live.value = null
    }

    private suspend fun decide(scale: PairedScale, reading: SettledReading, askWhenUnusual: Boolean): ScaleOutcome {
        val at = Instant.ofEpochMilli(reading.atMs)
        if (askWhenUnusual) {
            val recent = weights.recentWeighIns(at.minus(Duration.ofDays(RECENT_DAYS))).firstOrNull()?.kg
            if (recent != null && isUnusual(reading.kg, recent)) {
                return ScaleOutcome.Unusual(scale, reading.kg, at, reading.impedanceOhm, recent)
            }
        }
        return save(scale, reading)
    }

    private suspend fun save(scale: PairedScale, reading: SettledReading): ScaleOutcome {
        val at = Instant.ofEpochMilli(reading.atMs)
        val id = weights.saveFromScale(reading.kg, at, scale.model, manufacturer = null, impedanceOhm = reading.impedanceOhm)
            ?: return ScaleOutcome.Repeat(scale, reading.kg, at)
        store.markReading(at)
        return ScaleOutcome.Saved(scale, reading.kg, at, id)
    }

    companion object {
        const val RECENT_DAYS = 45L
        const val MIN_PERSON_KG = 20.0
        const val MAX_PERSON_KG = 300.0

        /** Further than this from the last month and a half's latest reading reads as someone else. */
        fun isUnusual(kg: Double, recentKg: Double): Boolean = abs(kg - recentKg) > max(3.0, recentKg * 0.05)
    }
}
