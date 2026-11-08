package com.resurgeai.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

class FitnessFoodTest {
    @Test
    fun `catalog ids are unique and every activity is searchable by name`() {
        val ids = ActivityCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ActivityCatalog.all.forEach { assertTrue(it.name, it in ActivityCatalog.search(it.name)) }
    }

    @Test
    fun `search matches aliases and ranks name prefixes first`() {
        assertEquals("run", ActivityCatalog.search("jog").first().id)
        assertEquals("weights", ActivityCatalog.search("gym").first().id)
        assertEquals("walk", ActivityCatalog.search("wal").first().id)
        assertTrue(ActivityCatalog.search("zzz").isEmpty())
    }

    @Test
    fun `health connect types map to catalog activities with a fallback`() {
        assertEquals("run", ActivityCatalog.forHealthConnect("running").id)
        assertEquals("weights", ActivityCatalog.forHealthConnect("strength_training").id)
        assertEquals("swim_pool", ActivityCatalog.forHealthConnect("swimming_pool").id)
        assertEquals("other", ActivityCatalog.forHealthConnect("unknown_sport").id)
        assertEquals("other", ActivityCatalog.forHealthConnect(null).id)
    }

    @Test
    fun `pace speed and calorie estimates`() {
        assertEquals(330L, FitnessMath.paceSecPerKm(5000.0, Duration.ofMinutes(27).plusSeconds(30)))
        assertNull("too short to have a pace", FitnessMath.paceSecPerKm(100.0, Duration.ofMinutes(1)))
        assertEquals(20.0, FitnessMath.speedKmh(10_000.0, Duration.ofMinutes(30))!!, 0.001)
        // Running (9.8 MET), 70 kg, 30 min → 343 kcal
        assertEquals(343, FitnessMath.estimateKcal(ActivityCatalog.byId("run"), 30, 70.0))
        assertEquals(1000.0, FitnessMath.swimDistanceM(WorkoutMetricsInput(laps = 40, poolLengthM = 25))!!, 0.0)
    }

    @Test
    fun `food items scale nutrition per 100 g`() {
        val rice = FoodItem("Rice", 150.0, Nutrition(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3))
        assertEquals(195.0, rice.nutrition.kcal, 0.001)
        assertEquals(42.0, rice.copy(grams = 150.0).nutrition.carbs, 0.001)
        val meal = Meal("m", MealType.LUNCH, java.time.Instant.EPOCH, java.time.LocalDate.EPOCH, MealSource.SCAN, "Lunch",
            listOf(rice, FoodItem("Dal", 200.0, Nutrition(kcal = 100.0, protein = 6.0))))
        assertEquals(395.0, meal.totals.kcal, 0.001)
        assertEquals(16.05, meal.totals.protein, 0.001)
    }

    @Test
    fun `micronutrient totals add what is known and scale with the portion`() {
        val dal = FoodItem("Dal", 200.0, Nutrition(kcal = 100.0, micros = mapOf(Micro.IRON to 1.0, Micro.SODIUM to 300.0)))
        val rice = FoodItem("Rice", 150.0, Nutrition(kcal = 130.0, micros = mapOf(Micro.IRON to 0.2)))
        val meal = Meal("m", MealType.LUNCH, java.time.Instant.EPOCH, java.time.LocalDate.EPOCH, MealSource.SCAN, "Lunch", listOf(dal, rice))
        assertEquals(2.3, meal.totals[Micro.IRON]!!, 0.001)
        assertEquals(600.0, meal.totals[Micro.SODIUM]!!, 0.001)
        assertNull(meal.totals[Micro.VIT_C])
    }

    @Test
    fun `meal slot from time of day and reminder toggles`() {
        assertEquals(MealType.BREAKFAST, MealType.forTime(LocalTime.of(8, 0)))
        assertEquals(MealType.LUNCH, MealType.forTime(LocalTime.of(13, 30)))
        assertEquals(MealType.DINNER, MealType.forTime(LocalTime.of(20, 15)))
        assertEquals(MealType.SNACK, MealType.forTime(LocalTime.of(16, 30)))
        val off = MealReminders().with(MealType.LUNCH, null)
        assertNull(off.lunch)
        assertEquals(LocalTime.of(8, 30), off.breakfast)
    }

    @Test
    fun `macro targets split the calorie target 30-40-30`() {
        assertEquals(MacroTargets(protein = 150, carbs = 200, fat = 67), MacroTargets.from(2000))
    }
}
