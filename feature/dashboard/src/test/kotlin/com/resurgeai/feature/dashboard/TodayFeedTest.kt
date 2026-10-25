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
    fun `one day newest first, without other days or the lead insight`() {
        val lead = insight("lead", at(today, 9))
        val meals = listOf(meal("lunch", at(today, 13)), meal("old", at(today.minusDays(2), 13)))
        val workouts = listOf(workout("run", at(today, 7)), workout("gym", at(today.minusDays(1), 18)))
        val sleep = listOf(SleepSession(at(today.minusDays(1), 23), at(today, 6), emptyMap(), null, id = "s"))
        val insights = listOf(lead, insight("older", at(today.minusDays(1), 19)))

        val day = TodayFeed.build(today, meals, workouts, sleep, insights, lead, zone)
        assertEquals(listOf("meal_lunch", "workout_run", "sleep_s"), day.items.map { it.key })

        // Yesterday has its own entries; the night that ended this morning belongs to today.
        val yesterday = TodayFeed.build(today.minusDays(1), meals, workouts, sleep, insights, lead = null, zone = zone)
        assertEquals(listOf("insight_older", "workout_gym"), yesterday.items.map { it.key })
    }

    @Test
    fun `the lead is the day's newest unseen insight`() {
        val insights = listOf(
            insight("seen", at(today, 11)).copy(seen = true),
            insight("dismissed", at(today, 10)),
            insight("morning", at(today, 7)),
            insight("yesterday", at(today.minusDays(1), 20)),
        )
        assertEquals("morning", TodayFeed.lead(today, insights, dismissed = setOf("dismissed"), zone = zone)?.id)
        assertEquals("yesterday", TodayFeed.lead(today.minusDays(1), insights, emptySet(), zone)?.id)
        assertEquals(null, TodayFeed.lead(today.minusDays(2), insights, emptySet(), zone))
    }
}
