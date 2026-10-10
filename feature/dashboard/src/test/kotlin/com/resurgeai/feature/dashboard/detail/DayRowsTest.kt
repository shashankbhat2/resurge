package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class DayRowsTest {
    @Before
    fun setUp() = java.util.Locale.setDefault(java.util.Locale.US)

    private val zone = ZoneOffset.UTC
    private val d1 = LocalDate.of(2026, 10, 9)
    private val d2 = LocalDate.of(2026, 10, 10)

    private fun day(date: LocalDate, value: Double?) = PeriodValue(date, date, value, value, if (value != null) 1 else 0, 1)

    private fun week(start: LocalDate, value: Double?, total: Double?, days: Int) =
        PeriodValue(start, start.plusDays(days - 1L), value, total, days, days)

    @Test
    fun `magnitude is relative to the best day, with an average marker, newest first`() {
        val (_, rows) = DayRows.build(HealthMetric.STEPS, listOf(day(d1, 5000.0), day(d2, 10000.0)), emptyList(), emptyList(), emptyMap(), zone)
        assertEquals(d2, rows[0].date)
        val top = rows[0].viz as DayViz.Magnitude
        assertEquals(1f, top.fraction, 0.001f)
        assertEquals(0.75f, top.marker!!, 0.001f)
        assertEquals(0.5f, (rows[1].viz as DayViz.Magnitude).fraction, 0.001f)
    }

    @Test
    fun `sleep rows show a quality bar and no times`() {
        val start = d1.atTime(23, 0).toInstant(zone)
        // 7h in bed, 42 min awake -> 90% quality.
        val night = SleepSession(
            start, start.plus(Duration.ofHours(7)),
            mapOf(SleepStage.AWAKE to Duration.ofMinutes(42), SleepStage.LIGHT to Duration.ofMinutes(378)),
            null,
        )
        val (axis, rows) = DayRows.build(HealthMetric.SLEEP, listOf(day(d2, 7.0)), listOf(night), emptyList(), emptyMap(), zone)
        assertTrue(axis.labels.isEmpty())
        assertEquals(0.9f, (rows.single().viz as DayViz.Magnitude).fraction, 0.001f)
        assertEquals(null, rows.single().detail)
    }

    @Test
    fun `week sleep row averages quality across nights`() {
        val mon = LocalDate.of(2026, 10, 5)
        fun night(endDay: Long, awakeMin: Long): SleepSession {
            val start = mon.plusDays(endDay - 1).atTime(23, 0).toInstant(zone)
            return SleepSession(start, start.plus(Duration.ofHours(8)), mapOf(SleepStage.AWAKE to Duration.ofMinutes(awakeMin)), null)
        }
        val (_, rows) = DayRows.build(
            HealthMetric.SLEEP, listOf(week(mon, 8.0, null, 7)),
            listOf(night(1, 48), night(2, 0)), emptyList(), emptyMap(), zone,
        )
        // 90% and 100% -> 95%.
        assertEquals(0.95f, (rows.single().viz as DayViz.Magnitude).fraction, 0.001f)
    }

    @Test
    fun `heart rate rows carry no visual`() {
        val (axis, rows) = DayRows.build(
            HealthMetric.HEART_RATE, listOf(day(d1, 70.0)), emptyList(), emptyList(), mapOf(d1 to (52L to 141L)), zone,
        )
        assertTrue(axis.labels.isEmpty())
        assertTrue(rows.single().viz is DayViz.Empty)
        assertEquals(70.0, rows.single().value!!, 0.0)
    }

    @Test
    fun `month rows show weekly totals for additive metrics`() {
        val mon = LocalDate.of(2026, 10, 5)
        val (_, rows) = DayRows.build(HealthMetric.STEPS, listOf(week(mon, 9000.0, 63000.0, 7)), emptyList(), emptyList(), emptyMap(), zone)
        assertEquals("Total 63,000", rows.single().detail)
    }

    @Test
    fun `exercise shows one segment per workout`() {
        val t = d2.atTime(7, 0).toInstant(zone)
        val sessions = listOf(
            ExerciseSession("a", "Running", null, t, t.plus(Duration.ofMinutes(30)), null),
            ExerciseSession("b", "Yoga", null, t.plus(Duration.ofHours(10)), t.plus(Duration.ofHours(10)).plus(Duration.ofMinutes(15)), null),
        )
        val (_, rows) = DayRows.build(HealthMetric.EXERCISE, listOf(day(d1, null), day(d2, 45.0)), emptyList(), sessions, emptyMap(), zone)
        val seg = rows[0].viz as DayViz.Segments
        assertEquals(listOf(30f / 45, 15f / 45), seg.fractions)
        assertEquals("2 workouts", rows[0].detail)
        assertTrue(rows[1].viz is DayViz.Empty)
    }
}
