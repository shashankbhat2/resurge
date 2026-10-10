package com.resurgeai.core.data.health

import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.ExerciseSession
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.SourceKind
import com.resurgeai.core.model.WorkoutMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class EventPayloadsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val end = Instant.parse("2026-10-11T01:30:00Z") // 07:00 local
    private val watch = DataSource(SourceKind.WEARABLE, "com.fitbit.FitbitMobile", "Fitbit", "Pixel Watch 3")

    private fun run(minutes: Long, endAt: Instant = end, id: String = "hc-1") =
        ExerciseSession(id, "Running", null, endAt.minus(Duration.ofMinutes(minutes)), endAt, watch)

    private fun night(hours: Long, endAt: Instant = end) = SleepSession(
        start = endAt.minus(Duration.ofHours(hours)),
        end = endAt,
        stages = mapOf(SleepStage.DEEP to Duration.ofMinutes(80), SleepStage.LIGHT to Duration.ofMinutes(250),
            SleepStage.REM to Duration.ofMinutes(90), SleepStage.AWAKE to Duration.ofMinutes(30)),
        source = watch,
        id = "night-1",
    )

    @Test
    fun `workouts need ten minutes and fifteen minutes to settle`() {
        assertFalse(EventPayloads.isPostableWorkout(run(9), end.plus(Duration.ofHours(1))))
        assertFalse("not settled yet", EventPayloads.isPostableWorkout(run(30), end.plus(Duration.ofMinutes(10))))
        assertTrue(EventPayloads.isPostableWorkout(run(30), end.plus(Duration.ofMinutes(15))))
        assertFalse("needs an id", EventPayloads.isPostableWorkout(run(30, id = ""), end.plus(Duration.ofHours(1))))
    }

    @Test
    fun `naps are skipped and nights settle for thirty minutes`() {
        assertFalse("nap", EventPayloads.isPostableSleep(night(2), end.plus(Duration.ofHours(1))))
        assertFalse(EventPayloads.isPostableSleep(night(7), end.plus(Duration.ofMinutes(20))))
        assertTrue(EventPayloads.isPostableSleep(night(7), end.plus(Duration.ofMinutes(30))))
    }

    @Test
    fun `workout payload has local date, pace and pending status`() {
        val p = EventPayloads.workout(run(26), WorkoutMetrics(5000.0, 390.4, 420.0, 152, 176, 98, 4300), zone)
        assertEquals("workout", p["type"])
        assertEquals("hc-1", p["hcId"])
        assertEquals("2026-10-11", p["localDate"])
        assertEquals("Asia/Kolkata", p["timezone"])
        assertEquals("pending", p["status"])
        @Suppress("UNCHECKED_CAST") val w = p["workout"] as Map<String, Any?>
        assertEquals(26, w["durationMin"])
        assertEquals(312L, w["paceSecPerKm"]) // 26 min / 5 km = 5:12/km
        assertEquals(390L, w["activeKcal"])
    }

    @Test
    fun `short distances get no pace`() {
        val p = EventPayloads.workout(run(30), WorkoutMetrics(150.0, null, null, null, null, null, null), zone)
        @Suppress("UNCHECKED_CAST") val w = p["workout"] as Map<String, Any?>
        assertNull(w["paceSecPerKm"])
    }

    @Test
    fun `sleep payload carries stages, efficiency and local clock times`() {
        val p = EventPayloads.sleep(night(8), null, zone)
        @Suppress("UNCHECKED_CAST") val s = p["sleep"] as Map<String, Any?>
        assertEquals(480L, s["durationMin"])
        assertEquals(450L, s["asleepMin"])
        assertEquals(80L, s["deepMin"])
        assertEquals("23:00", s["bedtime"])
        assertEquals("07:00", s["wake"])
        assertEquals(450.0 / 480.0, s["efficiency"] as Double, 0.001)
    }
}
