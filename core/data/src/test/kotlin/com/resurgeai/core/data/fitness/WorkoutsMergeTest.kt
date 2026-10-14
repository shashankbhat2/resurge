package com.resurgeai.core.data.fitness

import com.resurgeai.core.data.fitness.WorkoutsRepository.Companion.metricsFrom
import com.resurgeai.core.data.fitness.WorkoutsRepository.Companion.toMap
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WorkoutsMergeTest {
    private val t0 = Instant.parse("2026-10-10T07:00:00Z")

    private fun device(id: String, minutesAgo: Long) = Workout(
        id, WorkoutSource.HEALTH_CONNECT, "run", null, t0.minusSeconds(minutesAgo * 60), t0.minusSeconds(minutesAgo * 60 - 1800),
        WorkoutMetricsInput(distanceM = 5000.0), sourceName = "Pixel Watch",
    )

    private fun record(id: String, source: WorkoutSource, deleted: Boolean = false, activity: String = "run", start: Instant = t0) =
        WorkoutRecord(id, source, activity, null, start, start.plusSeconds(1800), WorkoutMetricsInput(kcal = 300), null, deleted)

    @Test
    fun `edits replace device fields, tombstones hide sessions, manual logs are added newest first`() {
        val merged = WorkoutsRepository.merge(
            device = listOf(device("hc1", 600), device("hc2", 300)),
            records = listOf(
                record("hc1", WorkoutSource.HEALTH_CONNECT, activity = "treadmill", start = t0.minusSeconds(36_000)),
                record("hc2", WorkoutSource.HEALTH_CONNECT, deleted = true),
                record("m1", WorkoutSource.MANUAL, activity = "weights"),
            ),
        )
        assertEquals(listOf("m1", "hc1"), merged.map { it.id })
        val edited = merged.last()
        assertTrue(edited.edited)
        assertEquals("treadmill", edited.activityId)
        assertEquals("Pixel Watch", edited.sourceName)
        assertEquals(300, edited.metrics.kcal)
    }

    @Test
    fun `metrics round-trip through the Firestore map`() {
        val metrics = WorkoutMetricsInput(
            distanceM = 1200.0, kcal = 210, rpe = 7,
            exercises = listOf(StrengthExercise("Bench press", listOf(StrengthSet(8, 60.0), StrengthSet(12, null)))),
        )
        assertEquals(metrics, metricsFrom(metrics.toMap()))
    }

    private fun session(id: String, source: String, startMin: Long, minutes: Long, distance: Double? = null, hr: Int? = null, activity: String = "run") = Workout(
        id, WorkoutSource.HEALTH_CONNECT, activity, null, t0.plusSeconds(startMin * 60), t0.plusSeconds((startMin + minutes) * 60),
        WorkoutMetricsInput(distanceM = distance, avgHr = hr), sourceName = source,
    )

    @Test
    fun `a session recorded by two devices shows once, keeping the one with more data`() {
        val watch = session("watch", "Fitbit Air", 0, 20, distance = 2150.0, hr = 140)
        val phone = session("phone", "OnePlus CPH2723", 0, 23, distance = 2150.0)
        val merged = WorkoutsRepository.merge(listOf(phone, watch), emptyList())
        assertEquals(listOf("watch"), merged.map { it.id })
        assertEquals(listOf("phone"), merged.single().duplicates.map { it.id })
    }

    @Test
    fun `different activities, or sessions that barely overlap, stay separate`() {
        val run = session("run", "Fitbit Air", 0, 30)
        val lift = session("lift", "OnePlus", 0, 30, activity = "weights")
        val walk = session("walk", "Fitbit Air", -10, 54, activity = "walk")
        val later = session("later", "OnePlus", 25, 30)
        assertEquals(4, WorkoutsRepository.merge(listOf(run, lift, walk, later), emptyList()).size)
    }

    @Test
    fun `the recording the user edited wins, and hiding one shows the other on its own`() {
        val watch = session("watch", "Fitbit Air", 0, 20, distance = 2150.0, hr = 140)
        val phone = session("phone", "OnePlus", 0, 23)
        val edited = WorkoutsRepository.merge(listOf(watch, phone), listOf(record("phone", WorkoutSource.HEALTH_CONNECT, start = phone.start)))
        assertEquals(listOf("phone"), edited.map { it.id })
        val hidden = WorkoutsRepository.merge(listOf(watch, phone), listOf(record("watch", WorkoutSource.HEALTH_CONNECT, deleted = true)))
        assertEquals(listOf("phone"), hidden.map { it.id })
        assertTrue(hidden.single().duplicates.isEmpty())
    }
}
