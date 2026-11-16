package com.resurgeai.feature.food

import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.feature.food.FoodFeed.DayState
import com.resurgeai.core.model.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FoodFeedTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun meal(
        id: String,
        type: MealType,
        date: LocalDate,
        title: String,
        kcal: Double,
        itemName: String = title,
        hour: Int = 12,
    ) = Meal(
        id, type, date.atTime(hour, 0).toInstant(ZoneOffset.UTC), date, MealSource.MANUAL, title,
        listOf(FoodItem(itemName, 100.0, Nutrition(kcal, 10.0, 20.0, 5.0, 3.0))),
    )

    private val breakfast = meal("b", MealType.BREAKFAST, today, "Masala omelette", 420.0, "Eggs", 8)
    private val lunch = meal("l", MealType.LUNCH, today, "Paneer butter masala", 890.0, "Basmati rice", 13)
    private val yesterdaySnack = meal("s", MealType.SNACK, today.minusDays(1), "Apple", 80.0, hour = 16)
    private val oldDinner = meal("d", MealType.DINNER, today.minusDays(10), "Dal", 500.0, hour = 20)
    private val all = listOf(breakfast, lunch, yesterdaySnack, oldDinner)

    @Test
    fun dayStateFollowsCaloriesAgainstTarget() {
        assertEquals(DayState.EMPTY, FoodFeed.dayState(today, today, 0.0, 0, 2000))
        assertEquals(DayState.UNDER, FoodFeed.dayState(today, today, 1200.0, 2, 2000))
        assertEquals(DayState.ON_TARGET, FoodFeed.dayState(today, today, 1850.0, 3, 2000))
        assertEquals(DayState.ON_TARGET, FoodFeed.dayState(today, today, 2200.0, 3, 2000))
        assertEquals(DayState.OVER, FoodFeed.dayState(today, today, 2600.0, 4, 2000))
        assertEquals(DayState.FUTURE, FoodFeed.dayState(today.plusDays(1), today, 0.0, 0, 2000))
    }

    @Test
    fun calendarRunsFromHistoryStartToAWeekAhead() {
        val from = today.minusDays(FoodFeed.HISTORY_DAYS)
        val cal = FoodFeed.calendar(all, today, from, 2000)
        assertEquals(FoodFeed.HISTORY_DAYS + FoodFeed.FUTURE_DAYS + 1, cal.size.toLong())
        assertEquals(from, cal.first().date)
        assertEquals(today.plusDays(FoodFeed.FUTURE_DAYS), cal.last().date)
        val t = cal.single { it.date == today }
        assertEquals(2, t.meals)
        assertEquals(1310.0, t.kcal, 0.01)
        assertEquals(DayState.UNDER, t.state)
        assertEquals(DayState.EMPTY, cal.single { it.date == today.minusDays(2) }.state)
        assertTrue(cal.filter { it.date.isAfter(today) }.all { it.state == DayState.FUTURE })
    }

    @Test
    fun dayListsOnlyThatDayInEatingOrder() {
        val day = FoodFeed.day(listOf(lunch, yesterdaySnack, breakfast), today)
        assertEquals(listOf("b", "l"), day.meals.map { it.id })
        assertEquals(1310.0, day.totals.kcal, 0.01)
        assertTrue(FoodFeed.day(all, today.minusDays(2)).meals.isEmpty())
    }

    @Test
    fun weekCoversSevenDaysEndingOnTheDay() {
        val week = FoodFeed.week(all, today)
        assertEquals(listOf(today.minusDays(1), today), week.map { it.date })
        assertFalse(week.any { it.date == oldDinner.date })
    }

    @Test
    fun clampKeepsPicksInsideHistory() {
        val from = today.minusDays(FoodFeed.HISTORY_DAYS)
        assertEquals(today, FoodFeed.clamp(today.plusDays(3), today, from))
        assertEquals(from, FoodFeed.clamp(from.minusDays(5), today, from))
        assertEquals(today.minusDays(4), FoodFeed.clamp(today.minusDays(4), today, from))
    }
}
