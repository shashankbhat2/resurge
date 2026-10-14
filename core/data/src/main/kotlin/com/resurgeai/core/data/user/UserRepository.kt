package com.resurgeai.core.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.auth.AuthUser
import com.resurgeai.core.data.habits.HabitsRepository
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStreaks
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes `users/{uid}` in the `resurge` Firestore database. */
@Singleton
class UserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun doc(uid: String) = firestore.collection(USERS).document(uid)

    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = doc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    /**
     * Keeps identity fields in sync with the Google account. Uses a merge write (no read),
     * so it also works offline and never overwrites goals, targets or onboarding state.
     */
    suspend fun syncIdentity(user: AuthUser) {
        doc(user.uid).set(
            mapOf(
                "displayName" to user.displayName,
                "email" to user.email,
                "photoUrl" to user.photoUrl,
                // Lets the backend schedule the Saturday weekly review in the user's local time.
                "timezone" to ZoneId.systemDefault().id,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    /** Saves onboarding answers and the starter habits in one atomic batch. */
    suspend fun completeOnboarding(
        uid: String,
        goals: Set<Goal>,
        targets: Targets,
        habits: List<HabitDraft>,
        settings: HabitSettings,
    ) {
        val batch = firestore.batch()
        batch.set(
            doc(uid),
            mapOf(
                "goals" to goals.map { it.name },
                "targets" to targets.toMap(),
                "habitResetTime" to settings.resetTime.format(HHMM),
                "habitReminder" to settings.reminderMap(),
                "onboardingComplete" to true,
                "onboardedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        )
        val today = HabitStreaks.habitDay(Instant.now(), ZoneId.systemDefault(), settings.resetTime)
        habits.forEachIndexed { i, h ->
            batch.set(doc(uid).collection("habits").document(), HabitsRepository.newHabitMap(h, i, today))
        }
        batch.commit().await()
    }

    suspend fun updateCoachStyle(uid: String, style: CoachStyle) {
        doc(uid).update(mapOf("coachStyle" to style.wire, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun updateHabitSettings(uid: String, settings: HabitSettings) {
        doc(uid).update(
            mapOf(
                "habitResetTime" to settings.resetTime.format(HHMM),
                "habitReminder" to settings.reminderMap(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun updateUnits(uid: String, units: UnitSystem) {
        doc(uid).update(mapOf("units" to units.wire, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    /** Stored as { breakfast: "08:30" | "off", lunch: …, dinner: … }. */
    suspend fun updateMealReminders(uid: String, reminders: MealReminders) {
        val map = MealReminders.DEFAULT_TIMES.keys.associate { type -> type.wire to (reminders.time(type)?.format(HHMM) ?: OFF) }
        doc(uid).update(mapOf("mealReminders" to map, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    private fun HabitSettings.reminderMap() = mapOf("enabled" to reminderEnabled, "time" to reminderTime.format(HHMM))

    suspend fun updateTargets(uid: String, targets: Targets) {
        doc(uid).update(
            mapOf(
                "targets" to targets.toMap(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun Targets.toMap() = mapOf("calories" to calories, "waterMl" to waterMl, "sleepMinutes" to sleepMinutes, "steps" to steps)

    private fun DocumentSnapshot.toProfile(): UserProfile {
        @Suppress("UNCHECKED_CAST")
        val targets = get("targets") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val goals = (get("goals") as? List<String>).orEmpty()
            .mapNotNull { name -> Goal.entries.firstOrNull { it.name == name } }
            .toSet()

        return UserProfile(
            uid = id,
            displayName = getString("displayName"),
            email = getString("email"),
            photoUrl = getString("photoUrl"),
            goals = goals,
            targets = Targets(
                calories = (targets?.get("calories") as? Number)?.toInt() ?: Targets.DEFAULT_CALORIES,
                waterMl = (targets?.get("waterMl") as? Number)?.toInt() ?: Targets.DEFAULT_WATER_ML,
                sleepMinutes = (targets?.get("sleepMinutes") as? Number)?.toInt() ?: Targets.DEFAULT_SLEEP_MINUTES,
                steps = (targets?.get("steps") as? Number)?.toInt() ?: Targets.DEFAULT_STEPS,
            ),
            onboardingComplete = getBoolean("onboardingComplete") ?: false,
            habitSettings = habitSettings(),
            coachStyle = CoachStyle.fromWire(getString("coachStyle")),
            units = UnitSystem.fromWire(getString("units")),
            mealReminders = mealReminders(),
        )
    }

    private fun DocumentSnapshot.mealReminders(): MealReminders {
        @Suppress("UNCHECKED_CAST")
        val map = get("mealReminders") as? Map<String, Any?> ?: return MealReminders()
        return MealReminders.DEFAULT_TIMES.entries.fold(MealReminders()) { acc, (type, default) ->
            val value = map[type.wire] as? String
            acc.with(type, if (value == OFF) null else parseTime(value) ?: default)
        }
    }

    private fun DocumentSnapshot.habitSettings(): HabitSettings {
        @Suppress("UNCHECKED_CAST")
        val reminder = get("habitReminder") as? Map<String, Any?>
        return HabitSettings(
            resetTime = parseTime(getString("habitResetTime")) ?: HabitSettings.DEFAULT_RESET,
            reminderEnabled = reminder?.get("enabled") as? Boolean ?: true,
            reminderTime = parseTime(reminder?.get("time") as? String) ?: HabitSettings.DEFAULT_REMINDER,
        )
    }

    private fun parseTime(value: String?): LocalTime? = value?.let { runCatching { LocalTime.parse(it, HHMM) }.getOrNull() }

    private companion object {
        const val USERS = "users"
        const val OFF = "off"
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
