package com.resurgeai.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.prefs.PreferencesRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val preferencesRepository: PreferencesRepository,
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

    /** True until we've asked for the notification permission once. */
    private val _askNotifications = MutableStateFlow(false)
    val askNotifications: StateFlow<Boolean> = _askNotifications.asStateFlow()

    init {
        viewModelScope.launch { _askNotifications.value = !preferencesRepository.notificationPromptShown() }
    }

    fun onNotificationPromptShown() {
        _askNotifications.value = false
        viewModelScope.launch { preferencesRepository.setNotificationPromptShown() }
    }
}
