package com.resurgeai.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.auth.AuthRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.TimeFormat
import com.resurgeai.core.model.UserProfile
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val healthConnect: HealthConnectManager,
    private val userRepository: UserRepository,
) : ViewModel() {
    fun healthConnectSettingsIntent() = healthConnect.settingsIntent()

    val state: StateFlow<ProfileUiState> = combine(
        sessionRepository.session,
        preferencesRepository.themePreference,
        preferencesRepository.timeFormat,
    ) { session, theme, timeFormat ->
        ProfileUiState(profile = (session as? SessionState.Ready)?.profile, theme = theme, timeFormat = timeFormat)
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

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
