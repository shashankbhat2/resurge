package com.resurgeai.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeSet

enum class CardioMethod { CADENCE, STEP_RATE_ESTIMATE, ACTIVITY_ESTIMATE }
enum class CardioStatus { READY, LIMITED_DATA, PERMISSION_REQUIRED, UNAVAILABLE, STALE }

/** Non-overlapping duration is resolved by the engine, never by summing source rates. */
data class CardioEvidenceInterval(
    val start: Instant,
    val end: Instant,
    val intensity: Int,
    val method: CardioMethod,
    val source: DataSource,
    val recordId: String,
    val workoutId: String? = null,
)

data class CardioContribution(
    val method: CardioMethod,
    val source: String,
    val workoutId: String?,
    val workoutTitle: String?,
    val durationMs: Long,
    val weightedMs: Long,
) {
    val points: Double get() = weightedMs / 60_000.0
}

data class DailyCardioPoints(
    val date: LocalDate,
    val contributions: List<CardioContribution> = emptyList(),
    val unclassifiedMs: Long = 0,
    val status: CardioStatus = CardioStatus.READY,
    val version: Int = CardioPointsEngine.VERSION,
    val calculatedAt: Instant,
) {
    val weightedMs: Long get() = contributions.sumOf { it.weightedMs }
    val points: Long get() = weightedMs / 60_000
    val estimated: Boolean get() = contributions.any { it.method == CardioMethod.ACTIVITY_ESTIMATE }
    fun weightedMs(method: CardioMethod): Long = contributions.filter { it.method == method }.sumOf { it.weightedMs }
}

/** Pure, deterministic interval sweep. No Android, cloud, heart-rate, or calorie dependencies. */
object CardioPointsEngine {
    const val VERSION = 1
    const val HISTORY_DAYS = 30L
    private val cadenceActivities = setOf("walk", "run", "treadmill")

    fun cadenceIntensity(rate: Double): Int = when {
        !rate.isFinite() || rate < 100 -> 0
        rate < 130 -> 1
        else -> 2
    }

    fun metIntensity(met: Double): Int = when {
        !met.isFinite() || met < 3 -> 0
        met < 6 -> 1
        else -> 2
    }

    fun calculate(
        first: LocalDate,
        last: LocalDate,
        zone: ZoneId,
        now: Instant,
        evidence: List<CardioEvidenceInterval>,
        workouts: List<Workout>,
        status: CardioStatus = CardioStatus.READY,
    ): List<DailyCardioPoints> {
        require(!last.isBefore(first))
        val start = first.atStartOfDay(zone).toInstant()
        val end = minOf(last.plusDays(1).atStartOfDay(zone).toInstant(), now)
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        val totals = days.associateWith { linkedMapOf<Key, CardioContribution>() }
        val unknown = mutableMapOf<LocalDate, Long>()
        if (end <= start) return days.map { DailyCardioPoints(it, status = status, calculatedAt = now) }
        val signals = evidence.filter { it.start < it.end && it.start < end && it.end > start && it.intensity in 0..2 }
        val sessions = workouts.filter { it.start < it.end && it.start < end && it.end > start }
        val events = sortedMapOf<Instant, Events>()
        fun at(time: Instant) = events.getOrPut(time) { Events() }
        signals.forEachIndexed { i, e -> at(maxOf(start, e.start)).signalStarts += i; at(minOf(end, e.end)).signalEnds += i }
        sessions.forEachIndexed { i, w -> at(maxOf(start, w.start)).workoutStarts += i; at(minOf(end, w.end)).workoutEnds += i }
        days.forEach { at(it.atStartOfDay(zone).toInstant().coerceIn(start, end)) }
        at(start); at(end)
        val signalOrder = compareBy<Int> { signals[it].method.ordinal }
            .thenBy { sourceRank(signals[it].source.kind) }
            .thenBy { signals[it].source.packageName }.thenBy { signals[it].recordId }.thenBy { it }
        val workoutOrder = compareByDescending<Int> { sessions[it].edited }
            .thenBy { if (sessions[it].source == WorkoutSource.HEALTH_CONNECT) 0 else 1 }
            .thenBy { sessions[it].start }.thenBy { sessions[it].id }.thenBy { it }
        val activeSignals = TreeSet(signalOrder)
        val activeWorkouts = TreeSet(workoutOrder)
        val times = events.keys.toList()
        times.zipWithNext().forEach { (a, b) ->
            val event = events.getValue(a)
            activeSignals.removeAll(event.signalEnds.toSet()); activeSignals.addAll(event.signalStarts)
            activeWorkouts.removeAll(event.workoutEnds.toSet()); activeWorkouts.addAll(event.workoutStarts)
            val date = a.atZone(zone).toLocalDate()
            val duration = b.toEpochMilli() - a.toEpochMilli()
            val workout = activeWorkouts.firstOrNull()?.let(sessions::get)
            val signal = activeSignals.asSequence().map(signals::get)
                .firstOrNull { it.workoutId == null || it.workoutId == workout?.id }
            val useSignal = signal != null && (workout == null || workout.activityId in cadenceActivities || !known(workout))
            val contribution = when {
                useSignal -> CardioContribution(
                    signal.method, sourceLabel(signal.source),
                    workout?.id, workout?.displayName, duration, duration * signal.intensity,
                )
                workout != null && known(workout) -> CardioContribution(
                    CardioMethod.ACTIVITY_ESTIMATE, workout.sourceName ?: if (workout.source == WorkoutSource.MANUAL) "Manual workout" else "Health Connect",
                    workout.id, workout.displayName, duration, duration * metIntensity(workout.activity.met),
                )
                else -> null
            }
            if (contribution != null) {
                val key = Key(contribution.method, contribution.source, contribution.workoutId)
                val previous = totals.getValue(date)[key]
                totals.getValue(date)[key] = contribution.copy(
                    durationMs = contribution.durationMs + (previous?.durationMs ?: 0),
                    weightedMs = contribution.weightedMs + (previous?.weightedMs ?: 0),
                )
            } else if (workout != null) unknown[date] = (unknown[date] ?: 0) + duration
        }
        return days.map { date ->
            val contributions = totals.getValue(date).values.toList()
            val unclassified = unknown[date] ?: 0
            DailyCardioPoints(date, contributions, unclassified,
                if (status == CardioStatus.READY && (unclassified > 0 || contributions.none { it.method != CardioMethod.ACTIVITY_ESTIMATE })) CardioStatus.LIMITED_DATA else status,
                calculatedAt = now)
        }
    }

    private fun sourceLabel(source: DataSource) = listOfNotNull(source.deviceName, source.appName ?: source.packageName)
        .distinct().joinToString(" · ")
    private fun known(w: Workout) = w.activityId != "other" && ActivityCatalog.all.any { it.id == w.activityId }
    private fun sourceRank(kind: SourceKind) = when (kind) { SourceKind.WEARABLE -> 0; SourceKind.PHONE -> 1; else -> 2 }
    private data class Key(val method: CardioMethod, val source: String, val workout: String?)
    private class Events {
        val signalStarts = mutableListOf<Int>(); val signalEnds = mutableListOf<Int>()
        val workoutStarts = mutableListOf<Int>(); val workoutEnds = mutableListOf<Int>()
    }
}
