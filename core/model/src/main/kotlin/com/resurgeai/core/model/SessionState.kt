package com.resurgeai.core.model

/** Top-level app state that decides which flow is shown. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsOnboarding(val profile: UserProfile) : SessionState
    data class Ready(val profile: UserProfile) : SessionState
}

enum class ThemePreference { SYSTEM, LIGHT, DARK }
