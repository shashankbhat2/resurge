package com.resurgeai.feature.food

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.FoodSource
import com.resurgeai.core.model.Micro
import com.resurgeai.core.model.MacroTargets
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import com.resurgeai.core.model.Targets
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Record with :feature:food:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1900dp-xhdpi")
class FoodScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneId.systemDefault()
    private fun item(name: String, g: Double, kcal: Double, p: Double, c: Double, f: Double, conf: Double = 0.9, source: FoodSource? = null, micros: Map<Micro, Double> = emptyMap()) =
        FoodItem(name, g, Nutrition(kcal, p, c, f, fiber = c * 0.08, micros = micros), conf, source)

    private fun meal(id: String, type: MealType, hour: Int, title: String, source: MealSource, vararg items: FoodItem) =
        Meal(id, type, today.atTime(hour, 15).atZone(zone).toInstant(), today, source, title, items.toList())

    private val breakfast = meal("b", MealType.BREAKFAST, 8, "Masala omelette with toast", MealSource.TEXT,
        item("Masala omelette (2 eggs)", 130.0, 175.0, 11.0, 3.0, 13.0), item("Whole wheat toast", 60.0, 250.0, 9.0, 43.0, 3.4))
    private val lunch = meal("l", MealType.LUNCH, 13, "Paneer butter masala with rice", MealSource.SCAN,
        item(
            "Steamed basmati rice", 180.0, 130.0, 2.7, 28.0, 0.3,
            source = FoodSource("cofid:11-858", "CoFID 2021", "Rice, white, basmati, boiled in unsalted water"),
            micros = mapOf(Micro.SODIUM to 1.0, Micro.POTASSIUM to 35.0, Micro.IRON to 0.2, Micro.CALCIUM to 3.0, Micro.FOLATE to 3.0, Micro.SUGAR to 0.1),
        ),
        item(
            "Paneer butter masala", 200.0, 210.0, 8.5, 9.0, 16.0, 0.55,
            source = FoodSource("components", "Built from 5 ingredients", "Paneer 70 g; Tomato 60 g; Butter 10 g; Cream 15 g; Onion 30 g"),
            micros = mapOf(Micro.SODIUM to 310.0, Micro.CALCIUM to 190.0, Micro.IRON to 0.6, Micro.SAT_FAT to 9.5, Micro.SUGAR to 3.0, Micro.VIT_A to 120.0, Micro.VIT_C to 6.0),
        ),
        item("Cucumber raita", 80.0, 60.0, 3.0, 5.0, 3.0, source = FoodSource("estimate", "Estimate", verified = false)))

    private fun past(id: String, daysAgo: Long, kcal: Double) = Meal(
        id, MealType.LUNCH, today.minusDays(daysAgo).atTime(13, 0).atZone(zone).toInstant(), today.minusDays(daysAgo),
        MealSource.TEXT, "Dal, rice and sabzi", listOf(item("Dal, rice and sabzi", 100.0, kcal, kcal * 0.04, kcal * 0.13, kcal * 0.03)),
    )

    // Two weeks of history with every state: on target, under, over, nothing logged.
    private val history = listOf(
        past("p1", 1, 2950.0), past("p2", 2, 2150.0), past("p3", 3, 1200.0), past("p5", 5, 2050.0),
        past("p6", 6, 2600.0), past("p7", 7, 900.0), past("p8", 8, 2250.0), past("p10", 10, 1900.0),
    )
    private val allMeals = listOf(breakfast, lunch) + history
    private val from = today.minusDays(14)

    private fun stateFor(selected: LocalDate, meals: List<Meal> = allMeals) = FoodUiState(
        loading = false,
        today = today,
        selected = selected,
        calendar = FoodFeed.calendar(meals, today, from, 2200),
        day = FoodFeed.day(meals, selected),
        targets = Targets(2200, 3000),
        macroTargets = MacroTargets.from(2200),
        waterMl = if (selected == today) 1250 else 2100,
        weekDays = FoodFeed.week(meals, selected),
        hasAnyMeals = meals.isNotEmpty(),
    )

    private val state = stateFor(today)

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit, scheme: ColorScheme = ColorScheme.Light) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ResurgeTheme(colorScheme = scheme) { Screen { content() } } }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("src/test/screenshots/food_$name.png")
    }

    private fun feed(s: FoodUiState) = @androidx.compose.runtime.Composable {
        FoodScreen(s, onOpenMeal = {}, onLogMeal = {}, onAddWater = {}, onDelete = {}, onUndo = {}, onUndoTimeout = {})
    }

    @Test fun feed() = capture("feed", feed(state))
    @Test fun feedDark() = capture("feed_dark", feed(state), ColorScheme.Dark)

    @Test fun feedLoggerOff() = capture("feed_logger_off", feed(state.copy(logOnDevice = false)))
    @Test fun feedPastDay() = capture("feed_past_day", feed(stateFor(today.minusDays(1))), ColorScheme.Dark)
    @Test fun feedEmptyDay() = capture("feed_empty_day", feed(stateFor(today.minusDays(4))), ColorScheme.Dark)
    @Test fun empty() = capture("empty", feed(stateFor(today, emptyList())))

    private fun manual(s: ManualMealState) = @androidx.compose.runtime.Composable {
        ManualMealScreen(s, onBack = {}, onSave = { _, _, _ -> }, onDelete = {})
    }

    @Test fun manualNew() = capture("manual_new", manual(ManualMealState()))
    @Test fun manualEditDark() = capture("manual_edit_dark", manual(ManualMealState(meal = breakfast)), ColorScheme.Dark)

    private val reviewState get() = MealReviewState(
        loading = false,
        title = lunch.title,
        type = MealType.LUNCH,
        items = lunch.items.mapIndexed { i, it -> EditableItem(i, it) },
        question = "Was the curry made with cream?",
        options = listOf("Yes", "No", "Not sure"),
        notes = "Assumed about a tablespoon of butter in the curry.",
        source = MealSource.SCAN,
        eatenBefore = 720.0,
        mealsBefore = 1,
        targetKcal = 2000,
    )

    private fun reviewVm() = MealReviewViewModel(mockk<MealsRepository>(relaxed = true), MealDraftStore(), SavedStateHandle(), mockk(relaxed = true))

    @Test fun review() = capture("review", { MealReviewScreen(reviewState, reviewVm(), onBack = {}) })

    @Test fun reviewSources() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ResurgeTheme { Screen { MealReviewScreen(reviewState, reviewVm(), onBack = {}) } } }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("Fix results", ignoreCase = true).performClick()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().captureRoboImage("src/test/screenshots/food_review_sources.png")
    }

    @Test fun barcodeReview() = capture("barcode_review", {
        val product = item(
            "Maggi 2-minute masala noodles", 70.0, 384.0, 8.0, 58.0, 13.0,
            source = FoodSource("off:8901058815387", "Open Food Facts", "Maggi 2-minute masala noodles"),
            micros = mapOf(Micro.SODIUM to 890.0, Micro.SUGAR to 3.0, Micro.SAT_FAT to 6.0),
        )
        MealReviewScreen(reviewState.copy(
            title = "Maggi masala noodles",
            type = MealType.SNACK,
            source = MealSource.BARCODE,
            items = listOf(EditableItem(0, product)),
            question = null,
            options = emptyList(),
            notes = "Nutrition is for the dry product. Check the portion against your pack.",
        ), reviewVm(), onBack = {})
    })

    @Test fun reviewDark() = capture(
        "review_dark",
        { MealReviewScreen(reviewState.copy(question = null, options = emptyList()), reviewVm(), onBack = {}) },
        ColorScheme.Dark,
    )

    @Test
    fun analyzing() {
        val vm = MealReviewViewModel(mockk<MealsRepository>(relaxed = true), MealDraftStore(), SavedStateHandle(), mockk(relaxed = true))
        val s = MealReviewState(
            loading = false,
            analyzing = MealReviewViewModel.TEXT_STEPS[1],
            description = "2 rotis, dal and a bowl of curd",
            source = MealSource.TEXT,
        )
        capture("analyzing", { MealReviewScreen(s, vm, onBack = {}) })
    }
}
