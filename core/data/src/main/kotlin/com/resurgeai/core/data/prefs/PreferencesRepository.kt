package com.resurgeai.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
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

    /** User's clock preference, or null to follow the device's 12/24-hour setting. */
    val timeFormat: Flow<TimeFormat?> = dataStore.data.map { prefs ->
        prefs[TIME_FORMAT]?.let { stored -> TimeFormat.entries.firstOrNull { it.name == stored } }
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        dataStore.edit { it[TIME_FORMAT] = format.name }
    }

    suspend fun isHealthBackfillDone(uid: String): Boolean =
        dataStore.data.first()[booleanPreferencesKey("health_backfill_$uid")] == true

    suspend fun setHealthBackfillDone(uid: String) {
        dataStore.edit { it[booleanPreferencesKey("health_backfill_$uid")] = true }
    }

    /** Health Connect IDs of workouts/nights already posted as events (bounded, newest kept). */
    suspend fun postedEventIds(uid: String): Set<String> =
        dataStore.data.first()[stringSetPreferencesKey("posted_events_$uid")].orEmpty()

    suspend fun hasPostedEvents(uid: String): Boolean =
        dataStore.data.first()[booleanPreferencesKey("posted_events_init_$uid")] == true

    suspend fun addPostedEventIds(uid: String, ids: Set<String>) {
        dataStore.edit { prefs ->
            val key = stringSetPreferencesKey("posted_events_$uid")
            // Older than the 48h scan window can't be seen again, so a few hundred IDs is plenty.
            prefs[key] = (prefs[key].orEmpty() + ids).toList().takeLast(MAX_POSTED).toSet()
            prefs[booleanPreferencesKey("posted_events_init_$uid")] = true
        }
    }

    /**
     * Local copy of the habit settings, so the reminder alarm can be rescheduled after a reboot
     * without network or a signed-in Firestore read.
     */
    suspend fun saveHabitSettingsMirror(settings: HabitSettings) {
        dataStore.edit {
            it[HABIT_RESET] = settings.resetTime.toString()
            it[HABIT_REMINDER_ON] = settings.reminderEnabled
            it[HABIT_REMINDER_TIME] = settings.reminderTime.toString()
        }
    }

    suspend fun habitSettingsMirror(): HabitSettings? {
        val prefs = dataStore.data.first()
        val reset = prefs[HABIT_RESET] ?: return null
        return HabitSettings(
            resetTime = LocalTime.parse(reset),
            reminderEnabled = prefs[HABIT_REMINDER_ON] ?: true,
            reminderTime = prefs[HABIT_REMINDER_TIME]?.let(LocalTime::parse) ?: HabitSettings.DEFAULT_REMINDER,
        )
    }

    /** Local copy of the meal reminder times ("off" when disabled), for rescheduling after a reboot. */
    suspend fun saveMealRemindersMirror(reminders: MealReminders) {
        dataStore.edit { prefs ->
            MealReminders.DEFAULT_TIMES.keys.forEach { type ->
                prefs[stringPreferencesKey("meal_reminder_${type.wire}")] = reminders.time(type)?.toString() ?: "off"
            }
        }
    }

    suspend fun mealRemindersMirror(): MealReminders? {
        val prefs = dataStore.data.first()
        if (prefs[stringPreferencesKey("meal_reminder_breakfast")] == null) return null
        return MealReminders.DEFAULT_TIMES.keys.fold(MealReminders()) { acc, type ->
            val value = prefs[stringPreferencesKey("meal_reminder_${type.wire}")]
            acc.with(type, if (value == null || value == "off") null else runCatching { LocalTime.parse(value) }.getOrNull())
        }
    }

    /** Whether we've already asked for the notification permission (ask once). */
    suspend fun notificationPromptShown(): Boolean = dataStore.data.first()[NOTIFICATION_PROMPT] == true

    suspend fun setNotificationPromptShown() {
        dataStore.edit { it[NOTIFICATION_PROMPT] = true }
    }

    /**
     * Insights dismissed on this device. Kept locally too, so a dismissed card stays gone even
     * before the `seen` write reaches the server (or if it's refused).
     */
    val dismissedInsights: Flow<Set<String>> = dataStore.data.map { it[DISMISSED_INSIGHTS].orEmpty() }

    suspend fun dismissInsight(id: String) {
        dataStore.edit { prefs -> prefs[DISMISSED_INSIGHTS] = (prefs[DISMISSED_INSIGHTS].orEmpty() + id).toList().takeLast(MAX_DISMISSED).toSet() }
    }

    /** Habit day whose high-tide celebration was last shown (it plays once per habit day). */
    /** The Health Connect weight import was done or declined, so Today stops offering it. */
    fun weightImportSettled(uid: String): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("weight_import_$uid")] == true }

    suspend fun setWeightImportSettled(uid: String) {
        dataStore.edit { it[booleanPreferencesKey("weight_import_$uid")] = true }
    }

    /** Save new weigh-ins to Health Connect as well. */
    val weightWriteToHealthConnect: Flow<Boolean> = dataStore.data.map { it[WEIGHT_WRITE_HC] == true }

    suspend fun setWeightWriteToHealthConnect(on: Boolean) {
        dataStore.edit { it[WEIGHT_WRITE_HC] = on }
    }

    /** Block screenshots and the recents preview on progress photo screens. On unless turned off. */
    val progressScreenshotBlock: Flow<Boolean> = dataStore.data.map { it[PROGRESS_SECURE] != false }

    suspend fun setProgressScreenshotBlock(on: Boolean) {
        dataStore.edit { it[PROGRESS_SECURE] = on }
    }

    /** The "track it visually too" prompt on the Weight screen was dismissed. */
    val progressPromptDismissed: Flow<Boolean> = dataStore.data.map { it[PROGRESS_PROMPT_DISMISSED] == true }

    suspend fun dismissProgressPrompt() {
        dataStore.edit { it[PROGRESS_PROMPT_DISMISSED] = true }
    }

    suspend fun highTideShownOn(): LocalDate? =
        dataStore.data.first()[HIGH_TIDE_SHOWN]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun setHighTideShown(day: LocalDate) {
        dataStore.edit { it[HIGH_TIDE_SHOWN] = day.toString() }
    }

    private companion object {
        val HIGH_TIDE_SHOWN = stringPreferencesKey("high_tide_shown")
        val PROGRESS_SECURE = booleanPreferencesKey("progress_secure")
        val PROGRESS_PROMPT_DISMISSED = booleanPreferencesKey("progress_prompt_dismissed")
        val WEIGHT_WRITE_HC = booleanPreferencesKey("weight_write_hc")
        val DISMISSED_INSIGHTS = stringSetPreferencesKey("dismissed_insights")
        const val MAX_DISMISSED = 100
        const val MAX_POSTED = 300
        val NOTIFICATION_PROMPT = booleanPreferencesKey("notification_prompt_shown")
        val HABIT_RESET = stringPreferencesKey("habit_reset")
        val HABIT_REMINDER_ON = booleanPreferencesKey("habit_reminder_on")
        val HABIT_REMINDER_TIME = stringPreferencesKey("habit_reminder_time")
        val THEME = stringPreferencesKey("theme")
        val TIME_FORMAT = stringPreferencesKey("time_format")
    }
}
