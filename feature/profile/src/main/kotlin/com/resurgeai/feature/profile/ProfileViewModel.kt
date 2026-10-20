package com.resurgeai.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.auth.AuthRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import com.resurgeai.core.model.UserProfile
import com.resurgeai.core.scale.ScaleStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile? = null,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** Null = follow the device's 12/24-hour setting. */
    val timeFormat: TimeFormat? = null,
    /** The paired smart scale's model, "2 scales" for several, null when none is set up. */
    val smartScale: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val healthConnect: HealthConnectManager,
    private val userRepository: UserRepository,
    scaleStore: ScaleStore,
) : ViewModel() {
    fun healthConnectSettingsIntent() = healthConnect.settingsIntent()

    val state: StateFlow<ProfileUiState> = combine(
        sessionRepository.session,
        preferencesRepository.themePreference,
        preferencesRepository.timeFormat,
        scaleStore.settings,
    ) { session, theme, timeFormat, scales ->
        ProfileUiState(
            profile = (session as? SessionState.Ready)?.profile,
            theme = theme,
            timeFormat = timeFormat,
            smartScale = when (scales.scales.size) {
                0 -> null
                1 -> scales.scales.single().model
                else -> "${scales.scales.size} scales"
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setThemePreference(theme) }
    }

    fun setTimeFormat(format: TimeFormat) {
        viewModelScope.launch { preferencesRepository.setTimeFormat(format) }
    }

    /** Saves reset / reminder settings; the app reschedules the local reminder when they change. */
    fun setHabitSettings(settings: HabitSettings) {
        val uid = state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { userRepository.updateHabitSettings(uid, settings) } }
    }

    fun setCoachStyle(style: CoachStyle) {
        val uid = state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { userRepository.updateCoachStyle(uid, style) } }
    }

    fun setUnits(units: UnitSystem) {
        val uid = state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { userRepository.updateUnits(uid, units) } }
    }

    /** Saves meal reminder times; the app reschedules the local alarms when they change. */
    fun setMealReminders(reminders: MealReminders) {
        val uid = state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { userRepository.updateMealReminders(uid, reminders) } }
    }

    fun setTargets(targets: Targets) {
        val uid = state.value.profile?.uid ?: return
        viewModelScope.launch { runCatching { userRepository.updateTargets(uid, targets) } }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
