package com.resurgeai.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** The one goal picked in onboarding. Everything else (plan, day list, weekly target) follows from it. */
enum class TrainingGoal(val wire: String, val label: String, val description: String, val legacy: Goal) {
    LOSE_FAT("lose_fat", "Lose fat", "Drop weight and keep your strength", Goal.LOSE_WEIGHT),
    BUILD_MUSCLE("build_muscle", "Build muscle", "Get stronger and add size", Goal.BUILD_MUSCLE),
    RUN_FURTHER("run_further", "Run further", "Build an aerobic base, train for a race", Goal.MOVE_MORE),
    GET_IN_SHAPE("get_in_shape", "Get back in shape", "Move most days and feel better", Goal.MOVE_MORE);

    /** Measured by body weight (start and target), rather than a race or consistency alone. */
    val byWeight: Boolean get() = this == LOSE_FAT || this == BUILD_MUSCLE

    companion object {
        fun fromWire(value: String?): TrainingGoal? = entries.firstOrNull { it.wire == value }
    }
}

enum class TrainingLocation(val wire: String, val label: String) {
    GYM("gym", "Gym"),
    HOME("home", "Home"),
    NONE("none", "No kit");

    companion object {
        fun fromWire(value: String?): TrainingLocation = entries.firstOrNull { it.wire == value } ?: GYM
    }
}

enum class Experience(val wire: String, val label: String) {
    NEW("new", "New"),
    SOME("some", "Some"),
    YEARS("years", "Years");

    companion object {
        fun fromWire(value: String?): Experience = entries.firstOrNull { it.wire == value } ?: SOME
    }
}

/** How strength days are split. [AUTO] lets the plan service choose from the days available. */
enum class WorkoutStyle(val wire: String, val label: String, val description: String) {
    AUTO("auto", "Coach picks", "Best fit for your days"),
    FULL_BODY("full_body", "Full body", "Every session hits everything"),
    PUSH_PULL_LEGS("push_pull_legs", "Push · Pull · Legs", "Chest and shoulders, back, legs"),
    UPPER_LOWER("upper_lower", "Upper · Lower", "Alternate top and bottom"),
    BODY_PART("body_part", "One muscle a day", "Chest day, back day, arm day…");

    companion object {
        fun fromWire(value: String?): WorkoutStyle = entries.firstOrNull { it.wire == value } ?: AUTO
    }
}

/** Where running goes in the week. */
enum class RunMode(val wire: String, val label: String) {
    SEPARATE("separate", "Own days"),
    IN_WORKOUT("in_workout", "In workouts"),
    NONE("none", "No runs");

    companion object {
        fun fromWire(value: String?): RunMode = entries.firstOrNull { it.wire == value } ?: SEPARATE
    }
}

/** "mon" … "sun", as stored and sent to the plan service. */
val DayOfWeek.wire: String get() = name.take(3).lowercase()

fun dayOfWeekFromWire(value: String?): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.wire == value }

/** When and how the person trains. */
data class TrainingPrefs(
    val days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
    val location: TrainingLocation = TrainingLocation.GYM,
    val sessionMin: Int = 45,
    val experience: Experience = Experience.SOME,
    val style: WorkoutStyle = WorkoutStyle.AUTO,
    val runs: RunMode = RunMode.SEPARATE,
) {
    val validDays: Boolean get() = days.size in DAYS_RANGE

    companion object {
        val DAYS_RANGE = 2..6
        val SESSION_CHOICES = listOf(30, 45, 60)
    }
}

/**
 * The major goal: [weeks] long from [startDate]. Weight goals carry [startKg] and [targetKg]; a run
 * goal carries [raceKm]. Each week is a mini-goal on the way.
 */
data class MajorGoal(
    val type: TrainingGoal,
    val weeks: Int,
    val startDate: LocalDate,
    val startKg: Double? = null,
    val targetKg: Double? = null,
    val raceKm: Double? = null,
) {
    val endDate: LocalDate get() = startDate.plusWeeks(weeks.toLong())

    /** Kilos to move in total, or null for goals not measured by weight. */
    val changeKg: Double? get() = if (startKg != null && targetKg != null) abs(targetKg - startKg) else null

    /** Kilos a week the target asks for. */
    val paceKgPerWeek: Double? get() = changeKg?.let { it / weeks }

    /** How realistic the pace is; null when there's no weight target. */
    val paceSignal: PaceSignal?
        get() = paceKgPerWeek?.let { GoalMath.paceSignal(gaining = (targetKg ?: 0.0) > (startKg ?: 0.0), kgPerWeek = it) }

    /** The weight goal the Weight screen tracks, when this goal has a target that differs from the start. */
    fun weightGoal(): WeightGoal? {
        val start = startKg ?: return null
        val target = targetKg ?: return null
        val pace = paceKgPerWeek?.takeIf { it > 0.0 } ?: return null
        return WeightGoal(startKg = start, startDate = startDate, goalKg = target, paceKgPerWeek = pace.coerceAtMost(2.0))
    }

    companion object {
        /** Quick picks; any length in [DURATIONS] can be set as a custom duration. */
        val WEEK_CHOICES = listOf(8, 12, 16)

        /** Up to this many weeks the custom stepper moves a week at a time; past it, a month at a time. */
        const val WEEKLY_UP_TO = 12

        /** Custom durations in weeks: 4–12 weeks, then 3–12 months (a month is 52/12 weeks, rounded). */
        val DURATIONS: List<Int> = (4..WEEKLY_UP_TO).toList() + (3..12).map { monthsToWeeks(it) }

        fun monthsToWeeks(months: Int): Int = Math.round(months * 52 / 12.0).toInt()

        /** "9 weeks" up to [WEEKLY_UP_TO], then "4 months" (for durations off the month grid, the nearest month). */
        fun durationLabel(weeks: Int): String =
            if (weeks <= WEEKLY_UP_TO) "$weeks weeks"
            else Math.round(weeks * 12 / 52.0).toInt().let { if (it == 1) "1 month" else "$it months" }

        /** The duration [steps] notches along [DURATIONS] from [weeks]. */
        fun stepDuration(weeks: Int, steps: Int): Int = when {
            steps > 0 -> DURATIONS.filter { it > weeks }.getOrNull(steps - 1) ?: DURATIONS.last()
            steps < 0 -> DURATIONS.filter { it < weeks }.asReversed().getOrNull(-steps - 1) ?: DURATIONS.first()
            else -> weeks
        }
        val RACES = listOf(5.0 to "5K", 10.0 to "10K", 21.1 to "Half", 42.2 to "Marathon")
        val WEEKS_RANGE = 4..52
    }
}

/** The signal tones: toward the goal / holding or at risk / away from it. */
enum class PaceSignal { UP, MID, DOWN }

object GoalMath {
    /**
     * How sustainable a weekly change in weight is. Losing: up to 0.75 kg a week is steady, up to 1 kg
     * ambitious, more than that too fast. Gaining (muscle): up to 0.35 kg, then 0.5 kg.
     */
    fun paceSignal(gaining: Boolean, kgPerWeek: Double): PaceSignal {
        val (steady, ambitious) = if (gaining) 0.35 to 0.5 else 0.75 to 1.0
        return when {
            kgPerWeek <= steady -> PaceSignal.UP
            kgPerWeek <= ambitious -> PaceSignal.MID
            else -> PaceSignal.DOWN
        }
    }
}

enum class SessionKind(val wire: String) {
    STRENGTH("strength"),
    RUN("run"),
    CARDIO("cardio"),
    MOBILITY("mobility");

    companion object {
        fun fromWire(value: String?): SessionKind? = entries.firstOrNull { it.wire == value }
    }
}

data class PlanExercise(val name: String, val sets: Int, val reps: String, val load: String, val restSec: Int)

/** One weekday of the plan: a session, or a rest day with a short [note] ("Walk 8,000 steps"). */
data class PlanDay(
    val day: DayOfWeek,
    val rest: Boolean,
    val title: String,
    val kind: SessionKind? = null,
    val activityId: String? = null,
    val durationMin: Int? = null,
    val focus: String = "",
    val target: String = "",
    val note: String = "",
    val exercises: List<PlanExercise> = emptyList(),
)

/** A week the person repeats, written by the plan service. [days] is Monday to Sunday. */
data class WorkoutPlan(
    val id: String,
    val name: String,
    val summary: String,
    val days: List<PlanDay>,
    val progression: String,
    val notes: String,
) {
    val sessions: List<PlanDay> get() = days.filterNot { it.rest }

    companion object {
        /** Parses the plan as the service returns it (and as it's stored). Null when it has no usable days. */
        fun fromMap(id: String, map: Map<*, *>): WorkoutPlan? {
            val days = (map["days"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::dayFrom) }
            if (days.isEmpty()) return null
            return WorkoutPlan(
                id = id,
                name = map["name"] as? String ?: "Your plan",
                summary = map["summary"] as? String ?: "",
                days = days.sortedBy { it.day },
                progression = map["progression"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
            )
        }

        private fun dayFrom(m: Map<*, *>): PlanDay? {
            val day = dayOfWeekFromWire(m["day"] as? String) ?: return null
            val rest = m["rest"] as? Boolean ?: true
            return PlanDay(
                day = day,
                rest = rest,
                title = m["title"] as? String ?: if (rest) "Rest" else "Session",
                kind = SessionKind.fromWire(m["kind"] as? String),
                activityId = m["activityId"] as? String,
                durationMin = (m["durationMin"] as? Number)?.toInt(),
                focus = m["focus"] as? String ?: "",
                target = m["target"] as? String ?: "",
                note = m["note"] as? String ?: "",
                exercises = (m["exercises"] as? List<*>).orEmpty().mapNotNull { e ->
                    val x = e as? Map<*, *> ?: return@mapNotNull null
                    PlanExercise(
                        name = x["name"] as? String ?: return@mapNotNull null,
                        sets = (x["sets"] as? Number)?.toInt() ?: 1,
                        reps = x["reps"] as? String ?: "",
                        load = x["load"] as? String ?: "",
                        restSec = (x["restSec"] as? Number)?.toInt() ?: 0,
                    )
                },
            )
        }
    }
}
