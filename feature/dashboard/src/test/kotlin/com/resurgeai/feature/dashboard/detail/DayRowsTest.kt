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
    fun `sleep window sits on a 6pm to noon clock`() {
        // 23:00 on the 9th -> 06:00 on the 10th, i.e. 5h and 12h after 18:00.
        val start = d1.atTime(23, 0).toInstant(zone)
        val night = SleepSession(start, start.plus(Duration.ofHours(7)), mapOf(SleepStage.DEEP to Duration.ofHours(2), SleepStage.LIGHT to Duration.ofHours(5)), null)
        val (axis, rows) = DayRows.build(HealthMetric.SLEEP, listOf(day(d2, 7.0)), listOf(night), emptyList(), emptyMap(), zone)
        val viz = rows.single().viz as DayViz.Window
        assertEquals(5f / 18, viz.start, 0.001f)
        assertEquals(12f / 18, viz.end, 0.001f)
        assertEquals(listOf(SleepStage.DEEP, SleepStage.LIGHT), viz.stages.map { it.second })
        assertEquals("23:00 → 06:00", rows.single().detail)
        assertEquals(4, axis.labels.size)
    }

    @Test
    fun `heart rate ranges share a rounded axis`() {
        val (axis, rows) = DayRows.build(
            HealthMetric.HEART_RATE,
            listOf(day(d1, 70.0), day(d2, 80.0)),
            emptyList(), emptyList(),
            mapOf(d1 to (52L to 141L), d2 to (60L to 120L)),
            zone,
        )
        assertEquals(listOf("50 bpm", "150 bpm"), axis.labels.map { it.first })
        val r = rows.first { it.date == d1 }.viz as DayViz.Range
        assertEquals(0.02f, r.low, 0.001f)
        assertEquals(0.91f, r.high, 0.001f)
        assertEquals(0.2f, r.mid!!, 0.001f)
    }

    private fun week(start: LocalDate, value: Double?, total: Double?, days: Int) =
        PeriodValue(start, start.plusDays(days - 1L), value, total, days, days)

    @Test
    fun `week rows average bedtime and wake time across nights`() {
        val mon = LocalDate.of(2026, 10, 5)
        // Nights ending Tue (22:00 -> 06:00) and Wed (00:00 -> 08:00): avg 23:00 -> 07:00.
        val n1 = SleepSession(mon.atTime(22, 0).toInstant(zone), mon.plusDays(1).atTime(6, 0).toInstant(zone), emptyMap(), null)
        val n2 = SleepSession(mon.plusDays(2).atTime(0, 0).toInstant(zone), mon.plusDays(2).atTime(8, 0).toInstant(zone), emptyMap(), null)
        val (_, rows) = DayRows.build(HealthMetric.SLEEP, listOf(week(mon, 8.0, null, 7)), listOf(n1, n2), emptyList(), emptyMap(), zone)
        val viz = rows.single().viz as DayViz.Window
        assertEquals(5f / 18, viz.start, 0.001f)
        assertEquals(13f / 18, viz.end, 0.001f)
        assertEquals("avg 23:00 → 07:00", rows.single().detail)
    }

    @Test
    fun `week rows span the lowest and highest heart rate of the week`() {
        val mon = LocalDate.of(2026, 10, 5)
        val (_, rows) = DayRows.build(
            HealthMetric.HEART_RATE,
            listOf(week(mon, 70.0, null, 7)),
            emptyList(), emptyList(),
            mapOf(mon to (55L to 120L), mon.plusDays(3) to (60L to 150L)),
            zone,
        )
        assertEquals("55–150 bpm", rows.single().detail)
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
