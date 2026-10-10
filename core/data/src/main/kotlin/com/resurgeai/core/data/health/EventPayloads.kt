package com.resurgeai.core.data.health

import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.WorkoutMetrics
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Builds the users/{uid}/events/{hcId} documents for finished workouts and nights.
 * Only per-session aggregates leave the device — never raw heart-rate or step samples.
 * Must match validEvent() in firestore.rules and ActivityEvent in functions/src/lib/types.ts.
 */
object EventPayloads {
    /** Workouts shorter than this are ignored. */
    val MIN_WORKOUT: Duration = Duration.ofMinutes(10)

    /** Main sleep only: naps (shorter than this) are skipped. */
    val MIN_SLEEP: Duration = Duration.ofHours(3)

    /** Wait this long after a session ends so late watch data (HR samples, stages) can sync. */
    val WORKOUT_SETTLE: Duration = Duration.ofMinutes(15)
    val SLEEP_SETTLE: Duration = Duration.ofMinutes(30)

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

    fun isPostableWorkout(s: ExerciseSession, now: Instant): Boolean =
        s.id.isNotBlank() && s.duration >= MIN_WORKOUT && !s.end.plus(WORKOUT_SETTLE).isAfter(now)

    fun isPostableSleep(s: SleepSession, now: Instant): Boolean =
        s.id.isNotBlank() && s.duration >= MIN_SLEEP && !s.end.plus(SLEEP_SETTLE).isAfter(now)

    fun workout(s: ExerciseSession, m: WorkoutMetrics, zone: ZoneId): Map<String, Any?> {
        val minutes = s.duration.seconds / 60.0
        val pace = m.distanceMeters
            ?.takeIf { it >= 200 }
            ?.let { s.duration.seconds / (it / 1000.0) }
        return base("workout", s.id, s.start, s.end, zone, s.source) + mapOf(
            "workout" to mapOf(
                "activity" to s.type,
                "title" to s.title,
                "durationMin" to minutes.roundToInt(),
                "distanceM" to m.distanceMeters?.roundToLong(),
                "activeKcal" to m.activeKcal?.roundToLong(),
                "totalKcal" to m.totalKcal?.roundToLong(),
                "avgHr" to m.avgBpm,
                "maxHr" to m.maxBpm,
                "steps" to m.steps,
                "paceSecPerKm" to pace?.roundToLong(),
            ),
        )
    }

    fun sleep(s: SleepSession, hr: WorkoutMetrics?, zone: ZoneId): Map<String, Any?> {
        fun stage(vararg st: SleepStage) = st.sumOf { s.stages[it]?.toMinutes() ?: 0L }
        return base("sleep", s.id, s.start, s.end, zone, s.source) + mapOf(
            "sleep" to mapOf(
                "durationMin" to s.duration.toMinutes(),
                "asleepMin" to s.asleep.toMinutes(),
                "deepMin" to stage(SleepStage.DEEP),
                "lightMin" to stage(SleepStage.LIGHT, SleepStage.SLEEPING, SleepStage.UNKNOWN),
                "remMin" to stage(SleepStage.REM),
                "awakeMin" to stage(SleepStage.AWAKE),
                "efficiency" to s.quality?.toDouble(),
                "bedtime" to s.start.atZone(zone).format(CLOCK),
                "wake" to s.end.atZone(zone).format(CLOCK),
                "avgHr" to hr?.avgBpm,
                "minHr" to hr?.minBpm,
            ),
        )
    }

    private fun base(type: String, id: String, start: Instant, end: Instant, zone: ZoneId, source: DataSource?) = mapOf(
        "type" to type,
        "hcId" to id,
        "startMs" to start.toEpochMilli(),
        "endMs" to end.toEpochMilli(),
        "timezone" to zone.id,
        "localDate" to end.atZone(zone).toLocalDate().toString(),
        "source" to source?.let { mapOf("kind" to it.kind.name, "device" to it.deviceName, "app" to it.appName) },
        "status" to "pending",
    )
}
