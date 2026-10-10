package com.resurgeai.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.resurgeai.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Device-local preferences (not synced to Firestore). */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themePreference: Flow<ThemePreference> = dataStore.data.map { prefs ->
        prefs[THEME]?.let { stored -> ThemePreference.entries.firstOrNull { it.name == stored } }
            ?: ThemePreference.SYSTEM
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[THEME] = preference.name }
    }

    suspend fun isHealthBackfillDone(uid: String): Boolean =
        dataStore.data.first()[booleanPreferencesKey("health_backfill_$uid")] == true

    suspend fun setHealthBackfillDone(uid: String) {
        dataStore.edit { it[booleanPreferencesKey("health_backfill_$uid")] = true }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
    }
}
