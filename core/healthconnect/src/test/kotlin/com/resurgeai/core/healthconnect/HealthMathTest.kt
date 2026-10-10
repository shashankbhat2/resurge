package com.resurgeai.core.healthconnect

import androidx.health.connect.client.records.SleepSessionRecord
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class HealthMathTest {
    private val zone: ZoneId = ZoneOffset.ofHours(5) // non-UTC so date boundaries matter

    private fun session(start: String, minutes: Long) = Instant.parse(start).let {
        ExerciseSession("id", "Running", null, it, it.plus(Duration.ofMinutes(minutes)), null)
    }

    @Test
    fun `week starts on Monday`() {
        assertEquals(LocalDate.of(2026, 10, 5), HealthMath.weekStart(LocalDate.of(2026, 10, 10))) // Sat -> Mon
        assertEquals(LocalDate.of(2026, 10, 5), HealthMath.weekStart(LocalDate.of(2026, 10, 5))) // Mon -> Mon
        assertEquals(LocalDate.of(2026, 10, 5), HealthMath.weekStart(LocalDate.of(2026, 10, 11))) // Sun -> Mon
    }

    @Test
    fun `exercise days need ten minutes in local time`() {
        val days = HealthMath.exerciseDays(
            listOf(
                session("2026-10-06T03:00:00Z", 30), // 08:00 local, Tue
                session("2026-10-07T03:00:00Z", 5), // Wed: 5 + 6 = 11 min total
                session("2026-10-07T10:00:00Z", 6),
                session("2026-10-08T03:00:00Z", 9), // Thu: too short
                session("2026-10-08T20:00:00Z", 15), // 01:00 local on Fri
            ),
            zone,
        )
        assertEquals(
            setOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 9)),
            days,
        )
    }

    @Test
    fun `day bounds cap today at now`() {
        val now = Instant.parse("2026-10-10T07:00:00Z") // 12:00 local
        val (start, end) = HealthMath.dayBounds(LocalDate.of(2026, 10, 10), zone, now)
        assertEquals(Instant.parse("2026-10-09T19:00:00Z"), start)
        assertEquals(now, end)

        val (_, pastEnd) = HealthMath.dayBounds(LocalDate.of(2026, 10, 8), zone, now)
        assertEquals(Instant.parse("2026-10-08T19:00:00Z"), pastEnd)
    }

    @Test
    fun `stage totals merge awake variants`() {
        val t0 = Instant.parse("2026-10-09T17:00:00Z")
        fun at(min: Long) = t0.plus(Duration.ofMinutes(min))
        val totals = HealthMath.stageTotals(
            listOf(
                Triple(at(0), at(10), SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED),
                Triple(at(10), at(100), SleepSessionRecord.STAGE_TYPE_LIGHT),
                Triple(at(100), at(160), SleepSessionRecord.STAGE_TYPE_DEEP),
                Triple(at(160), at(165), SleepSessionRecord.STAGE_TYPE_AWAKE),
                Triple(at(165), at(255), SleepSessionRecord.STAGE_TYPE_REM),
            ),
        )
        assertEquals(Duration.ofMinutes(15), totals[SleepStage.AWAKE])
        assertEquals(Duration.ofMinutes(90), totals[SleepStage.LIGHT])
        assertEquals(Duration.ofMinutes(60), totals[SleepStage.DEEP])
        assertEquals(Duration.ofMinutes(90), totals[SleepStage.REM])
    }
}
