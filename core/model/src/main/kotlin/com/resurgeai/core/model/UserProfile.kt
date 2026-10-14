package com.resurgeai.core.model

data class UserProfile(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val goals: Set<Goal>,
    val targets: Targets,
    val onboardingComplete: Boolean,
    val habitSettings: HabitSettings = HabitSettings(),
    val coachStyle: CoachStyle = CoachStyle.FRIEND,
    val units: UnitSystem = UnitSystem.METRIC,
    val mealReminders: MealReminders = MealReminders(),
) {
    val firstName: String?
        get() = displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
}

data class Targets(
    val calories: Int = DEFAULT_CALORIES,
    val waterMl: Int = DEFAULT_WATER_ML,
    /** Time asleep per night. */
    val sleepMinutes: Int = DEFAULT_SLEEP_MINUTES,
    val steps: Int = DEFAULT_STEPS,
) {
    companion object {
        const val DEFAULT_CALORIES = 2000
        const val DEFAULT_WATER_ML = 2500
        const val DEFAULT_SLEEP_MINUTES = 7 * 60
        const val DEFAULT_STEPS = 10_000

        /** Settings ranges; sleep moves in half hours. */
        val SLEEP_RANGE = 4 * 60..11 * 60
        const val SLEEP_STEP = 30
        val STEPS_RANGE = 2_000..30_000
        const val STEPS_STEP = 500

        /** Below this, setting a sleep goal shows a gentle note (most adults need 7–9 hours). */
        const val RECOMMENDED_MIN_SLEEP_MINUTES = 7 * 60

        /** A gentle note for a sleep goal under what most adults need, or null. Never blocks the choice. */
        fun sleepGoalNote(minutes: Int): String? =
            if (minutes < RECOMMENDED_MIN_SLEEP_MINUTES) {
                "Most adults need 7 to 9 hours. A lower goal is fine as a first step, and you can raise it later."
            } else null

        /** "7h", "6h 30m". */
        fun formatSleep(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
    }
}

/** "Coaching style" in Settings: how the coach talks in chat and insights. */
enum class CoachStyle(val wire: String, val label: String, val description: String) {
    FRIEND("friend", "Friend", "Warm, encouraging and patient"),
    COMMANDER("commander", "Commander", "Direct, no-nonsense and demanding");

    companion object {
        fun fromWire(value: String?): CoachStyle = entries.firstOrNull { it.wire == value } ?: FRIEND
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
