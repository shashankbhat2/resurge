package com.resurgeai.core.data.food

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealEstimateParsingTest {
    @Test
    fun `parses items with per-100 g nutrition and blank question as none`() {
        val estimate = MealsRepository.parseEstimate(
            mapOf(
                "title" to "Dal rice",
                "items" to listOf(
                    mapOf("name" to "Rice", "grams" to 180, "kcal" to 130, "protein" to 2.7, "carbs" to 28, "fat" to 0.3, "fiber" to 0.4, "confidence" to 0.9),
                    mapOf("name" to "Dal", "grams" to 150.5, "kcal" to 116, "protein" to 9, "carbs" to 20, "fat" to 0.4, "fiber" to 8, "confidence" to 0.5),
                ),
                "question" to "",
                "options" to emptyList<String>(),
                "notes" to "Assumed one teaspoon of ghee",
            ),
        )
        assertEquals("Dal rice", estimate.title)
        assertEquals(2, estimate.items.size)
        assertEquals(234.0, estimate.items[0].nutrition.kcal, 0.001)
        assertEquals(true, estimate.items[1].lowConfidence)
        assertNull(estimate.question)
        assertEquals("Assumed one teaspoon of ghee", estimate.notes)
    }

    @Test(expected = MealAnalysisException::class)
    fun `no items is an error the user can act on`() {
        MealsRepository.parseEstimate(mapOf("title" to "", "items" to emptyList<Any>()))
    }
}
