package com.resurgeai.core.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.SleepStage
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Pure date/duration helpers used by the dashboard. */
object HealthMath {
    /** Sessions shorter than this don't count towards an "exercise day". */
    val MIN_EXERCISE_FOR_DAY: Duration = Duration.ofMinutes(10)

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Local dates (in [zone]) with at least [minDuration] of exercise in total. */
    fun exerciseDays(
        sessions: List<ExerciseSession>,
        zone: ZoneId,
        minDuration: Duration = MIN_EXERCISE_FOR_DAY,
    ): Set<LocalDate> = sessions
        .groupBy { it.start.atZone(zone).toLocalDate() }
        .filterValues { day -> day.fold(Duration.ZERO) { acc, s -> acc + s.duration } >= minDuration }
        .keys

    /** Start/end instants for a local day; the end is capped at [now] for today. */
    fun dayBounds(date: LocalDate, zone: ZoneId, now: Instant = Instant.now()): Pair<Instant, Instant> {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return start to minOf(end, now).coerceAtLeast(start)
    }

    /** Sums stage durations, mapping Health Connect stage ints to [SleepStage]. */
    fun stageTotals(stages: List<Triple<Instant, Instant, Int>>): Map<SleepStage, Duration> = stages
        .groupBy { mapStage(it.third) }
        .mapValues { (_, list) -> list.fold(Duration.ZERO) { acc, (s, e, _) -> acc + Duration.between(s, e) } }

    fun mapStage(stage: Int): SleepStage = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> SleepStage.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> SleepStage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> SleepStage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> SleepStage.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> SleepStage.SLEEPING
        else -> SleepStage.UNKNOWN
    }
}
