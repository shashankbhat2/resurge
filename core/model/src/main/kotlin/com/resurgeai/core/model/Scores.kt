package com.resurgeai.core.model

import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The three daily scores on Today. Each one answers a single question and is built from base metrics. */
enum class ScoreKind(val label: String, val question: String) {
    RECOVERY("Recovery", "Is my body ready today?"),
    EFFORT("Effort", "How much did I do against what today asked?"),
    FUEL("Fuel", "Did I eat for my goal?");

    companion object {
        fun fromName(name: String?): ScoreKind? = entries.firstOrNull { it.name == name }
    }
}

/** How a part's raw figure is written. */
enum class PartUnit {
    MINUTES, BPM, MS, STEPS, KCAL, GRAMS, ML, MEALS, LOAD, SCORE;

    fun format(value: Double): String = when (this) {
        MINUTES -> {
            val m = value.roundToInt()
            when {
                m >= 60 && m % 60 == 0 -> "${m / 60} h"
                m >= 60 -> "${m / 60}h ${m % 60}m"
                else -> "$m min"
            }
        }
        BPM -> "${value.roundToInt()} bpm"
        MS -> "${value.roundToInt()} ms"
        STEPS -> String.format(Locale.getDefault(), "%,d", value.roundToLong())
        KCAL -> String.format(Locale.getDefault(), "%,d", value.roundToLong())
        GRAMS -> "${value.roundToInt()} g"
        ML -> if (value >= 1000) String.format(Locale.getDefault(), "%.1f L", value / 1000) else "${value.roundToInt()} ml"
        MEALS -> if (value == value.roundToInt().toDouble()) "${value.roundToInt()}" else String.format(Locale.getDefault(), "%.1f", value)
        LOAD, SCORE -> "${value.roundToInt()}"
    }
}

/**
 * One base metric inside a score. [level] is how well it did, 0 to 1 (null when it wasn't recorded,
 * and then it's left out of the score). [weight] is its share once missing parts are left out.
 */
data class ScorePart(
    val key: String,
    val label: String,
    val raw: Double?,
    val unit: PartUnit,
    val note: String,
    val level: Double?,
    val weight: Double,
    /** What it's measured against, e.g. the calorie target; null when it's measured against your normal. */
    val target: Double? = null,
    /** The vital that shows this part's own history, when there is one. */
    val metric: HealthMetric? = null,
    /** Another score this part comes from (yesterday's Effort). */
    val opens: ScoreKind? = null,
) {
    val value: String? get() = raw?.let(unit::format)

    /** Points this part moved the score from an ordinary day. */
    val impact: Int get() = level?.let { ((it - ScoreMath.ORDINARY) * weight * 100).roundToInt() } ?: 0

    val signal: PaceSignal? get() = level?.let {
        when {
            it >= 0.8 -> PaceSignal.UP
            it >= 0.5 -> PaceSignal.MID
            else -> PaceSignal.DOWN
        }
    }
}

/**
 * A day's score. [value] is null when too little was recorded ([missing] says what's needed).
 * [soFar] marks today's Effort and Fuel before the day is done, drawn in grey until it's in range.
 */
data class Score(
    val kind: ScoreKind,
    val date: LocalDate,
    val value: Int?,
    val signal: PaceSignal?,
    val soFar: Boolean,
    val parts: List<ScorePart>,
    /** Two or three words for the ring ("Ready to train"). */
    val headline: String,
    /** One plain sentence on why. */
    val reason: String,
    val missing: String? = null,
    /** Fewer than a week of history, so "normal" is still a guess. */
    val learning: Boolean = false,
    /** Effort only: the range today asked for. */
    val band: IntRange? = null,
)

data class RecoveryInputs(
    val date: LocalDate,
    val asleepMin: Int?,
    val sleepGoalMin: Int,
    val restingHr: Double?,
    val restingHrNormal: Double?,
    val hrv: Double?,
    val hrvNormal: Double?,
    /** Minutes last night's bedtime was from the week's average; null with under three nights. */
    val bedtimeShiftMin: Int?,
    val yesterdayEffort: Int?,
    val yesterdayBand: IntRange?,
    /** Days of history behind the normals. */
    val historyDays: Int,
)

data class EffortInputs(
    val date: LocalDate,
    val load: Double,
    /** Average load on days with a workout over the last four weeks; null with fewer than three. */
    val loadNormal: Double?,
    val workoutMin: Int,
    val steps: Long?,
    val stepsGoal: Int,
    /** The plan's session for the day, null on a rest day or without a plan. */
    val session: PlanDay?,
    /** Planned session done (1), partly (0.5) or not (0). */
    val sessionDone: Double,
    val recovery: Int?,
    val soFar: Boolean,
    val healthReady: Boolean = true,
)

data class FuelInputs(
    val date: LocalDate,
    val kcal: Double,
    val kcalTarget: Int,
    val proteinG: Double,
    val proteinTarget: Int,
    val meals: Int,
    val mealsGoal: Int,
    val waterMl: Int,
    val waterTarget: Int,
    /** Share of the day's eating that should be done by now: 1 for a finished day. */
    val dayShare: Double = 1.0,
)

/** Pure scoring for Recovery, Effort and Fuel. Each part scores 0–1 and the score is their weighted mean. */
object ScoreMath {
    /** A part at this level leaves the score where an ordinary day would. */
    const val ORDINARY = 0.7

    /** Below this share of the weight recorded, the score isn't shown. */
    const val MIN_COVERAGE = 0.6

    /** Normals need this many days before they're trusted. */
    const val LEARNING_DAYS = 7

    const val READY = 67
    const val CAREFUL = 34

    /** Over the top of Effort's band by this much counts as well over. */
    const val WELL_OVER = 15

    fun band(value: Int): PaceSignal = when {
        value >= READY -> PaceSignal.UP
        value >= CAREFUL -> PaceSignal.MID
        else -> PaceSignal.DOWN
    }

    /** What Effort should land in: higher on a session day, lower when Recovery is down. */
    fun effortBand(sessionDay: Boolean, recovery: Int?): IntRange {
        val base = if (sessionDay) 55..80 else 15..40
        val shift = when {
            recovery == null || recovery >= READY -> 0
            recovery >= CAREFUL -> if (sessionDay) 10 else 0
            else -> if (sessionDay) 25 else 5
        }
        return (base.first - shift)..(base.last - shift)
    }

    /** Minutes times intensity: logged effort (RPE) first, then heart rate against resting, else moderate. */
    fun load(minutes: Double, rpe: Int?, avgHr: Int?, restingHr: Double?): Double {
        val intensity = when {
            rpe != null -> rpe.coerceIn(1, 10) / 10.0
            avgHr != null -> {
                val rest = restingHr ?: 60.0
                ((avgHr - rest) / (MAX_HR - rest)).coerceIn(0.3, 1.0)
            }
            else -> 0.6
        }
        return minutes * intensity
    }

    /** Protein for the day: 1.6 g/kg when building muscle, 1.2 g/kg otherwise, to the nearest 5 g. */
    fun proteinTarget(weightKg: Double?, goal: TrainingGoal?): Int {
        val perKg = if (goal == TrainingGoal.BUILD_MUSCLE) 1.6 else 1.2
        return ((weightKg ?: 70.0) * perKg / 5).roundToInt() * 5
    }

    /** How much of the day's eating should be done by [minuteOfDay]: none before 7:00, all by 20:00. */
    fun dayShare(minuteOfDay: Int): Double = ((minuteOfDay - 7 * 60) / (13.0 * 60)).coerceIn(0.3, 1.0)

    fun recovery(r: RecoveryInputs): Score {
        val learning = r.historyDays < LEARNING_DAYS
        val sleep = r.asleepMin?.let { min ->
            val ratio = min.toDouble() / r.sleepGoalMin
            val diff = min - r.sleepGoalMin
            ScorePart(
                "sleep", "Sleep", min.toDouble(), PartUnit.MINUTES,
                note = when {
                    diff >= 0 -> "${PartUnit.MINUTES.format(diff.toDouble())} over your ${PartUnit.MINUTES.format(r.sleepGoalMin.toDouble())} goal"
                    else -> "${PartUnit.MINUTES.format(-diff.toDouble())} under your ${PartUnit.MINUTES.format(r.sleepGoalMin.toDouble())} goal"
                },
                level = ((ratio - 0.5) / 0.5).coerceIn(0.0, 1.0),
                weight = 0.35, target = r.sleepGoalMin.toDouble(), metric = HealthMetric.SLEEP,
            )
        }
        val rhr = r.restingHr?.let { now ->
            val normal = r.restingHrNormal
            ScorePart(
                "restingHr", "Resting heart rate", now, PartUnit.BPM,
                note = if (normal == null) "Learning your normal" else versusNormal(now - normal, "bpm", normal),
                level = if (normal == null) ORDINARY else (ORDINARY + (normal - now) * 0.06).coerceIn(0.0, 1.0),
                weight = 0.25, metric = HealthMetric.HEART_RATE,
            )
        }
        val hrv = r.hrv?.let { now ->
            val normal = r.hrvNormal
            ScorePart(
                "hrv", "Heart rate variability", now, PartUnit.MS,
                note = if (normal == null) "Learning your normal" else versusNormal(now - normal, "ms", normal),
                level = if (normal == null || normal <= 0) ORDINARY else (ORDINARY + (now - normal) / normal * 2).coerceIn(0.0, 1.0),
                weight = 0.15, metric = HealthMetric.HEART_RATE,
            )
        }
        val consistency = r.bedtimeShiftMin?.let { shift ->
            ScorePart(
                "consistency", "Sleep consistency", shift.toDouble(), PartUnit.MINUTES,
                note = if (shift <= 30) "Bedtime close to your usual" else "Bedtime ${PartUnit.MINUTES.format(shift.toDouble())} from your usual",
                level = (1 - (shift - 15) / 90.0).coerceIn(0.0, 1.0),
                weight = 0.15, metric = HealthMetric.SLEEP,
            )
        }
        val yesterday = if (r.yesterdayEffort != null && r.yesterdayBand != null) {
            val over = r.yesterdayEffort - r.yesterdayBand.last
            ScorePart(
                "yesterday", "Yesterday’s effort", r.yesterdayEffort.toDouble(), PartUnit.SCORE,
                note = if (over > WELL_OVER) "Well over its target" else "No effect",
                // Only a day well over its target costs anything; an easy day doesn't add.
                level = if (over > WELL_OVER) (ORDINARY - (over - WELL_OVER) / 40.0).coerceIn(0.0, ORDINARY) else ORDINARY,
                weight = 0.10, opens = ScoreKind.EFFORT,
            )
        } else null

        val parts = listOfNotNull(sleep, rhr, hrv, consistency, yesterday)
        if (sleep == null) return empty(ScoreKind.RECOVERY, r.date, parts, "Needs last night’s sleep", learning)
        return finish(ScoreKind.RECOVERY, r.date, parts, learning, soFar = false) { value, scored ->
            val signal = band(value)
            Triple(
                signal,
                when (signal) {
                    PaceSignal.UP -> "Ready to train"
                    PaceSignal.MID -> "Train, but easier"
                    PaceSignal.DOWN -> "Rest or walk"
                },
                sentence(scored, RECOVERY_GOOD, RECOVERY_BAD, "An ordinary night for your body."),
            )
        }
    }

    fun effort(e: EffortInputs): Score {
        val band = effortBand(e.session != null, e.recovery)
        if (!e.healthReady) return empty(ScoreKind.EFFORT, e.date, emptyList(), "Needs Health Connect", false, band)
        val normal = e.loadNormal ?: DEFAULT_LOAD
        val parts = listOfNotNull(
            ScorePart(
                "load", "Workout load", e.load, PartUnit.LOAD,
                note = when {
                    e.load <= 0 -> "No workout"
                    e.loadNormal == null -> "Learning your normal"
                    else -> "${(e.load / normal * 100).roundToInt()}% of a usual workout day"
                },
                level = (e.load / (normal * 1.6)).coerceIn(0.0, 1.0),
                weight = 0.50, metric = HealthMetric.EXERCISE,
            ),
            ScorePart(
                "active", "Active minutes", e.workoutMin.toDouble(), PartUnit.MINUTES,
                note = "From workouts",
                level = (e.workoutMin / ACTIVE_MINUTES).coerceIn(0.0, 1.0),
                weight = 0.20, target = ACTIVE_MINUTES, metric = HealthMetric.EXERCISE,
            ),
            e.steps?.let { steps ->
                ScorePart(
                    "steps", "Steps", steps.toDouble(), PartUnit.STEPS,
                    note = "of ${PartUnit.STEPS.format(e.stepsGoal.toDouble())}",
                    level = (steps.toDouble() / e.stepsGoal).coerceIn(0.0, 1.0),
                    weight = 0.15, target = e.stepsGoal.toDouble(), metric = HealthMetric.STEPS,
                )
            },
            e.session?.let { s ->
                ScorePart(
                    "session", "Planned session", e.sessionDone, PartUnit.SCORE,
                    note = when {
                        e.sessionDone >= 1 -> "${s.title} done"
                        e.sessionDone > 0 -> "${s.title}, partly"
                        e.soFar -> "${s.title} to go"
                        else -> "${s.title} missed"
                    },
                    level = e.sessionDone,
                    weight = 0.15,
                )
            },
        )
        return finish(ScoreKind.EFFORT, e.date, parts, learning = e.loadNormal == null, soFar = e.soFar, band = band) { value, _ ->
            val over = value - band.last
            val signal = when {
                value in band -> PaceSignal.UP
                over > WELL_OVER && (e.recovery ?: 100) < CAREFUL -> PaceSignal.DOWN
                over > 0 -> PaceSignal.UP
                else -> PaceSignal.MID
            }
            val headline = when {
                value in band -> "In range"
                over > 0 -> "Over today’s target"
                e.soFar -> e.session?.takeIf { e.sessionDone < 1 }?.let { "${it.title} to go" } ?: "Under target so far"
                else -> "Under target"
            }
            val reason = when {
                signal == PaceSignal.DOWN -> "Well over today’s target while Recovery was low. Keep tomorrow easy."
                e.session != null && e.sessionDone >= 1 -> "You did today’s ${e.session.title}." + if (over > 0) " More than today asked." else ""
                e.session != null && e.soFar -> "${e.session.title} is still to go; it’s most of today’s target."
                e.session != null -> "${e.session.title} was planned and not done."
                value in band -> "A lighter day, as planned."
                over > 0 -> "A rest day that turned into a hard one."
                else -> "A quiet day. A walk would bring it into range."
            }
            Triple(signal, headline, reason)
        }
    }

    fun fuel(f: FuelInputs): Score {
        val share = f.dayShare.coerceIn(0.3, 1.0)
        val soFar = share < 1.0
        if (f.meals == 0) return empty(ScoreKind.FUEL, f.date, emptyList(), if (soFar) "Log a meal to see Fuel" else "No meals logged", false)
        val expectedKcal = f.kcalTarget * share
        val kcalDev = when {
            f.kcal > f.kcalTarget -> f.kcal / f.kcalTarget - 1
            f.kcal < expectedKcal -> 1 - f.kcal / expectedKcal
            else -> 0.0
        }
        val proteinNeed = f.proteinTarget * share
        val mealsNeed = (f.mealsGoal.coerceAtLeast(1) * share).coerceAtLeast(1.0)
        val waterNeed = f.waterTarget * share
        val parts = listOf(
            ScorePart(
                "calories", "Calories", f.kcal, PartUnit.KCAL,
                note = when {
                    kcalDev <= 0.1 && soFar -> "On pace for ${PartUnit.KCAL.format(f.kcalTarget.toDouble())}"
                    kcalDev <= 0.1 -> "Within 10% of ${PartUnit.KCAL.format(f.kcalTarget.toDouble())}"
                    f.kcal > f.kcalTarget -> "${PartUnit.KCAL.format(f.kcal - f.kcalTarget)} over"
                    else -> "${PartUnit.KCAL.format(expectedKcal - f.kcal)} under ${if (soFar) "for now" else "target"}"
                },
                level = if (kcalDev <= 0.1) 1.0 else (1 - (kcalDev - 0.1) / 0.4).coerceIn(0.0, 1.0),
                weight = 0.40, target = f.kcalTarget.toDouble(),
            ),
            ScorePart(
                "protein", "Protein", f.proteinG, PartUnit.GRAMS,
                note = if (f.proteinG >= f.proteinTarget) "Target met" else "${PartUnit.GRAMS.format(f.proteinTarget - f.proteinG)} to go",
                level = (f.proteinG / proteinNeed).coerceIn(0.0, 1.0),
                weight = 0.30, target = f.proteinTarget.toDouble(),
            ),
            ScorePart(
                "meals", "Meals logged", f.meals.toDouble(), PartUnit.MEALS,
                note = "of ${f.mealsGoal.coerceAtLeast(1)} a day",
                level = (f.meals / mealsNeed).coerceIn(0.0, 1.0),
                weight = 0.15, target = f.mealsGoal.coerceAtLeast(1).toDouble(),
            ),
            ScorePart(
                "water", "Water", f.waterMl.toDouble(), PartUnit.ML,
                note = "of ${PartUnit.ML.format(f.waterTarget.toDouble())}",
                level = (f.waterMl / waterNeed).coerceIn(0.0, 1.0),
                weight = 0.15, target = f.waterTarget.toDouble(),
            ),
        )
        return finish(ScoreKind.FUEL, f.date, parts, learning = false, soFar = soFar) { value, scored ->
            val signal = band(value)
            val worst = scored.filter { (it.level ?: 1.0) < 0.5 }.minByOrNull { it.level ?: 1.0 }
            val headline = when {
                soFar && worst != null -> "So far · ${FUEL_SHORT[worst.key]}"
                soFar -> "So far · on track"
                signal == PaceSignal.UP -> "On track"
                signal == PaceSignal.MID -> "Partly there"
                else -> "Off track"
            }
            val bad = if (f.kcal > f.kcalTarget) FUEL_BAD + ("calories" to "too many calories") else FUEL_BAD
            Triple(signal, headline, sentence(scored, FUEL_GOOD, bad, "Some of today’s eating fits your goal."))
        }
    }

    /** Average of the days that have a score, or null with none. */
    fun average(scores: List<Score>): Int? = scores.mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    /** Days whose score was in its good range (Effort: inside its band or over it). */
    fun goodDays(scores: List<Score>): Int = scores.count { it.value != null && !it.soFar && it.signal == PaceSignal.UP }

    /** Days that were finished and scored. */
    fun scoredDays(scores: List<Score>): Int = scores.count { it.value != null && !it.soFar }

    private inline fun finish(
        kind: ScoreKind,
        date: LocalDate,
        parts: List<ScorePart>,
        learning: Boolean,
        soFar: Boolean,
        band: IntRange? = null,
        describe: (value: Int, scored: List<ScorePart>) -> Triple<PaceSignal, String, String>,
    ): Score {
        val scored = parts.filter { it.level != null }
        val total = scored.sumOf { it.weight }
        if (total < MIN_COVERAGE) return empty(kind, date, parts, "Not enough recorded yet", learning, band)
        val normalised = parts.map { it.copy(weight = if (it.level == null) 0.0 else it.weight / total) }
        val value = (normalised.sumOf { (it.level ?: 0.0) * it.weight } * 100).roundToInt().coerceIn(0, 100)
        val (signal, headline, reason) = describe(value, normalised.filter { it.level != null })
        // Before the day's done a low Effort isn't a verdict yet, so it stays grey until it's in range.
        val shown = if (soFar && kind == ScoreKind.EFFORT && value < (band?.first ?: 0)) null else signal
        return Score(kind, date, value, shown, soFar, normalised, headline, reason, learning = learning, band = band)
    }

    private fun empty(kind: ScoreKind, date: LocalDate, parts: List<ScorePart>, missing: String, learning: Boolean, band: IntRange? = null) =
        Score(kind, date, null, null, false, parts, headline = missing, reason = missing, missing = missing, learning = learning, band = band)

    private fun versusNormal(diff: Double, unit: String, normal: Double): String {
        val d = abs(diff).roundToInt()
        val n = normal.roundToInt()
        return if (d == 0) "Same as your normal of $n $unit" else "$d ${if (diff < 0) "below" else "above"} your normal of $n $unit"
    }

    /** "You slept well and your heart is calm. Held back by an irregular bedtime." */
    private fun sentence(parts: List<ScorePart>, good: Map<String, String>, bad: Map<String, String>, ordinary: String): String {
        val goods = parts.filter { (it.level ?: 0.0) >= 0.8 }.sortedByDescending { it.weight }.mapNotNull { good[it.key] }.take(2)
        val bads = parts.filter { (it.level ?: 1.0) < 0.5 }.sortedByDescending { it.weight }.mapNotNull { bad[it.key] }.take(2)
        val first = goods.takeIf { it.isNotEmpty() }?.joinToString(" and ")?.replaceFirstChar { it.uppercase() }?.plus(".")
        val second = bads.takeIf { it.isNotEmpty() }?.let { "Held back by ${it.joinToString(" and ")}." }
        return listOfNotNull(first, second).joinToString(" ").ifEmpty { ordinary }
    }

    private const val MAX_HR = 190.0
    private const val DEFAULT_LOAD = 35.0
    private const val ACTIVE_MINUTES = 45.0

    private val RECOVERY_GOOD = mapOf(
        "sleep" to "you slept well",
        "restingHr" to "your resting heart rate is low",
        "hrv" to "your HRV is up",
        "consistency" to "your bedtime was steady",
    )
    private val RECOVERY_BAD = mapOf(
        "sleep" to "short sleep",
        "restingHr" to "a raised resting heart rate",
        "hrv" to "low HRV",
        "consistency" to "a bedtime that moved around",
        "yesterday" to "yesterday’s hard effort",
    )
    private val FUEL_GOOD = mapOf(
        "calories" to "calories on target",
        "protein" to "protein on target",
        "water" to "plenty of water",
    )
    private val FUEL_BAD = mapOf(
        "calories" to "too few calories",
        "protein" to "low protein",
        "meals" to "meals not logged",
        "water" to "little water",
    )
    private val FUEL_SHORT = mapOf(
        "calories" to "calories off",
        "protein" to "protein low",
        "meals" to "meals to log",
        "water" to "water low",
    )
}
