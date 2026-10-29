package com.resurgeai.core.data.cardio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.resurgeai.core.model.CardioPointsEngine
import com.resurgeai.core.model.DailyCardioPoints
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Small derived-only Preferences cache. noBackupFilesDir also excludes device transfer. */
@Singleton
class CardioPointsCache internal constructor(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(PreferenceDataStoreFactory.create {
        File(context.noBackupFilesDir, "cardio_points.preferences_pb")
    })

    suspend fun load(uid: String, zone: String, access: String, first: LocalDate, today: LocalDate): CardioCacheSnapshot {
        val prefs = store.data.first()
        if (prefs[USER] != uid || prefs[ZONE] != zone || prefs[ACCESS] != access || prefs[VERSION] != CardioPointsEngine.VERSION.toString()) {
            store.edit { it.clear() }
            return CardioCacheSnapshot(emptyList(), null)
        }
        val days = prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith("day_") || value !is String) null else CardioCacheCodec.decode(value)
        }.filter { it.summary.date in first..today }
        return CardioCacheSnapshot(days, prefs[FULL_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    }

    suspend fun save(uid: String, zone: String, access: String, days: List<CachedCardioDay>, fullDate: LocalDate?) {
        store.edit { prefs ->
            prefs.clear()
            prefs[USER] = uid; prefs[ZONE] = zone; prefs[ACCESS] = access
            prefs[VERSION] = CardioPointsEngine.VERSION.toString()
            fullDate?.let { prefs[FULL_DATE] = it.toString() }
            days.forEach { prefs[stringPreferencesKey("day_${it.summary.date}")] = CardioCacheCodec.encode(it) }
        }
    }

    suspend fun clearForUser(uid: String) {
        store.edit { if (it[USER] == uid) it.clear() }
    }

    private companion object {
        val USER = stringPreferencesKey("uid"); val ZONE = stringPreferencesKey("zone")
        val ACCESS = stringPreferencesKey("access"); val VERSION = stringPreferencesKey("version")
        val FULL_DATE = stringPreferencesKey("full_date")
    }
}
