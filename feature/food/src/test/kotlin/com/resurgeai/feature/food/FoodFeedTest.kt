package com.resurgeai.feature.food

import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
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
    fun `last 7 days is today and the six before`() {
        val (from, to) = FoodFeed.bounds(today, FoodFeed.Filters(range = FoodFeed.Range.LAST_7))
        assertEquals(today.minusDays(6), from)
        assertEquals(today, to)
    }

    @Test
    fun `custom range uses the two picked days, either order`() {
        val filters = FoodFeed.Filters(range = FoodFeed.Range.CUSTOM, customFrom = today, customTo = today.minusDays(3))
        val (from, to) = FoodFeed.bounds(today, filters)
        assertEquals(today.minusDays(3), from)
        assertEquals(today, to)
    }

    @Test
    fun `search matches title or an item name`() {
        assertTrue(FoodFeed.matchesQuery(lunch, "paneer"))
        assertTrue(FoodFeed.matchesQuery(lunch, "basmati"))
        assertFalse(FoodFeed.matchesQuery(lunch, "omelette"))
        assertTrue(FoodFeed.matchesQuery(lunch, "  "))
    }

    @Test
    fun `type filter and last 7 hide older dinners`() {
        val days = FoodFeed.days(all, today, FoodFeed.Filters(types = setOf(MealType.LUNCH)))
        assertEquals(listOf("l"), days.flatMap { it.meals.map { m -> m.id } })
    }

    @Test
    fun `yesterday range is only that day`() {
        val days = FoodFeed.days(all, today, FoodFeed.Filters(range = FoodFeed.Range.YESTERDAY))
        assertEquals(listOf(today.minusDays(1)), days.map { it.date })
        assertEquals(listOf("s"), days.single().meals.map { it.id })
    }

    @Test
    fun `high kcal sorts meals inside a day`() {
        val days = FoodFeed.days(listOf(breakfast, lunch), today, FoodFeed.Filters(sort = FoodFeed.Sort.HIGH_KCAL))
        assertEquals(listOf("l", "b"), days.single().meals.map { it.id })
    }

    @Test
    fun `oldest lists days then meals ascending`() {
        val days = FoodFeed.days(all, today, FoodFeed.Filters(range = FoodFeed.Range.LAST_30, sort = FoodFeed.Sort.OLDEST))
        assertEquals(listOf(today.minusDays(10), today.minusDays(1), today), days.map { it.date })
        assertEquals(listOf("b", "l"), days.last().meals.map { it.id })
    }

    @Test
    fun `toggling every type clears the filter`() {
        var types = emptySet<MealType>()
        MealType.entries.forEach { types = FoodFeed.toggleType(types, it) }
        assertTrue(types.isEmpty())
    }
}
