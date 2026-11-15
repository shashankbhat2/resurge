package com.resurgeai.feature.food

import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ManualMealsTest {
    @Test
    fun `parses plain and comma decimals, rejects negatives and junk`() {
        assertEquals(12.0, ManualMeals.parse(" 12 ")!!, 0.0)
        assertEquals(12.5, ManualMeals.parse("12,5")!!, 0.0)
        assertNull(ManualMeals.parse("-3"))
        assertNull(ManualMeals.parse("abc"))
        assertNull(ManualMeals.parse(""))
    }

    @Test
    fun `totals become one 100 g item, like MCP log_meal, so they read back exactly`() {
        val items = ManualMeals.items("  Dal and rice ", 640.0, 22.0, 98.0, 14.0, 9.0)
        assertEquals(1, items.size)
        assertEquals("Dal and rice", items[0].name)
        assertEquals(100.0, items[0].grams, 0.0)
        assertEquals(Nutrition(640.0, 22.0, 98.0, 14.0, 9.0), items[0].nutrition)
        assertEquals("Meal", ManualMeals.items(" ", 100.0, 0.0, 0.0, 0.0)[0].name)
    }

    @Test
    fun `kcal is required and values stay within the MCP limits`() {
        assertTrue(ManualMeals.valid(640.0, 22.0, null, null))
        assertFalse(ManualMeals.valid(null, 22.0))
        assertFalse(ManualMeals.valid(10_001.0))
        assertFalse(ManualMeals.valid(500.0, 1_001.0))
    }

    @Test
    fun `only single-entry meals have editable totals`() {
        fun meal(n: Int) = Meal("m", MealType.LUNCH, Instant.EPOCH, LocalDate.EPOCH, MealSource.MANUAL, "Lunch",
            List(n) { FoodItem("Item $it", 100.0, Nutrition(100.0)) })
        assertTrue(ManualMeals.totalsEditable(null))
        assertTrue(ManualMeals.totalsEditable(meal(1)))
        assertFalse(ManualMeals.totalsEditable(meal(3)))
    }
}
