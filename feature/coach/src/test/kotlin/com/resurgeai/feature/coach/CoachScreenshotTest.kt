package com.resurgeai.feature.coach

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.InsightHighlight
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.TurnRole
import com.resurgeai.core.model.WeeklyArea
import com.resurgeai.core.model.WeeklyDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/** Record with :feature:coach:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1100dp-xhdpi")
class CoachScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.now()
    private val insights = listOf(
        CoachInsight(
            "w", InsightKind.WEEKLY, null, "A steadier week: more sleep, four active days", "",
            emptyList(), null, now.minus(Duration.ofHours(2)),
            WeeklyDetails(
                summary = "You averaged 7h 12m of sleep, up 25 minutes on last week, and trained on four days.",
                wins = listOf("Four workouts, including your fastest 5K this month", "Bedtime within 30 min on 5 nights"),
                areas = listOf(
                    WeeklyArea("training", "up", "4 of 4 sessions done", "4 of 4"),
                    WeeklyArea("weight", "down", "82.5 → 81.9 kg", "−0.6 kg"),
                    WeeklyArea("food", "steady", "Logged 11 of 14 meals", "79%"),
                    WeeklyArea("sleep", "down", "Average 6h 38m", "−22 min"),
                ),
                focus = listOf("In bed by 23:00 on training nights", "Move Friday's strength session before 19:00"),
                weekStart = "2026-10-04", weekEnd = "2026-10-10",
                days = listOf(true, false, true, true, true, false, true),
                goodDays = 5, target = 6,
            ),
        ),
        CoachInsight(
            "a", InsightKind.ACTIVITY, "ACTIVITY", "Strong 5K after a short night",
            "Your pace was 8 seconds per km faster than your last five runs, even after 6h 15m of sleep. Heart rate peaked at 176.",
            listOf(InsightHighlight("Pace", "5:12/km"), InsightHighlight("Avg HR", "152"), InsightHighlight("Distance", "5.0 km")),
            "Keep tomorrow easy: a 30-minute walk is plenty.", now.minus(Duration.ofHours(20)), null,
        ),
    )

    private fun capture(name: String, state: CoachUiState, scheme: ColorScheme = ColorScheme.Light) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { CoachScreen(state, onBack = {}, onSend = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/coach_$name.png")
    }

    private fun captureInsights(name: String, state: InsightsUiState, scheme: ColorScheme = ColorScheme.Light) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ResurgeTheme(colorScheme = scheme) { Screen { InsightsScreen(state, onBack = {}) } } }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/insights_$name.png")
    }

    private val goal = com.resurgeai.core.model.MajorGoal(com.resurgeai.core.model.TrainingGoal.LOSE_FAT, 12, java.time.LocalDate.of(2026, 9, 13), 84.0, 78.0)

    private fun captureDetail(name: String, insight: CoachInsight, scheme: ColorScheme = ColorScheme.Dark) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ResurgeTheme(colorScheme = scheme) { Screen { InsightDetailScreen(InsightDetailUiState(true, insight, goal), onBack = {}, onAsk = {}) } } }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/insight_$name.png")
    }

    @Test fun insightsDark() = captureInsights("dark", InsightsUiState(insights, loaded = true, goal = goal), ColorScheme.Dark)

    @Test fun weeklyDetailDark() = captureDetail("weekly_dark", insights[0])

    @Test fun noteDetailLight() = captureDetail("note_light", insights[1], ColorScheme.Light)

    @Test fun chatAboutInsightDark() = capture(
        "chat_about_dark",
        CoachUiState(
            insight = insights[1],
            hasPlan = true,
            turns = listOf(
                CoachTurn("0", TurnRole.USER, "Should I move Friday's session so I'm fresher?", null, false),
                CoachTurn(
                    "1", TurnRole.COACH,
                    "Yes. Friday's strength comes a day after your longest run, and your Recovery has been under 50 on both Fridays this month. Thursday is free in your plan.",
                    CoachModel.FAST, false,
                    offer = com.resurgeai.core.model.PlanChangeOffer("Move Friday's strength session to Thursday", "Move to Thursday"),
                ),
            ),
        ),
        ColorScheme.Dark,
    )

    @Test fun insightsLight() = captureInsights("light", InsightsUiState(insights, loaded = true))

    @Test fun insightsEmptyDark() = captureInsights("empty_dark", InsightsUiState(loaded = true), ColorScheme.Dark)

    @Test fun chatEmptyDark() = capture("chat_empty_dark", CoachUiState(), ColorScheme.Dark)

    @Test fun chatEmptyCommander() = capture("chat_empty_commander", CoachUiState(coachStyle = com.resurgeai.core.model.CoachStyle.COMMANDER))

    @Test fun chatLight() = capture(
        "chat_light",
        CoachUiState(
            model = CoachModel.SMART,
            turns = listOf(
                CoachTurn("0", TurnRole.USER, "Why am I so tired today?", null, false),
                CoachTurn("1", TurnRole.COACH, "You slept 6h 15m last night — 55 minutes under your 30-day average — and your resting heart rate is up 3 bpm.\n- Keep training light today\n- Aim for bed by 22:30", CoachModel.FAST, false),
            ),
            pending = PendingReply(userText = "Should I still run tomorrow?", streamedText = "Yes, an easy 20–30 minutes", baseline = 2),
        ),
    )
}
