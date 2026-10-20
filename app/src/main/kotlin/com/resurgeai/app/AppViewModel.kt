package com.resurgeai.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.progress.ProgressRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import android.content.Context
import com.resurgeai.app.main.FoodSignal
import com.resurgeai.app.main.ProgressSignal
import com.resurgeai.app.main.WeightSignal
import com.resurgeai.app.notifications.HabitReminder
import com.resurgeai.app.notifications.MealReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val progressRepository: ProgressRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionRepository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    val theme: StateFlow<ThemePreference> = preferencesRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)

    val timeFormat: StateFlow<TimeFormat?> = preferencesRepository.timeFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Bumped when a notification asks to open Coach › Insights; MainScreen navigates on change. */
    private val _openInsights = MutableStateFlow(0)
    val openInsights: StateFlow<Int> = _openInsights.asStateFlow()

    fun requestOpenInsights() = _openInsights.update { it + 1 }

    /** Bumped when the habit reminder asks to open the Habits tab. */
    private val _openHabits = MutableStateFlow(0)
    val openHabits: StateFlow<Int> = _openHabits.asStateFlow()

    fun requestOpenHabits() = _openHabits.update { it + 1 }

    /** Meal reminder taps: mode "scan" / "type" opens the log screen, null opens the Food tab. */
    private val _openFood = MutableStateFlow(FoodSignal())
    val openFood: StateFlow<FoodSignal> = _openFood.asStateFlow()

    fun requestOpenFood(mode: String?) = _openFood.update { FoodSignal(it.count + 1, mode) }

    /** The launcher shortcut (capture = true) or a reel notification: open Progress. */
    private val _openProgress = MutableStateFlow(ProgressSignal())
    val openProgress: StateFlow<ProgressSignal> = _openProgress.asStateFlow()

    fun requestOpenProgress(capture: Boolean, reelId: String? = null) = _openProgress.update { ProgressSignal(it.count + 1, capture, reelId) }

    /** Scale notifications and the weight widget. */
    private val _openWeight = MutableStateFlow(WeightSignal())
    val openWeight: StateFlow<WeightSignal> = _openWeight.asStateFlow()

    fun requestOpenWeight(log: Boolean = false, smartScale: Boolean = false) =
        _openWeight.update { WeightSignal(it.count + 1, log, smartScale) }

    /** True until we've asked for the notification permission once. */
    private val _askNotifications = MutableStateFlow(false)
    val askNotifications: StateFlow<Boolean> = _askNotifications.asStateFlow()

    init {
        viewModelScope.launch { _askNotifications.value = !preferencesRepository.notificationPromptShown() }
        // Keep the local habit reminder in step with the user's habit settings.
        viewModelScope.launch {
            sessionRepository.session
                .filterIsInstance<SessionState.Ready>()
                .map { it.profile.habitSettings }
                .distinctUntilChanged()
                .collect { settings ->
                    preferencesRepository.saveHabitSettingsMirror(settings)
                    HabitReminder.schedule(appContext, settings)
                }
        }
        // Back up any progress photos still waiting, once per signed-in user per launch.
        viewModelScope.launch {
            sessionRepository.session
                .filterIsInstance<SessionState.Ready>()
                .map { it.profile.uid }
                .distinctUntilChanged()
                .collect { progressRepository.scheduleUpload() }
        }
        // And the meal reminders with the user's meal reminder times.
        viewModelScope.launch {
            sessionRepository.session
                .filterIsInstance<SessionState.Ready>()
                .map { it.profile.mealReminders }
                .distinctUntilChanged()
                .collect { reminders ->
                    preferencesRepository.saveMealRemindersMirror(reminders)
                    MealReminder.scheduleAll(appContext, reminders)
                }
        }
    }

    fun onNotificationPromptShown() {
        _askNotifications.value = false
        viewModelScope.launch { preferencesRepository.setNotificationPromptShown() }
    }
}
