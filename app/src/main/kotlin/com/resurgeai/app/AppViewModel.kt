package com.resurgeai.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.ThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionRepository.session
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    val theme: StateFlow<ThemePreference> = preferencesRepository.themePreference
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePreference.SYSTEM)
}
