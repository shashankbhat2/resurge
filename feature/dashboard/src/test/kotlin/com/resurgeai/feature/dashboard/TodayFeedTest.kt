package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TodayFeedTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 10)
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone)

    private fun meal(id: String, time: Instant) = Meal(id, MealType.LUNCH, time, time.atZone(zone).toLocalDate(), MealSource.TEXT, id, emptyList())
    private fun workout(id: String, time: Instant) = Workout(id, WorkoutSource.MANUAL, "run", null, time, time.plusSeconds(1800), WorkoutMetricsInput())
    private fun insight(id: String, time: Instant) = CoachInsight(id, InsightKind.ACTIVITY, null, id, "", emptyList(), null, time, null)

    @Test
    fun `mixes entries newest first by day, drops older days and the lead insight`() {
        val lead = insight("lead", at(today, 9))
        val days = TodayFeed.build(
            today = today,
            meals = listOf(meal("lunch", at(today, 13)), meal("old", at(today.minusDays(2), 13))),
            workouts = listOf(workout("run", at(today, 7)), workout("gym", at(today.minusDays(1), 18))),
            sleep = listOf(SleepSession(at(today.minusDays(1), 23), at(today, 6), emptyMap(), null, id = "s")),
            insights = listOf(lead, insight("older", at(today.minusDays(1), 19))),
            lead = lead,
            zone = zone,
        )
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(listOf("meal_lunch", "workout_run", "sleep_s"), days[0].items.map { it.key })
        assertEquals(listOf("insight_older", "workout_gym"), days[1].items.map { it.key })
    }
}
