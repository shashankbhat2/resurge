package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.Workout
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One entry in the Today timeline. [time] orders the feed (newest first). */
sealed interface FeedItem {
    val key: String
    val time: Instant

    data class MealEntry(val meal: Meal) : FeedItem {
        override val key get() = "meal_${meal.id}"
        override val time: Instant get() = meal.loggedAt
    }

    data class WorkoutEntry(val workout: Workout) : FeedItem {
        override val key get() = "workout_${workout.id}"
        override val time: Instant get() = workout.start
    }

    /** Last night's sleep, placed at wake-up time. */
    data class SleepEntry(val session: SleepSession) : FeedItem {
        override val key get() = "sleep_${session.id.ifEmpty { session.end.toString() }}"
        override val time: Instant get() = session.end
    }

    data class InsightEntry(val insight: CoachInsight) : FeedItem {
        override val key get() = "insight_${insight.id}"
        override val time: Instant get() = insight.createdAt
    }
}

data class FeedDay(val date: LocalDate, val items: List<FeedItem>)

object TodayFeed {
    /**
     * Today's and yesterday's meals, workouts, sleep and insights, grouped by day, newest first.
     * The insight shown at the top of the page ([lead]) isn't repeated in the timeline.
     */
    fun build(
        today: LocalDate,
        meals: List<Meal>,
        workouts: List<Workout>,
        sleep: List<SleepSession>,
        insights: List<CoachInsight>,
        lead: CoachInsight?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<FeedDay> {
        val from = today.minusDays(1)
        val items = buildList<FeedItem> {
            meals.forEach { add(FeedItem.MealEntry(it)) }
            workouts.forEach { add(FeedItem.WorkoutEntry(it)) }
            sleep.forEach { add(FeedItem.SleepEntry(it)) }
            insights.filter { it.id != lead?.id }.forEach { add(FeedItem.InsightEntry(it)) }
        }
        return items
            .filter { !it.time.atZone(zone).toLocalDate().isBefore(from) && !it.time.atZone(zone).toLocalDate().isAfter(today) }
            .sortedByDescending { it.time }
            .groupBy { it.time.atZone(zone).toLocalDate() }
            .map { (date, list) -> FeedDay(date, list) }
            .sortedByDescending { it.date }
    }
}
