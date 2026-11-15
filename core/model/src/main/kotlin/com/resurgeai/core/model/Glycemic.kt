package com.resurgeai.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PREDICTED glycaemic index (GI) and glycaemic load (GL) of meals. Not a measurement: there is no GI
 * table behind it. Each food's GI is estimated from its macros (fibre, protein and fat blunt the
 * glucose response) unless the AI assistant supplied one over MCP. Mirrors functions/src/lib/glycemic.ts;
 * keep the two in step (shared test vector: "Dal rice").
 */
object Glycemic {
    enum class Band { LOW, MEDIUM, HIGH }
    enum class Basis { PREDICTED, ASSISTANT, MIXED }

    data class Estimate(
        /** Carb-weighted GI of the meal, or null under [MIN_CARBS_G] of available carbs. */
        val gi: Int?,
        val gl: Int,
        val band: Band,
        val basis: Basis,
        val availableCarbsG: Int,
    )

    const val MIN_CARBS_G = 5.0
    const val NOTE = "Predicted from carbs, fibre, protein and fat (or a GI your assistant sent). Not measured, and it can be well off."

    /** GI of one food from its per-100 g macros, or null without carbs. */
    fun predictGi(per100g: Nutrition): Int? {
        val carbs = per100g.carbs
        if (carbs <= 0) return null
        val gi = 72 - 25 * min(4 * per100g.fiber / carbs, 1.0) - 15 * min((per100g.protein + per100g.fat) / carbs, 1.0)
        return gi.coerceIn(25.0, 95.0).roundToInt()
    }

    /** Per meal: low <= 10, medium 11-19, high >= 20. */
    fun mealBand(gl: Int): Band = when { gl <= 10 -> Band.LOW; gl < 20 -> Band.MEDIUM; else -> Band.HIGH }

    /** Per day: low < 80, medium 80-120, high > 120. */
    fun dayBand(gl: Int): Band = when { gl < 80 -> Band.LOW; gl <= 120 -> Band.MEDIUM; else -> Band.HIGH }

    fun estimate(items: List<FoodItem>): Estimate {
        var avail = 0.0
        var weighted = 0.0
        var supplied = 0
        var counted = 0
        for (i in items) {
            val a = max(i.per100g.carbs - i.per100g.fiber, 0.0) * i.grams / 100
            if (a <= 0) continue
            val given = i.glycemicIndex?.takeIf { it in 0.0..110.0 }
            val gi = given ?: predictGi(i.per100g)?.toDouble() ?: continue
            counted++
            if (given != null) supplied++
            avail += a
            weighted += gi * a
        }
        val basis = when {
            counted > 0 && supplied == counted -> Basis.ASSISTANT
            supplied > 0 -> Basis.MIXED
            else -> Basis.PREDICTED
        }
        if (avail < MIN_CARBS_G) return Estimate(null, 0, Band.LOW, basis, avail.roundToInt())
        // Round like JavaScript's Math.round (half up) so the app and MCP agree.
        val gi = kotlin.math.floor(weighted / avail + 0.5).toInt()
        val gl = kotlin.math.floor(gi * avail / 100 + 0.5).toInt()
        return Estimate(gi, gl, mealBand(gl), basis, kotlin.math.floor(avail + 0.5).toInt())
    }

    fun estimate(meal: Meal): Estimate = estimate(meal.items)
}
