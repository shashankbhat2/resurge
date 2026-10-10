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
                areas = listOf(WeeklyArea("sleep", "up", "7h 12m average, deep sleep steady"), WeeklyArea("activity", "steady", "9,136 steps a day"), WeeklyArea("heart", "down", "Resting HR 58, down 2 bpm")),
                focus = listOf("Keep bedtime before 23:30", "Add one easy long walk"),
                weekStart = "2026-10-04", weekEnd = "2026-10-10",
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
                Screen { CoachScreen(state, onBack = {}, onTabChange = {}, onSend = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("src/test/screenshots/coach_$name.png")
    }

    @Test fun insightsLight() = capture("insights_light", CoachUiState(tab = CoachTab.INSIGHTS, insights = insights, insightsLoaded = true))

    @Test fun insightsEmptyDark() = capture("insights_empty_dark", CoachUiState(tab = CoachTab.INSIGHTS, insightsLoaded = true), ColorScheme.Dark)

    @Test fun chatLight() = capture(
        "chat_light",
        CoachUiState(
            tab = CoachTab.CHAT,
            insightsLoaded = true,
            model = CoachModel.SMART,
            turns = listOf(
                CoachTurn("0", TurnRole.USER, "Why am I so tired today?", null, false),
                CoachTurn("1", TurnRole.COACH, "You slept 6h 15m last night — 55 minutes under your 30-day average — and your resting heart rate is up 3 bpm.\n- Keep training light today\n- Aim for bed by 22:30", CoachModel.FAST, false),
            ),
            pending = PendingReply(userText = "Should I still run tomorrow?", streamedText = "Yes, an easy 20–30 minutes", baseline = 2),
        ),
    )
}
