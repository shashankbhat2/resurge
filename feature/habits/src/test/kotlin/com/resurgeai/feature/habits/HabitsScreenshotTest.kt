package com.resurgeai.feature.habits

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.Habit
import com.resurgeai.core.model.HabitSettings
import com.resurgeai.core.model.HabitStatus
import com.resurgeai.core.model.HabitStreaks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Record with :feature:habits:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi")
class HabitsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 14)

    private fun item(id: String, name: String, icon: String, color: String, pattern: String, todayStatus: HabitStatus?): HabitItem {
        // pattern: one char per day ending yesterday: d = done, m = missed, . = unlogged
        val log = pattern.reversed().mapIndexedNotNull { i, c ->
            val date = today.minusDays(i + 1L)
            when (c) { 'd' -> date to HabitStatus.DONE; 'm' -> date to HabitStatus.MISSED; else -> null }
        }.toMap() + listOfNotNull(todayStatus?.let { today to it })
        val habit = Habit(id, name, icon, color, id.hashCode(), false, today.minusDays(pattern.length.toLong()))
        return HabitItem(habit, HabitStreaks.compute(habit.createdDate, today, log), todayStatus)
    }

    private val items = listOf(
        item("a", "In bed by 23:00", "bed", "sleep", "dddmdddddddddd", HabitStatus.DONE),
        item("b", "Walk 10,000 steps", "walk", "activity", "ddmdmmdddd", null),
        item("c", "Drink 2.5 L of water", "droplet", "water", "dddddddd", null),
        item("d", "Read for 15 minutes", "book", "accent", "mmdmdm", HabitStatus.MISSED),
    )

    private fun capture(name: String, state: HabitsUiState, scheme: ColorScheme = ColorScheme.Light) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen {
                    HabitsScreen(state, onSwipe = { _, _ -> }, onUndo = {}, onShowStreaks = {}, onToggleDay = { _, _ -> }, onOpenHabit = {}, onAddHabit = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("src/test/screenshots/habits_$name.png")
    }

    private val state = HabitsUiState(loading = false, today = today, settings = HabitSettings(), items = items)

    @Test fun deckLight() = capture("deck_light", state.copy(lastSwiped = "a"))

    @Test fun deckDark() = capture("deck_dark", state, ColorScheme.Dark)

    @Test fun streaksLight() = capture("streaks_light", state.copy(showStreaksEarly = true))

    @Test fun streaksDark() = capture("streaks_dark", state.copy(showStreaksEarly = true), ColorScheme.Dark)
}
