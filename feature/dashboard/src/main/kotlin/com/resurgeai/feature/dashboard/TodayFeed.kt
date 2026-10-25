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
     * One day's meals, workouts, sleep and insights, newest first. Sleep counts on the day it ended.
     * The insight shown in the coach card ([lead]) isn't repeated in the list.
     */
    fun build(
        date: LocalDate,
        meals: List<Meal>,
        workouts: List<Workout>,
        sleep: List<SleepSession>,
        insights: List<CoachInsight>,
        lead: CoachInsight?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): FeedDay {
        val items = buildList<FeedItem> {
            meals.filter { it.date == date }.forEach { add(FeedItem.MealEntry(it)) }
            workouts.forEach { add(FeedItem.WorkoutEntry(it)) }
            sleep.forEach { add(FeedItem.SleepEntry(it)) }
            insights.filter { it.id != lead?.id }.forEach { add(FeedItem.InsightEntry(it)) }
        }
        return FeedDay(
            date,
            items.filter { it is FeedItem.MealEntry || it.time.atZone(zone).toLocalDate() == date }.sortedByDescending { it.time },
        )
    }

    /** The day's newest insight not yet seen, for the coach card; null when there's none. */
    fun lead(date: LocalDate, insights: List<CoachInsight>, dismissed: Set<String>, zone: ZoneId = ZoneId.systemDefault()): CoachInsight? =
        insights.filter { it.createdAt.atZone(zone).toLocalDate() == date && !it.seen && it.id !in dismissed }.maxByOrNull { it.createdAt }
}
