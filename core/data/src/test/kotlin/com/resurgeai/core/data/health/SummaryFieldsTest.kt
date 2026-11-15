package com.resurgeai.core.data.health

import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SummaryFieldsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val morning = Instant.parse("2026-10-11T01:30:00Z") // 07:00 local

    private fun night(stages: Map<SleepStage, Duration>) =
        SleepSession(start = morning.minus(Duration.ofHours(8)), end = morning, stages = stages, source = null)

    private fun session(type: String, startAt: Instant, minutes: Long, title: String? = null) =
        ExerciseSession("id-$type", type, title, startAt, startAt.plus(Duration.ofMinutes(minutes)), null)

    @Test
    fun `sleep stages are minutes per stage`() {
        val stages = SummaryFields.sleepStages(
            listOf(night(mapOf(SleepStage.DEEP to Duration.ofMinutes(80), SleepStage.REM to Duration.ofMinutes(90),
                SleepStage.LIGHT to Duration.ofMinutes(250), SleepStage.AWAKE to Duration.ofMinutes(20)))),
        )
        assertEquals(mapOf("deep" to 80L, "light" to 250L, "rem" to 90L, "awake" to 20L), stages)
    }

    @Test
    fun `a night without deep, light or REM has no stages`() {
        assertNull(SummaryFields.sleepStages(listOf(night(mapOf(SleepStage.SLEEPING to Duration.ofMinutes(450), SleepStage.AWAKE to Duration.ofMinutes(30))))))
        assertNull(SummaryFields.sleepStages(listOf(night(emptyMap()))))
        assertNull(SummaryFields.sleepStages(emptyList()))
    }

    @Test
    fun `exercise sessions keep type, local start and length in time order`() {
        val evening = morning.plus(Duration.ofHours(11)) // 18:00 local
        val out = SummaryFields.exerciseSessions(
            listOf(session("Strength training", evening, 45, title = "Push day"), session("Running", morning, 32)),
            zone,
        )
        assertEquals(
            listOf(
                mapOf("type" to "Running", "title" to null, "start" to "07:00", "durationMin" to 32L),
                mapOf("type" to "Strength training", "title" to "Push day", "start" to "18:00", "durationMin" to 45L),
            ),
            out,
        )
    }

    @Test
    fun `exercise sessions are capped`() {
        val many = (0 until 30).map { session("Walking", morning.plus(Duration.ofMinutes(it * 20L)), 10) }
        assertEquals(SummaryFields.MAX_EXERCISE_SESSIONS, SummaryFields.exerciseSessions(many, zone).size)
    }
}
