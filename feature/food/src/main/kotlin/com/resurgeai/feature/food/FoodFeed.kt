package com.resurgeai.feature.food

import com.resurgeai.core.model.Meal
import java.time.LocalDate

/** The Food tab's day calendar. Pure so tests don't need Android. */
object FoodFeed {
    /** Days of history loaded from Firestore before today (today plus the 89 before it). */
    const val HISTORY_DAYS = 89L

    /** Days after today drawn (dimmed) in the strip, so today can sit in the middle. */
    const val FUTURE_DAYS = 7L

    /** Within this share of the calorie target counts as on target. */
    const val ON_TARGET_BAND = 0.10

    /** How a day went, as drawn on its calendar cell. */
    enum class DayState(val label: String) {
        FUTURE("Ahead"),
        EMPTY("Nothing logged"),
        UNDER("Under target"),
        ON_TARGET("On target"),
        OVER("Over target"),
    }

    data class CalendarDay(
        val date: LocalDate,
        val kcal: Double,
        val meals: Int,
        val state: DayState,
    )

    fun dayState(date: LocalDate, today: LocalDate, kcal: Double, meals: Int, target: Int): DayState {
        if (date.isAfter(today)) return DayState.FUTURE
        if (meals == 0) return DayState.EMPTY
        val share = kcal / target.coerceAtLeast(1)
        return when {
            share > 1 + ON_TARGET_BAND -> DayState.OVER
            share >= 1 - ON_TARGET_BAND -> DayState.ON_TARGET
            else -> DayState.UNDER
        }
    }

    /** Every day from [from] to [FUTURE_DAYS] after [today], oldest first. */
    fun calendar(meals: List<Meal>, today: LocalDate, from: LocalDate, target: Int): List<CalendarDay> {
        val byDate = meals.groupBy { it.date }
        val last = today.plusDays(FUTURE_DAYS)
        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .map { date ->
                val dayMeals = byDate[date].orEmpty()
                val kcal = dayMeals.sumOf { it.totals.kcal }
                CalendarDay(date, kcal, dayMeals.size, dayState(date, today, kcal, dayMeals.size, target))
            }
            .toList()
    }

    /** The meals of one day in the order they were eaten. */
    fun day(meals: List<Meal>, date: LocalDate): FoodDay =
        FoodDay(date, meals.filter { it.date == date }.sortedBy { it.loggedAt })

    /** The 7 days ending on [end], for the glycaemic chart. */
    fun week(meals: List<Meal>, end: LocalDate): List<FoodDay> =
        (6 downTo 0).map { day(meals, end.minusDays(it.toLong())) }.filter { it.meals.isNotEmpty() }

    /** Clamps a picked day to the loaded history; future days fall back to today. */
    fun clamp(date: LocalDate, today: LocalDate, from: LocalDate): LocalDate = when {
        date.isAfter(today) -> today
        date.isBefore(from) -> from
        else -> date
    }
}
