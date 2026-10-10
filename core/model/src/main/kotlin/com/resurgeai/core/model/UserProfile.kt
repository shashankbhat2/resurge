package com.resurgeai.core.model

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val goals: Set<Goal>,
    val targets: Targets,
    val onboardingComplete: Boolean,
) {
    val firstName: String?
        get() = displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
}

data class Targets(
    val calories: Int = DEFAULT_CALORIES,
    val waterMl: Int = DEFAULT_WATER_ML,
) {
    companion object {
        const val DEFAULT_CALORIES = 2000
        const val DEFAULT_WATER_ML = 2500
    }
}

enum class Goal(val label: String, val description: String) {
    LOSE_WEIGHT("Lose weight", "Steady, sustainable fat loss"),
    BUILD_MUSCLE("Build muscle", "Strength and protein focus"),
    SLEEP_BETTER("Sleep better", "Consistent, restful nights"),
    MOVE_MORE("Move more", "More steps and active days"),
    EAT_BETTER("Eat better", "Balanced meals and macros"),
    HYDRATE("Stay hydrated", "Hit your water target daily"),
}
