package com.resurgeai.core.scale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resurgeai.core.scale.protocol.ScaleProtocol
import com.resurgeai.core.scale.protocol.ScaleProtocols
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A smart scale this phone listens for. Its Bluetooth address is what identifies it. */
data class PairedScale(
    val address: String,
    val protocolId: String,
    val model: String,
    val pairedAt: Instant,
) {
    val protocol: ScaleProtocol? get() = ScaleProtocols.byId(protocolId)
}

data class ScaleSettings(
    val scales: List<PairedScale> = emptyList(),
    /** Log readings whenever someone steps on, even with the app closed. */
    val autoLog: Boolean = true,
    /** Ask before logging a reading far from the user's recent weight (a shared scale). */
    val askWhenUnusual: Boolean = true,
    val lastReadingAt: Instant? = null,
)

/**
 * Paired scales and their settings. Device-local on purpose: a Bluetooth address only means
 * something to the phone that heard it, and each phone in a household pairs on its own.
 */
@Singleton
class ScaleStore @Inject constructor(@ApplicationContext context: Context) {
    private val store: DataStore<Preferences> = context.scaleDataStore

    val settings: Flow<ScaleSettings> = store.data.map { prefs ->
        ScaleSettings(
            scales = prefs[SCALES].orEmpty().mapNotNull(::decode).sortedBy { it.pairedAt },
            autoLog = prefs[AUTO_LOG] ?: true,
            askWhenUnusual = prefs[ASK_WHEN_UNUSUAL] ?: true,
            lastReadingAt = prefs[LAST_READING]?.let(Instant::ofEpochMilli),
        )
    }

    suspend fun current(): ScaleSettings = settings.first()

    suspend fun pair(scale: PairedScale) {
        store.edit { prefs ->
            val others = prefs[SCALES].orEmpty().filterNot { decode(it)?.address == scale.address }
            prefs[SCALES] = (others + encode(scale)).toSet()
        }
    }

    suspend fun forget(address: String) {
        store.edit { prefs -> prefs[SCALES] = prefs[SCALES].orEmpty().filterNot { decode(it)?.address == address }.toSet() }
    }

    suspend fun setAutoLog(on: Boolean) {
        store.edit { it[AUTO_LOG] = on }
    }

    suspend fun setAskWhenUnusual(on: Boolean) {
        store.edit { it[ASK_WHEN_UNUSUAL] = on }
    }

    suspend fun markReading(at: Instant) {
        store.edit { it[LAST_READING] = at.toEpochMilli() }
    }

    private fun encode(s: PairedScale) = listOf(s.address, s.protocolId, s.model.replace(SEP, " "), s.pairedAt.toEpochMilli()).joinToString(SEP)

    private fun decode(raw: String): PairedScale? {
        val parts = raw.split(SEP)
        if (parts.size != 4) return null
        return PairedScale(parts[0], parts[1], parts[2], Instant.ofEpochMilli(parts[3].toLongOrNull() ?: return null))
    }

    private companion object {
        const val SEP = "\t"
        val SCALES = stringSetPreferencesKey("scales")
        val AUTO_LOG = booleanPreferencesKey("auto_log")
        val ASK_WHEN_UNUSUAL = booleanPreferencesKey("ask_when_unusual")
        val LAST_READING = longPreferencesKey("last_reading_ms")
    }
}

private val Context.scaleDataStore: DataStore<Preferences> by preferencesDataStore(name = "resurge_scales")
