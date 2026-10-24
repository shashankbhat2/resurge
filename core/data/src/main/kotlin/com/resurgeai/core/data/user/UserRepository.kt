package com.resurgeai.core.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.resurgeai.core.data.auth.AuthUser
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.Experience
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.MealReminders
import com.resurgeai.core.model.RunMode
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.TrainingLocation
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.UserProfile
import com.resurgeai.core.model.WorkoutStyle
import com.resurgeai.core.model.dayOfWeekFromWire
import com.resurgeai.core.model.wire
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
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

    /**
     * Saves onboarding in one atomic batch: the major goal (and the weight goal it implies), how the
     * person trains, the accepted plan, and what a day needs for the streak.
     */
    suspend fun completeOnboarding(
        uid: String,
        goal: MajorGoal,
        training: TrainingPrefs,
        planId: String?,
        targets: Targets,
        dayGoals: DayGoals,
    ) {
        val batch = firestore.batch()
        batch.set(
            doc(uid),
            buildMap {
                put("goals", listOf(goal.type.legacy.name))
                put("primaryGoal", goal.type.wire)
                put("goal", goal.toMap())
                put("training", training.toMap())
                planId?.let { put("activePlanId", it) }
                put("targets", targets.toMap())
                put("dayGoals", dayGoals.toMap())
                put("onboardingComplete", true)
                put("onboardedAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            },
            SetOptions.merge(),
        )
        goal.weightGoal()?.let { w ->
            batch.set(
                doc(uid).collection("weightGoal").document("current"),
                mapOf(
                    "startKg" to w.startKg,
                    "startDate" to w.startDate.toString(),
                    "goalKg" to w.goalKg,
                    "paceKgPerWeek" to w.paceKgPerWeek,
                    "mode" to w.mode.wire,
                    "milestonesCelebrated" to 0,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
    }

    /** Switches to a rebuilt plan, saving the schedule it was built for. */
    suspend fun setActivePlan(uid: String, planId: String, training: TrainingPrefs) {
        doc(uid).update(
            mapOf(
                "activePlanId" to planId,
                "training" to training.toMap(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
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

    suspend fun updateDayGoals(uid: String, goals: DayGoals) {
        doc(uid).update(
            mapOf(
                "dayGoals" to goals.toMap(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun DayGoals.toMap() = mapOf(
        "excludedHabits" to excludedHabits.toList(),
        "sleep" to sleep,
        "steps" to steps,
        "meals" to meals,
        "water" to water,
        "weeklyDays" to weeklyDays,
    )

    private fun MajorGoal.toMap() = mapOf(
        "type" to type.wire,
        "weeks" to weeks,
        "startDate" to startDate.toString(),
        "startKg" to startKg,
        "targetKg" to targetKg,
        "raceKm" to raceKm,
    )

    private fun TrainingPrefs.toMap() = mapOf(
        "days" to days.sorted().map { it.wire },
        "location" to location.wire,
        "sessionMin" to sessionMin,
        "experience" to experience.wire,
        "style" to style.wire,
        "runs" to runs.wire,
    )

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
            dayGoals = dayGoals(),
            majorGoal = majorGoal(),
            training = training(),
            activePlanId = getString("activePlanId"),
        )
    }

    private fun DocumentSnapshot.majorGoal(): MajorGoal? {
        val m = get("goal") as? Map<*, *> ?: return null
        return MajorGoal(
            type = TrainingGoal.fromWire(m["type"] as? String) ?: return null,
            weeks = (m["weeks"] as? Number)?.toInt() ?: return null,
            startDate = (m["startDate"] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null,
            startKg = (m["startKg"] as? Number)?.toDouble(),
            targetKg = (m["targetKg"] as? Number)?.toDouble(),
            raceKm = (m["raceKm"] as? Number)?.toDouble(),
        )
    }

    private fun DocumentSnapshot.training(): TrainingPrefs? {
        val m = get("training") as? Map<*, *> ?: return null
        return TrainingPrefs(
            days = (m["days"] as? List<*>).orEmpty().mapNotNull { dayOfWeekFromWire(it as? String) }.toSet(),
            location = TrainingLocation.fromWire(m["location"] as? String),
            sessionMin = (m["sessionMin"] as? Number)?.toInt() ?: TrainingPrefs().sessionMin,
            experience = Experience.fromWire(m["experience"] as? String),
            style = WorkoutStyle.fromWire(m["style"] as? String),
            runs = RunMode.fromWire(m["runs"] as? String),
        )
    }

    private fun DocumentSnapshot.dayGoals(): DayGoals {
        @Suppress("UNCHECKED_CAST")
        val m = get("dayGoals") as? Map<String, Any?> ?: return DayGoals()
        val d = DayGoals()
        @Suppress("UNCHECKED_CAST")
        return DayGoals(
            excludedHabits = (m["excludedHabits"] as? List<String>).orEmpty().toSet(),
            sleep = m["sleep"] as? Boolean ?: d.sleep,
            steps = m["steps"] as? Boolean ?: d.steps,
            meals = ((m["meals"] as? Number)?.toInt() ?: d.meals).coerceIn(DayGoals.MEALS_RANGE),
            water = m["water"] as? Boolean ?: d.water,
            weeklyDays = ((m["weeklyDays"] as? Number)?.toInt() ?: d.weeklyDays).coerceIn(DayGoals.WEEKLY_RANGE),
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
