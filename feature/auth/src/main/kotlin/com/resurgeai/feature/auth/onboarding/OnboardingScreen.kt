package com.resurgeai.feature.auth.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.ChoiceCard
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.TimeStepper
import com.resurgeai.core.designsystem.components.NumberStepper
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.components.StepIndicator
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.HabitDraft
import com.resurgeai.core.model.HabitSettings
import java.time.LocalTime
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.resurgeai.core.model.UserProfile

@Composable
fun OnboardingRoute(
    profile: UserProfile,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val healthLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract) {
        viewModel.refreshHealthStatus()
    }
    BackHandler(enabled = state.step != OnboardingStep.Goals) { viewModel.back() }
    OnboardingScreen(
        onConnectHealth = {
            if (state.healthAvailability == HealthConnectAvailability.AVAILABLE) {
                healthLauncher.launch(viewModel.healthPermissionsToRequest)
            } else {
                runCatching { context.startActivity(viewModel.installHealthConnectIntent()) }
            }
        },
        firstName = profile.firstName,
        state = state,
        onToggleGoal = viewModel::toggleGoal,
        onCaloriesChange = viewModel::setCalories,
        onWaterChange = viewModel::setWater,
        onToggleHabit = viewModel::toggleHabit,
        onAddHabit = viewModel::addCustomHabit,
        onResetTimeChange = viewModel::setResetTime,
        onBack = viewModel::back,
        onNext = { viewModel.next(profile.uid) },
    )
}

@Composable
fun OnboardingScreen(
    firstName: String?,
    state: OnboardingUiState,
    onToggleGoal: (Goal) -> Unit,
    onCaloriesChange: (Int) -> Unit,
    onWaterChange: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onConnectHealth: () -> Unit = {},
    onToggleHabit: (HabitDraft) -> Unit = {},
    onAddHabit: (String) -> Unit = {},
    onResetTimeChange: (LocalTime) -> Unit = {},
) {
    Screen {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.step != OnboardingStep.Goals) {
                    IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                StepIndicator(
                    current = state.step.ordinal,
                    total = OnboardingStep.entries.size,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Spacer(Modifier.size(44.dp))
            }

            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
                label = "onboarding-step",
            ) { step ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    when (step) {
                        OnboardingStep.Goals -> GoalsStep(firstName, state.goals, onToggleGoal)
                        OnboardingStep.Targets -> TargetsStep(state, onCaloriesChange, onWaterChange)
                        OnboardingStep.Habits -> HabitsStep(state, onToggleHabit, onAddHabit, onResetTimeChange)
                        OnboardingStep.Health -> HealthStep(state, onConnectHealth)
                        OnboardingStep.Ready -> ReadyStep(state)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                state.error?.let {
                    Text(
                        it,
                        style = Resurge.type.caption,
                        color = Resurge.colors.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                Button(
                    text = when (state.step) {
                        OnboardingStep.Goals -> "Continue"
                        OnboardingStep.Targets -> "Continue"
                        OnboardingStep.Habits -> if (state.habits.isEmpty()) "Pick at least one habit" else "Continue"
                        OnboardingStep.Health -> if (state.healthConnected) "Continue" else "Skip for now"
                        OnboardingStep.Ready -> "Start using Resurge"
                    },
                    onClick = onNext,
                    enabled = state.canContinue,
                    loading = state.saving,
                    variant = if (state.step == OnboardingStep.Health && !state.healthConnected) ButtonVariant.Ghost else ButtonVariant.Primary,
                    trailingIcon = ResurgeIcons.ArrowRight.takeIf { state.step != OnboardingStep.Ready },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = Resurge.type.brandLarge)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = Resurge.type.body, color = Resurge.colors.textMuted)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun GoalsStep(firstName: String?, selected: Set<Goal>, onToggle: (Goal) -> Unit) {
    StepHeader(
        title = if (firstName != null) "Hi $firstName, what are you working on?" else "What are you working on?",
        subtitle = "Pick one or more. Your coach uses these to focus its advice.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Goal.entries.forEach { goal ->
            val (icon, tint) = goal.visual()
            ChoiceCard(
                title = goal.label,
                description = goal.description,
                selected = goal in selected,
                onClick = { onToggle(goal) },
                icon = icon,
                iconTint = tint,
            )
        }
    }
}

@Composable
private fun TargetsStep(
    state: OnboardingUiState,
    onCaloriesChange: (Int) -> Unit,
    onWaterChange: (Int) -> Unit,
) {
    StepHeader(
        title = "Set your daily targets",
        subtitle = "A starting point — you can change these any time in Profile.",
    )
    Card {
        TargetLabel(ResurgeIcons.Flame, Resurge.colors.energy, "Calories eaten")
        Spacer(Modifier.height(16.dp))
        NumberStepper(
            value = state.targets.calories,
            onValueChange = onCaloriesChange,
            step = 50,
            range = 1200..4500,
            unit = "kcal per day",
        )
    }
    Spacer(Modifier.height(12.dp))
    Card {
        TargetLabel(ResurgeIcons.Droplet, Resurge.colors.water, "Water")
        Spacer(Modifier.height(16.dp))
        NumberStepper(
            value = state.targets.waterMl,
            onValueChange = onWaterChange,
            step = 250,
            range = 1000..5000,
            unit = "ml per day",
        )
    }
}

@Composable
private fun HabitsStep(
    state: OnboardingUiState,
    onToggle: (HabitDraft) -> Unit,
    onAdd: (String) -> Unit,
    onResetTimeChange: (LocalTime) -> Unit,
) {
    StepHeader(
        title = "Build your daily habits",
        subtitle = "Pick at least one. Each day you'll swipe through them: right for done, left for not done.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (state.suggestions + state.customHabits).forEach { habit ->
            val selected = state.habits.any { it.name == habit.name }
            ChoiceCard(
                title = habit.name,
                description = null,
                selected = selected,
                onClick = { onToggle(habit) },
                icon = HabitVisuals.icon(habit.icon),
                iconTint = HabitVisuals.color(habit.color),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    val custom = rememberTextFieldState()
    val submit = {
        onAdd(custom.text.toString())
        custom.clearText()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(custom, placeholder = "Add your own habit", modifier = Modifier.weight(1f), onDone = submit)
        Spacer(Modifier.width(10.dp))
        IconButton(ResurgeIcons.Plus, contentDescription = "Add habit", onClick = submit, enabled = state.canAddHabit)
    }
    Text(
        "${state.habits.size} of ${HabitSettings.MAX_ACTIVE} selected",
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
    Spacer(Modifier.height(24.dp))
    Card {
        TargetLabel(ResurgeIcons.Habits, Resurge.colors.accent, "New day starts at")
        Spacer(Modifier.height(16.dp))
        TimeStepper(
            time = state.habitSettings.resetTime,
            onTimeChange = onResetTimeChange,
            caption = "Habits reset daily at this time",
        )
    }
}

@Composable
private fun TargetLabel(icon: Int, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint = tint, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = Resurge.type.label, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun HealthStep(state: OnboardingUiState, onConnect: () -> Unit) {
    StepHeader(
        title = "Bring in your health data",
        subtitle = "Resurge reads from Health Connect — the place your phone and watch apps already share data.",
    )
    Card {
        HealthRow(ResurgeIcons.Bed, Resurge.colors.sleep, "Sleep", "Duration and stages")
        Spacer(Modifier.height(14.dp))
        HealthRow(ResurgeIcons.Footprints, Resurge.colors.activity, "Activity", "Steps, distance and workouts")
        Spacer(Modifier.height(14.dp))
        HealthRow(ResurgeIcons.Flame, Resurge.colors.energy, "Energy", "Calories burned")
        Spacer(Modifier.height(14.dp))
        HealthRow(ResurgeIcons.Heart, Resurge.colors.heart, "Heart", "Heart rate and resting heart rate")
    }
    Spacer(Modifier.height(16.dp))
    when {
        state.healthConnected -> Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ResurgeIcons.Check, tint = Resurge.colors.success, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text("Health Connect is connected", style = Resurge.type.label)
        }
        state.healthAvailability == HealthConnectAvailability.NOT_SUPPORTED -> Text(
            "Health Connect isn't available on this device. You can still log food and water.",
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
        )
        else -> Button(
            text = if (state.healthAvailability == HealthConnectAvailability.AVAILABLE) "Connect Health Connect" else "Install Health Connect",
            onClick = onConnect,
            variant = ButtonVariant.Secondary,
            leadingIcon = ResurgeIcons.Watch,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Only daily totals are synced to your account so your coach can spot trends. You can revoke access any time in Health Connect.",
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
    )
}

@Composable
private fun HealthRow(icon: Int, tint: Color, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, tint = tint, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = Resurge.type.label)
            Text(body, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
    }
}

@Composable
private fun ReadyStep(state: OnboardingUiState) {
    StepHeader(
        title = "You're all set",
        subtitle = if (state.healthConnected) "Your dashboard will fill with data from your phone and watch."
        else "You can connect Health Connect any time from the Today tab.",
    )
    Card {
        SummaryRow("Goals", state.goals.joinToString { it.label })
        Spacer(Modifier.height(12.dp))
        SummaryRow("Calories", "%,d kcal / day".format(state.targets.calories))
        Spacer(Modifier.height(12.dp))
        SummaryRow("Water", "%,d ml / day".format(state.targets.waterMl))
        Spacer(Modifier.height(12.dp))
        SummaryRow("Habits", state.habits.joinToString { it.name })
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.width(88.dp))
        Text(value, style = Resurge.type.label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Goal.visual(): Pair<Int, Color> = when (this) {
    Goal.LOSE_WEIGHT -> ResurgeIcons.Scale to Resurge.colors.energy
    Goal.BUILD_MUSCLE -> ResurgeIcons.Dumbbell to Resurge.colors.heart
    Goal.SLEEP_BETTER -> ResurgeIcons.Bed to Resurge.colors.sleep
    Goal.MOVE_MORE -> ResurgeIcons.Footprints to Resurge.colors.activity
    Goal.EAT_BETTER -> ResurgeIcons.Food to Resurge.colors.energy
    Goal.HYDRATE -> ResurgeIcons.Droplet to Resurge.colors.water
}

@Preview
@Composable
private fun OnboardingPreview() {
    ResurgeTheme {
        OnboardingScreen(
            firstName = "Shashank",
            state = OnboardingUiState(goals = setOf(Goal.SLEEP_BETTER)),
            onToggleGoal = {}, onCaloriesChange = {}, onWaterChange = {}, onBack = {}, onNext = {},
        )
    }
}
