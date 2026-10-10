package com.resurgeai.core.model

/** Top-level app state that decides which flow is shown. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsOnboarding(val profile: UserProfile) : SessionState
    data class Ready(val profile: UserProfile) : SessionState
}

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Clock style for times shown in the app. Null preference = follow the device setting. */
enum class TimeFormat { H12, H24 }
