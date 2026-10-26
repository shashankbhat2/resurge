package com.resurgeai.core.data.food

import com.resurgeai.core.data.food.MealsRepository.Companion.toMap
import com.resurgeai.core.model.Micro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `reads micronutrients and the source of each food, and stores them round trip`() {
        val estimate = MealsRepository.parseEstimate(
            mapOf(
                "title" to "Dal",
                "items" to listOf(
                    mapOf(
                        "name" to "Toor dal", "grams" to 200, "kcal" to 105, "protein" to 6.6, "carbs" to 17, "fat" to 2.9, "fiber" to 2.8, "confidence" to 0.7,
                        "nutrients" to mapOf("kcal" to 105, "iron" to 0.9, "sodium" to null, "folate" to 45.2, "bogus" to 3),
                        "sourceId" to "components", "sourceName" to "Built from 2 ingredients", "sourceDetail" to "Red gram, dal 60 g; Ghee 5 g", "verified" to true,
                    ),
                    mapOf(
                        "name" to "Mystery sweet", "grams" to 50, "kcal" to 400, "protein" to 5, "carbs" to 60, "fat" to 15, "fiber" to 1, "confidence" to 0.5,
                        "nutrients" to mapOf("kcal" to 400), "sourceId" to "estimate", "sourceName" to "Estimate", "sourceDetail" to "No table had this food", "verified" to false,
                    ),
                ),
                "question" to "", "options" to emptyList<String>(), "notes" to "",
            ),
        )
        val dal = estimate.items[0]
        assertEquals(mapOf(Micro.IRON to 0.9, Micro.FOLATE to 45.2), dal.per100g.micros)
        assertEquals(1.8, dal.nutrition[Micro.IRON]!!, 0.001)
        assertEquals("components", dal.source?.id)
        assertFalse(dal.unverified)
        assertTrue(estimate.items[1].unverified)
        assertTrue(estimate.items[1].source!!.isEstimate)

        val stored = MealsRepository.itemFrom(dal.toMap())!!
        assertEquals(dal, stored)
    }

    @Test
    fun `old stored items without micronutrients or source still load`() {
        val item = MealsRepository.itemFrom(mapOf("name" to "Rice", "grams" to 150, "kcal" to 130, "protein" to 2.7, "carbs" to 28, "fat" to 0.3, "fiber" to 0.4))!!
        assertTrue(item.per100g.micros.isEmpty())
        assertNull(item.source)
        assertFalse(item.unverified)
    }

    @Test
    fun `parses a barcode product and treats not found as null`() {
        val product = MealsRepository.parseProduct(
            mapOf(
                "found" to true,
                "product" to mapOf(
                    "sourceId" to "off:8901058000290", "sourceName" to "Open Food Facts", "name" to "Nestlé Maggi", "servingGrams" to 70,
                    "nutrients" to mapOf("kcal" to 384, "protein" to 8.2, "carbs" to 59.6, "fat" to 12.5, "fiber" to null, "sodium" to 1028.3),
                ),
            ),
        )
        assertNotNull(product)
        val item = product!!.toItem()
        assertEquals(70.0, item.grams, 0.0)
        assertEquals(268.8, item.nutrition.kcal, 0.01)
        assertEquals(1028.3, item.per100g[Micro.SODIUM]!!, 0.001)
        assertEquals("off:8901058000290", item.source?.id)
        assertNull(MealsRepository.parseProduct(mapOf("found" to false)))
    }

    @Test(expected = MealAnalysisException::class)
    fun `no items is an error the user can act on`() {
        MealsRepository.parseEstimate(mapOf("title" to "", "items" to emptyList<Any>()))
    }
}
