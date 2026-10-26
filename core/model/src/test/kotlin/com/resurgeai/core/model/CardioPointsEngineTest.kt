package com.resurgeai.core.model

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CardioPointsEngineTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 20)
    private val start = date.atTime(10, 0).atZone(zone).toInstant()
    private val now = date.plusDays(1).atStartOfDay(zone).toInstant()
    private val watch = DataSource(SourceKind.WEARABLE, "watch", "Watch", null)
    private val phone = DataSource(SourceKind.PHONE, "phone", "Phone", null)
    private fun evidence(rate: Double = 110.0, seconds: Long = 1200, at: Instant = start, source: DataSource = watch, method: CardioMethod = CardioMethod.CADENCE, id: String = "sensor") =
        CardioEvidenceInterval(at, at.plusSeconds(seconds), CardioPointsEngine.cadenceIntensity(rate), method, source, id)
    private fun workout(id: String = "w", activity: String = "run", seconds: Long = 1200, source: WorkoutSource = WorkoutSource.MANUAL, edited: Boolean = false, at: Instant = start) =
        Workout(id, source, activity, null, at, at.plusSeconds(seconds), WorkoutMetricsInput(), edited = edited)
    private fun score(evidence: List<CardioEvidenceInterval> = emptyList(), workouts: List<Workout> = emptyList(), until: Instant = now) =
        CardioPointsEngine.calculate(date, date, zone, until, evidence, workouts).single()

    @Test fun `cadence and MET thresholds are inclusive at moderate and vigorous boundaries`() {
        assertEquals(listOf(0, 1, 1, 2), listOf(99.999, 100.0, 129.999, 130.0).map(CardioPointsEngine::cadenceIntensity))
        assertEquals(listOf(0, 1, 1, 2), listOf(2.999, 3.0, 5.999, 6.0).map(CardioPointsEngine::metIntensity))
        assertEquals(0, CardioPointsEngine.cadenceIntensity(Double.NaN))
    }
    @Test fun `twenty moderate or ten vigorous minutes each yield twenty points`() {
        assertEquals(20L, score(listOf(evidence())).points)
        assertEquals(20L, score(listOf(evidence(130.0, 600))).points)
    }
    @Test fun `fractional contributions accumulate before rounding and split intervals do not change totals`() {
        val a = evidence(seconds = 35)
        val b = evidence(seconds = 35, at = start.plusSeconds(35), id = "b")
        assertEquals(1L, score(listOf(a, b)).points)
        assertEquals(70_000L, score(listOf(a, b)).weightedMs)
        assertEquals(score(listOf(evidence(seconds = 70))).weightedMs, score(listOf(a, b)).weightedMs)
    }
    @Test fun `low cadence overrides running estimate but missing cadence falls back`() {
        assertEquals(0L, score(listOf(evidence(90.0)), listOf(workout())).points)
        assertEquals(40L, score(workouts = listOf(workout())).points)
        assertEquals(30L, score(listOf(evidence(110.0, 600)), listOf(workout())).points)
    }
    @Test fun `cycling owns its time and uses catalog MET even with low cadence`() {
        val day = score(listOf(evidence(80.0)), listOf(workout(activity = "cycle")))
        assertEquals(40L, day.points)
        assertTrue(day.estimated)
        assertEquals(CardioMethod.ACTIVITY_ESTIMATE, day.contributions.single().method)
    }
    @Test fun `unknown and generic activities have no automatic MET estimate`() {
        for (id in listOf("other", "unmapped_sport")) {
            val day = score(workouts = listOf(workout(activity = id)))
            assertEquals(0L, day.points)
            assertEquals(1_200_000L, day.unclassifiedMs)
            assertEquals(CardioStatus.LIMITED_DATA, day.status)
            assertEquals(20L, score(listOf(evidence()), listOf(workout(activity = id))).points)
        }
    }
    @Test fun `cadence outranks step estimates and wearable outranks phone without adding rates`() {
        assertEquals(20L, score(listOf(evidence(), evidence(150.0, source = phone))).points)
        assertEquals(20L, score(listOf(evidence(), evidence(150.0, method = CardioMethod.STEP_RATE_ESTIMATE))).points)
        assertEquals(20L, score(listOf(evidence(), evidence(id = "duplicate"))).points)
    }
    @Test fun `source ties resolve by package and record id rather than input order`() {
        val a = evidence(110.0, source = watch.copy(packageName = "a"))
        val b = evidence(150.0, source = watch.copy(packageName = "b"))
        assertEquals(20L, score(listOf(b, a)).points)
        assertEquals(score(listOf(a, b)), score(listOf(b, a)))
    }
    @Test fun `edited then imported then manual owns overlap`() {
        val manual = workout("manual", "cycle")
        val imported = workout("imported", "stretch", source = WorkoutSource.HEALTH_CONNECT)
        val edited = workout("edited", "walk", edited = true)
        assertEquals(0L, score(workouts = listOf(manual, imported)).points)
        assertEquals(20L, score(workouts = listOf(manual, imported, edited)).points)
        assertEquals("edited", score(workouts = listOf(edited, manual, imported)).contributions.single().workoutId)
    }
    @Test fun `same priority workouts resolve by earlier start then stable id`() {
        val a = workout("a", "walk")
        val b = workout("b", "cycle")
        assertEquals(20L, score(workouts = listOf(b, a)).points)
        val earlier = b.copy(start = start.minusSeconds(60))
        assertEquals(42L, score(workouts = listOf(a, earlier)).points)
    }
    @Test fun `everyday cadence is attributed once outside workout and released on deletion`() {
        val w = workout(activity = "cycle", seconds = 600)
        val result = score(listOf(evidence()), listOf(w))
        assertEquals(30L, result.points)
        assertEquals(600_000L, result.contributions.single { it.workoutId == null }.weightedMs)
        assertEquals(20L, score(listOf(evidence())).points)
        assertEquals(result, score(listOf(evidence()), listOf(w))) // undo restores original calculation
    }
    @Test fun `future time is clipped invalid intervals discarded and boundaries are half open`() {
        val day = score(listOf(evidence(seconds = 1200)), until = start.plusSeconds(30))
        assertEquals(30_000L, day.weightedMs)
        assertEquals(0L, day.points)
        assertEquals(0L, score(listOf(evidence().copy(end = start.minusSeconds(1)))).points)
        assertEquals(20L, score(listOf(evidence(seconds = 600), evidence(seconds = 600, at = start.plusSeconds(600)))).points)
    }
    @Test fun `cross midnight splits elapsed milliseconds and rounds each day independently`() {
        val at = now.minusSeconds(30)
        val days = CardioPointsEngine.calculate(date, date.plusDays(1), zone, now.plusSeconds(30), listOf(evidence(seconds = 60, at = at)), emptyList())
        assertEquals(listOf(30_000L, 30_000L), days.map { it.weightedMs })
        assertEquals(0L, days.sumOf { it.points })
    }
    @Test fun `DST day uses actual elapsed duration and timezone controls attribution`() {
        for ((d, hours) in listOf(LocalDate.of(2026, 3, 8) to 23L, LocalDate.of(2026, 11, 1) to 25L)) {
            val z = ZoneId.of("America/New_York")
            val from = d.atStartOfDay(z).toInstant(); val to = d.plusDays(1).atStartOfDay(z).toInstant()
            val interval = evidence(at = from, seconds = hours * 3600)
            assertEquals(hours * 60, CardioPointsEngine.calculate(d, d, z, to, listOf(interval), emptyList()).single().points)
        }
        val instant = date.atTime(23, 30).atZone(zone).toInstant()
        val localDate = date.plusDays(1)
        assertEquals(20L, CardioPointsEngine.calculate(localDate, localDate, ZoneId.of("Asia/Kolkata"), instant.plusSeconds(1200), listOf(evidence(at = instant)), emptyList()).single().points)
    }
    @Test fun `zero observed cadence is ready but absence of evidence is limited`() {
        assertEquals(CardioStatus.READY, score(listOf(evidence(0.0))).status)
        assertEquals(CardioStatus.LIMITED_DATA, score().status)
        assertEquals(0L, score().points)
    }
}
