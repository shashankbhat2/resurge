package com.resurgeai.feature.food

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.FoodItem
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
@Config(sdk = [35], qualifiers = "w400dp-h1500dp-xhdpi")
class FoodScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)
    private val zone = ZoneId.systemDefault()
    private fun item(name: String, g: Double, kcal: Double, p: Double, c: Double, f: Double, conf: Double = 0.9) =
        FoodItem(name, g, Nutrition(kcal, p, c, f), conf)

    private fun meal(id: String, type: MealType, hour: Int, title: String, source: MealSource, vararg items: FoodItem) =
        Meal(id, type, today.atTime(hour, 15).atZone(zone).toInstant(), today, source, title, items.toList())

    private val breakfast = meal("b", MealType.BREAKFAST, 8, "Masala omelette with toast", MealSource.TEXT,
        item("Masala omelette (2 eggs)", 130.0, 175.0, 11.0, 3.0, 13.0), item("Whole wheat toast", 60.0, 250.0, 9.0, 43.0, 3.4))
    private val lunch = meal("l", MealType.LUNCH, 13, "Paneer butter masala with rice", MealSource.SCAN,
        item("Steamed basmati rice", 180.0, 130.0, 2.7, 28.0, 0.3), item("Paneer butter masala", 200.0, 210.0, 8.5, 9.0, 16.0, 0.55),
        item("Cucumber raita", 80.0, 60.0, 3.0, 5.0, 3.0))

    private val state = FoodUiState(
        loading = false,
        today = today,
        todayTotals = breakfast.totals + lunch.totals,
        targets = Targets(2200, 3000),
        macroTargets = MacroTargets.from(2200),
        waterMl = 1250,
        days = listOf(FoodDay(today, listOf(MealType.BREAKFAST to listOf(breakfast), MealType.LUNCH to listOf(lunch)))),
    )

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
    @Test fun empty() = capture("empty", feed(state.copy(days = emptyList(), todayTotals = Nutrition.Zero, waterMl = 0)))

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
