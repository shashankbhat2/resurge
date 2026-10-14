package com.resurgeai.feature.fitness

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.data.fitness.WorkoutRecord
import com.resurgeai.core.data.fitness.WorkoutsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.StrengthExercise
import com.resurgeai.core.model.StrengthSet
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.UserProfile
import com.resurgeai.core.model.WorkoutMetricsInput
import com.resurgeai.core.model.WorkoutSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Workout editor and activity picker. Record with :feature:fitness:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xhdpi")
class WorkoutEditorScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val lastWeek = Instant.now().minusSeconds(5 * 86_400L)
    private val history = listOf(
        WorkoutRecord(
            "prev", WorkoutSource.MANUAL, "weights", null, lastWeek, lastWeek.plusSeconds(2_700),
            WorkoutMetricsInput(exercises = listOf(StrengthExercise("Bench press", listOf(StrengthSet(8, 60.0), StrengthSet(8, 60.0), StrengthSet(8, 60.0))))),
            notes = null, deleted = false,
        ),
    )
    private val repo = mockk<WorkoutsRepository>(relaxed = true) {
        coEvery { latestWeightKg() } returns 76.0
        every { records(any()) } returns flowOf(history)
    }
    private val session = mockk<SessionRepository> {
        every { session } returns flowOf(SessionState.Ready(UserProfile("u", "Shashank", null, null, setOf(Goal.BUILD_MUSCLE), Targets(), true)))
    }

    private fun editor(name: String, activityId: String, scheme: ColorScheme = ColorScheme.Light, edit: (WorkoutEditorViewModel) -> Unit = {}) {
        val vm = WorkoutEditorViewModel(repo, session, SavedStateHandle(mapOf("activityId" to activityId)))
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen {
                    val state by vm.state.collectAsStateWithLifecycle()
                    WorkoutEditorScreen(state, vm, onBack = {}, onChangeActivity = {})
                }
            }
        }
        compose.waitForIdle()
        edit(vm)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/editor_$name.png")
    }

    @Test fun strength() = editor("strength", "weights") { vm ->
        val key = vm.state.value.exercises.first().key
        vm.renameExercise(key, "Bench press")
        vm.updateSet(key, 0, StrengthSet(8, 62.5))
        vm.addSet(key)
        vm.addSet(key)
        vm.updateMetrics { it.copy(rpe = 7) }
    }

    @Test fun runDark() = editor("run_dark", "run", ColorScheme.Dark) { vm ->
        vm.updateMetrics { it.copy(distanceM = 5_200.0, rpe = 6) }
    }

    @Test fun picker() {
        val vm = ActivityPickerViewModel(repo)
        compose.setContent {
            ResurgeTheme(colorScheme = ColorScheme.Light) { Screen { ActivityPickerRoute(onPick = {}, onBack = {}, viewModel = vm) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/activity_picker.png")
    }
}
