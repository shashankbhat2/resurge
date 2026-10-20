package com.resurgeai.feature.weight

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.GoalCelebration
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sin

/** Record with :feature:weight:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1500dp-xhdpi")
class WeightScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val zone = ZoneId.systemDefault()

    /** 110 days from 82.1 kg down about 3.6 kg, weighed most days, with daily noise. */
    private val weighIns: List<WeighIn> = run {
        var seed = 7L
        fun rnd(): Double { seed = seed * 16807 % 2147483647; return seed / 2147483647.0 }
        (0 until 110).mapNotNull { i ->
            val base = 82.1 - 3.6 * i / 109 + 0.25 * sin(i / 9.0)
            val keep = (i % 7 != 3 && rnd() < 0.62) || i == 0 || i == 109
            if (!keep) null else WeighIn("w$i", Math.round((base + (rnd() - 0.5)) * 10) / 10.0, today.minusDays(109L - i).atTime(7, 10).atZone(zone).toInstant())
        }
    }
    private val goal = WeightGoal(82.1, today.minusDays(109), 74.0, 0.5)

    private fun state(range: WeightRange, units: UnitSystem = UnitSystem.METRIC) = WeightUiState(
        loading = false,
        today = today,
        units = units,
        weighIns = weighIns,
        trend = WeightMath.trend(weighIns, today, zone),
        goal = goal,
        range = range,
        customFrom = if (range == WeightRange.CUSTOM) today.minusDays(97) else null,
        customTo = if (range == WeightRange.CUSTOM) today.minusDays(16) else null,
    )

    private fun capture(name: String, s: WeightUiState, scheme: ColorScheme = ColorScheme.Light, strip: ScaleStrip? = null) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { WeightScreen(s, onBack = {}, onRange = {}, onCustom = { _, _ -> }, onSave = { _, _, _, _ -> }, onDelete = {}, onSetGoal = { _, _ -> }, onClearGoal = {}, scaleStrip = strip) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/weight_$name.png")
    }

    @Test fun month() = capture("month", state(WeightRange.MONTH))
    @Test fun monthDark() = capture("month_dark", state(WeightRange.MONTH), ColorScheme.Dark)
    @Test fun custom() = capture("custom", state(WeightRange.CUSTOM))
    @Test fun imperialAll() = capture("all_lb", state(WeightRange.ALL, UnitSystem.IMPERIAL))
    @Test fun empty() = capture("empty", WeightUiState(loading = false, today = today))

    private val scaleModel = "Mi Body Composition Scale 2"

    /** The newest weigh-in came from a smart scale, so its row says so. */
    private fun scaleState() = state(WeightRange.MONTH).let { st ->
        val last = st.weighIns.last()
        st.copy(weighIns = st.weighIns.dropLast(1) + last.copy(source = WeighInSource.SCALE, device = scaleModel))
    }

    @Test fun scaleListening() = capture("scale_listening", scaleState(), strip = ScaleStrip.Listening("Listening for $scaleModel"))
    @Test fun scaleWeighing() = capture("scale_weighing", scaleState(), strip = ScaleStrip.Weighing(scaleModel, 78.2))
    @Test fun scaleSaved() = capture("scale_saved", scaleState(), strip = ScaleStrip.Saved(scaleModel, scaleState().weighIns.last()))
    @Test fun scaleUnusual() = capture("scale_unusual", scaleState(), ColorScheme.Dark, strip = ScaleStrip.Unusual(scaleModel, 61.3))

    // Trend is about 78.5 here, so 78.6 is just reached; the maintain range is 77.5 to 79.5.
    @Test fun reached() = capture("reached", state(WeightRange.MONTH).copy(goal = goal.copy(goalKg = 78.6, reachedOn = today.minusDays(3))))
    @Test fun maintaining() = capture("maintaining", state(WeightRange.MONTH).copy(goal = WeightGoal.maintain(78.0, today.minusDays(20))))

    private fun captureAward(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { ResurgeTheme(colorScheme = ColorScheme.Light) { Screen { content() } } }
        compose.onRoot().captureRoboImage("src/test/screenshots/weight_$name.png")
    }

    private val celebrating = CelebrationState(
        celebration = GoalCelebration.Milestone(2, 3, 4.0, WeightMath.nextMilestone(goal, 77.9)),
        goal = goal,
        units = WeightUnits(UnitSystem.METRIC),
        commander = false,
    )

    @Test fun milestoneAward() = captureAward("milestone_award") { MilestoneAward(celebrating.celebration as GoalCelebration.Milestone, celebrating, onDone = {}) }

    /** The confetti mid-burst: the clock is held, then moved [atMs] into the animation. */
    private fun captureConfetti(name: String, goal: Boolean, atMs: Long) {
        compose.mainClock.autoAdvance = false
        val c: GoalCelebration = if (goal) GoalCelebration.Goal(8.1, today.minusDays(109), 3) else celebrating.celebration
        compose.setContent {
            ResurgeTheme(colorScheme = ColorScheme.Light) {
                Screen {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        if (c is GoalCelebration.Goal) GoalReward(c, celebrating.copy(celebration = c), onNewGoal = {}, onMaintain = {}, onLater = {})
                        else MilestoneAward(c as GoalCelebration.Milestone, celebrating, onDone = {})
                        com.resurgeai.core.designsystem.components.Confetti(androidx.compose.ui.Modifier.fillMaxSize(), pieces = if (goal) 160 else 60, durationMs = if (goal) 3_600 else 2_400)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(atMs)
        compose.onRoot().captureRoboImage("src/test/screenshots/weight_$name.png")
    }

    @Test fun milestoneConfetti() = captureConfetti("milestone_confetti", goal = false, atMs = 700)
    @Test fun goalConfetti() = captureConfetti("goal_confetti", goal = true, atMs = 900)

    @Test fun goalReward() {
        val c = GoalCelebration.Goal(8.1, today.minusDays(109), 3)
        captureAward("goal_reward") { GoalReward(c, celebrating.copy(celebration = c, commander = true), onNewGoal = {}, onMaintain = {}, onLater = {}) }
    }

    @Test
    fun `periods clamp to the first weigh-in and custom keeps its ends`() {
        val s = state(WeightRange.YEAR)
        assertEquals(today.minusDays(109), s.period!!.first)
        val c = state(WeightRange.CUSTOM)
        assertEquals(today.minusDays(97) to today.minusDays(16), c.period)
        assertEquals(today.minusDays(109) to today, c.chartSpan)
        assertTrue(c.change!! < 0)
    }

    @Test
    fun `units convert for display only`() {
        val lb = WeightUnits(UnitSystem.IMPERIAL)
        assertEquals("176.4 lb", lb.format(80.0))
        assertEquals(80.0, lb.toKg(lb.toUnit(80.0)), 1e-9)
        assertEquals("−1.0 kg", WeightUnits(UnitSystem.METRIC).signed(-1.04))
        assertEquals("0.25 kg", WeightUnits(UnitSystem.METRIC).pace(0.25))
    }
}
