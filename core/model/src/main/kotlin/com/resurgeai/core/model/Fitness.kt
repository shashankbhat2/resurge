package com.resurgeai.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** How an activity is measured; decides which fields the log form shows and what a card summarises. */
enum class ActivityCategory(val label: String) {
    /** Duration, distance, elevation, heart rate. Pace or speed is derived. */
    DISTANCE("Cardio"),

    /** Exercises with sets × reps × weight. */
    STRENGTH("Strength"),

    /** Laps × pool length, or distance. */
    SWIM("Swimming"),

    /** Duration and effort (RPE). */
    TIMED("Classes & sports"),

    /** A count of reps plus duration. */
    COUNT("Reps"),
}

/**
 * One entry in the activity catalog. [icon] is a key resolved to a Tabler drawable by the design
 * system; [healthConnect] lists Health Connect exercise-type keys (e.g. "running") that map here.
 */
data class Activity(
    val id: String,
    val name: String,
    val category: ActivityCategory,
    val icon: String,
    /** Metabolic equivalent, for calorie estimates on manual logs. */
    val met: Double,
    val aliases: List<String> = emptyList(),
    val healthConnect: List<String> = emptyList(),
    /** Distance activities where speed (km/h) reads better than pace (min/km). */
    val showSpeed: Boolean = false,
)

object ActivityCatalog {
    private fun distance(id: String, name: String, icon: String, met: Double, aliases: List<String> = emptyList(), hc: List<String> = emptyList(), speed: Boolean = false) =
        Activity(id, name, ActivityCategory.DISTANCE, icon, met, aliases, hc, speed)

    private fun strength(id: String, name: String, met: Double = 5.0, aliases: List<String> = emptyList(), hc: List<String> = emptyList()) =
        Activity(id, name, ActivityCategory.STRENGTH, "barbell", met, aliases, hc)

    private fun timed(id: String, name: String, icon: String, met: Double, aliases: List<String> = emptyList(), hc: List<String> = emptyList()) =
        Activity(id, name, ActivityCategory.TIMED, icon, met, aliases, hc)

    private fun count(id: String, name: String, icon: String, met: Double, aliases: List<String> = emptyList(), hc: List<String> = emptyList()) =
        Activity(id, name, ActivityCategory.COUNT, icon, met, aliases, hc)

    val all: List<Activity> = listOf(
        // Cardio
        distance("run", "Run", "run", 9.8, listOf("jog", "jogging", "running"), listOf("running")),
        distance("treadmill", "Treadmill run", "treadmill", 9.0, listOf("indoor run"), listOf("running_treadmill")),
        distance("walk", "Walk", "walk", 3.5, listOf("walking", "stroll"), listOf("walking")),
        distance("hike", "Hike", "trekking", 6.0, listOf("hiking", "trek", "trail"), listOf("hiking")),
        distance("cycle", "Cycle", "bike", 7.5, listOf("bike", "biking", "cycling", "ride"), listOf("biking"), speed = true),
        distance("indoor_cycle", "Indoor cycle", "bike", 7.0, listOf("spin", "spinning", "stationary bike"), listOf("biking_stationary"), speed = true),
        distance("row", "Row", "kayak", 7.0, listOf("rowing", "erg", "rowing machine"), listOf("rowing", "rowing_machine")),
        distance("elliptical", "Elliptical", "activity", 5.0, listOf("cross trainer"), listOf("elliptical")),
        distance("stairs", "Stair climber", "stairs", 9.0, listOf("stairmaster", "stair climbing", "steps"), listOf("stair_climbing", "stair_climbing_machine")),
        distance("wheelchair", "Wheelchair", "activity", 3.5, emptyList(), listOf("wheelchair")),
        distance("skate", "Skating", "skateboarding", 7.0, listOf("roller skating", "ice skating", "skateboard", "inline"), listOf("skating", "ice_skating", "roller_skating", "skateboarding")),
        distance("ski", "Skiing", "mountain", 7.0, listOf("snowboard", "snowboarding", "cross country"), listOf("skiing", "snowboarding", "snowshoeing")),
        distance("paddle", "Paddling", "kayak", 5.0, listOf("kayak", "canoe", "sup", "paddleboard"), listOf("paddling", "sailing")),

        // Swimming
        Activity("swim_pool", "Pool swim", ActivityCategory.SWIM, "swimming", 7.0, listOf("swimming", "laps"), listOf("swimming_pool")),
        Activity("swim_open", "Open water swim", ActivityCategory.SWIM, "swimming", 8.0, listOf("sea swim", "lake"), listOf("swimming_open_water")),

        // Strength
        strength("weights", "Weight training", 5.0, listOf("gym", "lifting", "weights", "strength"), listOf("weightlifting", "strength_training")),
        strength("bodyweight", "Bodyweight", 4.0, listOf("calisthenics", "push ups", "pull ups"), listOf("calisthenics")),
        strength("crossfit", "CrossFit", 8.0, listOf("wod", "functional"), emptyList()),
        strength("powerlifting", "Powerlifting", 6.0, listOf("squat", "bench", "deadlift"), emptyList()),

        // Classes & sports
        timed("hiit", "HIIT", "flame", 8.0, listOf("interval", "circuit", "tabata"), listOf("high_intensity_interval_training", "exercise_class", "bootcamp")),
        timed("yoga", "Yoga", "yoga", 3.0, listOf("vinyasa", "hatha"), listOf("yoga")),
        timed("pilates", "Pilates", "stretching", 3.5, listOf("reformer"), listOf("pilates")),
        timed("stretch", "Stretching", "stretching", 2.5, listOf("mobility", "flexibility", "cool down"), listOf("stretching", "guided_breathing")),
        timed("dance", "Dance", "music", 5.5, listOf("zumba", "dancing", "aerobics"), listOf("dancing")),
        timed("martial_arts", "Martial arts", "karate", 10.0, listOf("karate", "judo", "taekwondo", "bjj", "mma"), listOf("martial_arts")),
        timed("boxing", "Boxing", "karate", 9.0, listOf("kickboxing", "sparring", "punching bag"), listOf("boxing")),
        timed("football", "Football", "ball_football", 7.0, listOf("soccer", "futsal"), listOf("soccer", "football_american", "football_australian", "rugby")),
        timed("cricket", "Cricket", "cricket", 5.0, listOf("batting", "bowling nets"), listOf("cricket")),
        timed("basketball", "Basketball", "ball_basketball", 6.5, listOf("hoops"), listOf("basketball")),
        timed("tennis", "Tennis", "ball_tennis", 7.3, listOf("padel", "squash"), listOf("tennis", "squash", "racquetball")),
        timed("badminton", "Badminton", "ball_tennis", 5.5, listOf("shuttle"), listOf("badminton")),
        timed("table_tennis", "Table tennis", "ping_pong", 4.0, listOf("ping pong"), listOf("table_tennis")),
        timed("volleyball", "Volleyball", "ball_volleyball", 4.0, listOf("beach volleyball"), listOf("volleyball")),
        timed("golf", "Golf", "golf", 4.8, listOf("driving range"), listOf("golf")),
        timed("climbing", "Climbing", "mountain", 8.0, listOf("bouldering", "rock climbing"), listOf("rock_climbing")),
        timed("other", "Other workout", "activity", 5.0, listOf("workout", "exercise", "training"), listOf("other_workout")),

        // Reps
        count("jump_rope", "Jump rope", "jump_rope", 11.0, listOf("skipping"), emptyList()),
        count("burpees", "Burpees", "flame", 8.0, emptyList(), emptyList()),
        count("pushups", "Push-ups", "barbell", 4.0, listOf("press ups", "pushups"), emptyList()),
        count("squats", "Air squats", "barbell", 5.0, listOf("bodyweight squats"), emptyList()),
        count("situps", "Sit-ups", "barbell", 3.8, listOf("crunches", "abs"), emptyList()),
    )

    private val byId = all.associateBy { it.id }
    private val byHealthConnect = all.flatMap { a -> a.healthConnect.map { it to a } }.toMap()

    fun byId(id: String?): Activity = byId[id] ?: byId.getValue("other")

    /** Maps a Health Connect exercise-type key (e.g. "running") to a catalog activity. */
    fun forHealthConnect(key: String?): Activity = key?.let { byHealthConnect[it] } ?: byId("other")

    /** Name, alias and category match, best first; blank returns everything. */
    fun search(query: String): List<Activity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        fun score(a: Activity): Int = when {
            a.name.lowercase().startsWith(q) -> 0
            a.name.lowercase().contains(q) -> 1
            a.aliases.any { it.startsWith(q) } -> 2
            a.aliases.any { it.contains(q) } -> 3
            a.category.label.lowercase().contains(q) -> 4
            else -> -1
        }
        return all.map { it to score(it) }.filter { it.second >= 0 }.sortedBy { it.second }.map { it.first }
    }
}

enum class UnitSystem(val wire: String, val label: String) {
    METRIC("metric", "Metric (kg, km)"),
    IMPERIAL("imperial", "Imperial (lb, mi)");

    companion object {
        fun fromWire(value: String?): UnitSystem = entries.firstOrNull { it.wire == value } ?: METRIC
    }
}

/** One set of a strength exercise. Weight is null for bodyweight. */
data class StrengthSet(val reps: Int, val weightKg: Double? = null)

data class StrengthExercise(val name: String, val sets: List<StrengthSet>) {
    val volumeKg: Double get() = sets.sumOf { (it.weightKg ?: 0.0) * it.reps }
}

/** Everything a workout can measure; each field is null when not recorded. Stored in metric. */
data class WorkoutMetricsInput(
    val distanceM: Double? = null,
    val elevationM: Double? = null,
    val avgHr: Int? = null,
    val kcal: Int? = null,
    val laps: Int? = null,
    val poolLengthM: Int? = null,
    val reps: Int? = null,
    /** Rate of perceived exertion, 1–10. */
    val rpe: Int? = null,
    val exercises: List<StrengthExercise> = emptyList(),
)

enum class WorkoutSource { HEALTH_CONNECT, MANUAL }

/**
 * A card in the Fitness feed. Health Connect sessions come straight from the device, with any
 * edits the user made layered on top ([edited]); manual workouts live only in Firestore.
 */
data class Workout(
    /** Firestore document id: the Health Connect record id for device workouts, otherwise generated. */
    val id: String,
    val source: WorkoutSource,
    val activityId: String,
    val title: String?,
    val start: Instant,
    val end: Instant,
    val metrics: WorkoutMetricsInput,
    val notes: String? = null,
    val sourceName: String? = null,
    val edited: Boolean = false,
) {
    val activity: Activity get() = ActivityCatalog.byId(activityId)
    val duration: Duration get() = Duration.between(start, end)
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate = start.atZone(zone).toLocalDate()
    val displayName: String get() = title?.takeIf { it.isNotBlank() } ?: activity.name
}

/** What the log form edits; turned into a [Workout] on save. */
data class WorkoutDraft(
    val activityId: String,
    val start: Instant,
    val durationMin: Int,
    val metrics: WorkoutMetricsInput = WorkoutMetricsInput(),
    val title: String? = null,
    val notes: String? = null,
)

object FitnessMath {
    /** Calories from MET × weight × hours. */
    fun estimateKcal(activity: Activity, durationMin: Int, weightKg: Double?): Int =
        (activity.met * (weightKg ?: 70.0) * durationMin / 60.0).roundToInt()

    /** Seconds per km, or null for very short or missing distances. */
    fun paceSecPerKm(distanceM: Double?, duration: Duration): Long? =
        distanceM?.takeIf { it >= 200 && !duration.isZero }?.let { (duration.seconds / (it / 1000.0)).toLong() }

    fun speedKmh(distanceM: Double?, duration: Duration): Double? =
        distanceM?.takeIf { it > 0 && !duration.isZero }?.let { it / 1000.0 / (duration.seconds / 3600.0) }

    /** Swim distance from laps × pool length when distance itself isn't set. */
    fun swimDistanceM(m: WorkoutMetricsInput): Double? =
        m.distanceM ?: if (m.laps != null && m.poolLengthM != null) (m.laps * m.poolLengthM).toDouble() else null

    const val KG_PER_LB = 0.45359237
    const val M_PER_MI = 1609.344
    const val M_PER_FT = 0.3048
    const val M_PER_YD = 0.9144
}
