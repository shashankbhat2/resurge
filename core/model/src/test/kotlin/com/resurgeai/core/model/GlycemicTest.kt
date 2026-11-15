package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same vectors as functions/test/glycemic-weight.test.ts, so the app and MCP agree. */
class GlycemicTest {
    private fun food(g: Double, carbs: Double, protein: Double, fat: Double, fiber: Double = 0.0, gi: Double? = null) =
        FoodItem("f", g, Nutrition(0.0, protein, carbs, fat, fiber), glycemicIndex = gi)

    @Test
    fun `predicts GI from macros`() {
        assertEquals(58, Glycemic.predictGi(Nutrition(0.0, 22.0, 100.0, 15.0, 8.0)))
        assertEquals(71, Glycemic.predictGi(Nutrition(0.0, 2.0, 80.0, 1.0, 0.0)))
        assertEquals(32, Glycemic.predictGi(Nutrition(0.0, 20.0, 10.0, 20.0, 5.0)))
        assertNull(Glycemic.predictGi(Nutrition(0.0, 25.0, 0.0, 10.0)))
    }

    @Test
    fun `dal rice matches the server`() {
        assertEquals(
            Glycemic.Estimate(58, 53, Glycemic.Band.HIGH, Glycemic.Basis.PREDICTED, 92),
            Glycemic.estimate(listOf(food(100.0, 100.0, 22.0, 15.0, 8.0))),
        )
    }

    @Test
    fun `assistant GI wins and mixes by available carbs`() {
        val mixed = Glycemic.estimate(listOf(food(100.0, 100.0, 22.0, 15.0, 8.0), food(150.0, 14.0, 0.3, 0.2, 2.4, gi = 36.0)))
        assertEquals(Glycemic.Basis.MIXED, mixed.basis)
        assertEquals(109, mixed.availableCarbsG)
        assertEquals(Math.round((58 * 92 + 36 * 17.4) / 109.4).toInt(), mixed.gi)
        assertEquals(Glycemic.Basis.ASSISTANT, Glycemic.estimate(listOf(food(150.0, 14.0, 0.3, 0.2, 2.4, gi = 36.0))).basis)
    }

    @Test
    fun `no GI with almost no carbs, and bands`() {
        assertEquals(Glycemic.Estimate(null, 0, Glycemic.Band.LOW, Glycemic.Basis.PREDICTED, 2), Glycemic.estimate(listOf(food(200.0, 1.0, 25.0, 10.0))))
        assertEquals(listOf(Glycemic.Band.LOW, Glycemic.Band.MEDIUM, Glycemic.Band.HIGH), listOf(10, 19, 20).map(Glycemic::mealBand))
        assertEquals(listOf(Glycemic.Band.LOW, Glycemic.Band.MEDIUM, Glycemic.Band.HIGH), listOf(79, 120, 121).map(Glycemic::dayBand))
    }

    @Test
    fun `fibre target follows calories`() {
        assertEquals(31, MacroTargets.from(2200).fiber)
    }
}
