package com.resurgeai.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.auth.AuthRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val authRepository: AuthRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    fun healthConnectSettingsIntent() = healthConnect.settingsIntent()

    val state: StateFlow<ProfileUiState> = combine(
        sessionRepository.session,
        preferencesRepository.themePreference,
    ) { session, theme ->
        ProfileUiState(profile = (session as? SessionState.Ready)?.profile, theme = theme)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { preferencesRepository.setThemePreference(theme) }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
