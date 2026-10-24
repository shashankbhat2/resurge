package com.resurgeai.feature.auth.onboarding

import com.resurgeai.core.designsystem.components.OptionRow
import androidx.compose.ui.text.input.KeyboardType
import com.resurgeai.core.designsystem.components.TypedNumbers
import com.resurgeai.core.designsystem.components.EditableValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.OptionChips
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.StepButton
import com.resurgeai.core.designsystem.components.StepIndicator
import com.resurgeai.core.designsystem.components.Switch
import com.resurgeai.core.designsystem.components.ToggleSquares
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.Experience
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.PaceSignal
import com.resurgeai.core.model.PlanDay
import com.resurgeai.core.model.RunMode
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.TrainingLocation
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.UserProfile
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.model.WorkoutStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    LaunchedEffect(profile.units) { viewModel.setUnits(profile.units) }
    BackHandler(enabled = state.step != OnboardingStep.Goal) { viewModel.back() }
    OnboardingScreen(
        state = state,
        actions = OnboardingActions(
            selectGoal = viewModel::selectGoal,
            setWeeks = viewModel::setWeeks,
            useCustomWeeks = viewModel::useCustomWeeks,
            stepWeeks = viewModel::stepWeeks,
            stepStart = viewModel::stepStart,
            stepTarget = viewModel::stepTarget,
            setStartKg = viewModel::setStartKg,
            setTargetKg = viewModel::setTargetKg,
            setCustomWeeks = viewModel::setCustomWeeks,
            setRace = viewModel::setRace,
            toggleDay = viewModel::toggleDay,
            setLocation = viewModel::setLocation,
            setSessionMin = viewModel::setSessionMin,
            setExperience = viewModel::setExperience,
            setStyle = viewModel::setStyle,
            setRuns = viewModel::setRuns,
            buildPlan = viewModel::buildPlan,
            setDayGoals = viewModel::setDayGoals,
            connectHealth = {
                if (state.healthAvailability == HealthConnectAvailability.AVAILABLE) {
                    healthLauncher.launch(viewModel.healthPermissionsToRequest)
                } else {
                    runCatching { context.startActivity(viewModel.installHealthConnectIntent()) }
                }
            },
            back = viewModel::back,
            next = { viewModel.next(profile.uid) },
        ),
    )
}

/** Everything the onboarding screens can ask the view model to do. */
data class OnboardingActions(
    val selectGoal: (TrainingGoal) -> Unit = {},
    val setWeeks: (Int) -> Unit = {},
    val useCustomWeeks: () -> Unit = {},
    val stepWeeks: (Int) -> Unit = {},
    val stepStart: (Int) -> Unit = {},
    val stepTarget: (Int) -> Unit = {},
    val setStartKg: (Double) -> Unit = {},
    val setTargetKg: (Double) -> Unit = {},
    val setCustomWeeks: (Int) -> Unit = {},
    val setRace: (Double) -> Unit = {},
    val toggleDay: (DayOfWeek) -> Unit = {},
    val setLocation: (TrainingLocation) -> Unit = {},
    val setSessionMin: (Int) -> Unit = {},
    val setExperience: (Experience) -> Unit = {},
    val setStyle: (WorkoutStyle) -> Unit = {},
    val setRuns: (RunMode) -> Unit = {},
    val buildPlan: (String?) -> Unit = {},
    val setDayGoals: (DayGoals) -> Unit = {},
    val connectHealth: () -> Unit = {},
    val back: () -> Unit = {},
    val next: () -> Unit = {},
)

@Composable
fun OnboardingScreen(state: OnboardingUiState, actions: OnboardingActions) {
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
                if (state.step != OnboardingStep.Goal) {
                    IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = actions.back, background = Color.Transparent)
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                StepIndicator(
                    current = state.step.ordinal,
                    total = OnboardingStep.entries.size,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
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
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    when (step) {
                        OnboardingStep.Goal -> GoalStep(state, actions)
                        OnboardingStep.Target -> TargetStep(state, actions)
                        OnboardingStep.Schedule -> ScheduleStep(state, actions)
                        OnboardingStep.Plan -> PlanStep(state, actions)
                        OnboardingStep.Day -> DayStep(state, actions)
                        OnboardingStep.Ready -> ReadyStep(state)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                state.error?.let {
                    Text(
                        it,
                        style = Resurge.type.caption,
                        color = Resurge.colors.down,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }
                Button(
                    text = when (state.step) {
                        OnboardingStep.Schedule -> "Build my plan"
                        OnboardingStep.Plan -> "Use this plan"
                        OnboardingStep.Ready -> "Start"
                        else -> "Continue"
                    },
                    onClick = actions.next,
                    enabled = state.canContinue,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String?, overline: String? = null) {
    overline?.let {
        Overline(it.uppercase(), color = Resurge.colors.textSubtle)
        Spacer(Modifier.height(10.dp))
    }
    Text(title, style = Resurge.type.display)
    subtitle?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun GoalStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepHeader(
        title = "What are you training for?",
        subtitle = "Pick one. Your plan, your daily list and your weekly target all come from it. You can change it later.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TrainingGoal.entries.forEach { goal ->
            ChoiceCard(
                title = goal.label,
                description = goal.description,
                selected = goal == state.goal,
                onClick = { actions.selectGoal(goal) },
                icon = goal.icon(),
                iconTint = Resurge.colors.text,
            )
        }
    }
}

@Composable
private fun TargetStep(state: OnboardingUiState, actions: OnboardingActions) {
    val goal = state.goal ?: return
    when (goal) {
        TrainingGoal.LOSE_FAT, TrainingGoal.BUILD_MUSCLE -> {
            StepHeader(
                title = "Where do you want to land?",
                subtitle = "Start from your latest weigh-in, or set it here.",
            )
            Card(contentPadding = PaddingValues(16.dp)) {
                WeightStepper("Now", state.startKg, state.units, actions.stepStart, actions.setStartKg)
                Spacer(Modifier.height(16.dp))
                WeightStepper("Goal", state.targetKg, state.units, actions.stepTarget, actions.setTargetKg)
                Spacer(Modifier.height(12.dp))
                val change = abs(state.targetKg - state.startKg)
                Text(
                    when {
                        !state.targetValid && goal == TrainingGoal.LOSE_FAT -> "Set a goal below your current weight."
                        !state.targetValid -> "Set a goal above your current weight."
                        goal == TrainingGoal.LOSE_FAT -> "${formatWeight(change, state.units)} to lose"
                        else -> "${formatWeight(change, state.units)} to gain"
                    },
                    style = Resurge.type.label,
                    color = if (state.targetValid) Resurge.colors.textMuted else Resurge.colors.down,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        TrainingGoal.RUN_FURTHER -> {
            StepHeader(title = "What do you want to run?", subtitle = "Pick a distance to build toward.")
            OptionChips(
                options = MajorGoal.RACES.map { it.first },
                selected = state.raceKm,
                onSelect = actions.setRace,
                label = { km -> MajorGoal.RACES.first { it.first == km }.second },
            )
        }
        TrainingGoal.GET_IN_SHAPE -> StepHeader(
            title = "How long do you want to commit?",
            subtitle = "Each week you hit your target is a win. The goal is to stack them.",
        )
    }

    Spacer(Modifier.height(28.dp))
    Overline("IN HOW LONG", color = Resurge.colors.textSubtle)
    Spacer(Modifier.height(10.dp))
    // null is the "Custom" chip.
    OptionChips(
        options = MajorGoal.WEEK_CHOICES + listOf(null),
        selected = if (state.customWeeks || state.weeks !in MajorGoal.WEEK_CHOICES) null else state.weeks,
        onSelect = { if (it == null) actions.useCustomWeeks() else actions.setWeeks(it) },
        label = { it?.let { w -> "$w wk" } ?: "Custom" },
    )
    if (state.customWeeks || state.weeks !in MajorGoal.WEEK_CHOICES) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Resurge.shape.sm)
                .background(Resurge.colors.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("−", enabled = state.weeks > MajorGoal.DURATIONS.first(), contentDescription = "Shorter", size = 40.dp) { actions.stepWeeks(-1) }
            EditableValue(
                display = MajorGoal.durationLabel(state.weeks),
                editText = { "${state.weeks}" },
                onSubmit = { t -> TypedNumbers.parseInt(t)?.let(actions.setCustomWeeks) },
                label = "Duration in weeks",
                style = Resurge.type.headline,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            StepButton("+", enabled = state.weeks < MajorGoal.DURATIONS.last(), contentDescription = "Longer", size = 40.dp) { actions.stepWeeks(1) }
        }
    }
    Spacer(Modifier.height(12.dp))
    state.majorGoal(LocalDate.now())?.takeIf { state.targetValid }?.let { major ->
        val pace = major.paceKgPerWeek
        val signal = major.paceSignal
        if (pace != null && signal != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Resurge.shape.sm)
                    .background(Resurge.colors.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(signal.color()))
                Spacer(Modifier.width(10.dp))
                Text("${formatWeight(pace, state.units)} a week. ", style = Resurge.type.label)
                Text(
                    when (signal) {
                        PaceSignal.UP -> "A pace you can hold."
                        PaceSignal.MID -> "Ambitious, but doable."
                        PaceSignal.DOWN -> "Too fast to keep up."
                    },
                    style = Resurge.type.label,
                    color = Resurge.colors.textMuted,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    Text(
        "${MajorGoal.durationLabel(state.weeks).replaceFirstChar { it.uppercase() }} means ${state.weeks} weekly mini-goals to win.",
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
    )
}

@Composable
private fun WeightStepper(label: String, kg: Double, units: UnitSystem, onStep: (Int) -> Unit, onType: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Resurge.type.label, color = Resurge.colors.textSubtle, modifier = Modifier.width(48.dp))
        StepButton("−", enabled = true, onClick = { onStep(-1) }, contentDescription = "$label weight: less", size = 48.dp)
        EditableValue(
            display = formatWeight(kg, units),
            editText = { if (units == UnitSystem.IMPERIAL) "${(kg / WeightMath.KG_PER_LB).roundToInt()}" else "%.1f".format(kg) },
            onSubmit = { t -> TypedNumbers.parse(t)?.let { onType(if (units == UnitSystem.IMPERIAL) it * WeightMath.KG_PER_LB else it) } },
            label = "$label weight",
            style = Resurge.type.display,
            modifier = Modifier.weight(1f),
        )
        StepButton("+", enabled = true, onClick = { onStep(1) }, contentDescription = "$label weight: more", size = 48.dp)
    }
}

@Composable
private fun ScheduleStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepHeader(
        title = "When can you train?",
        subtitle = "Tap the days. Pick the ones you can protect, not the ones you hope for.",
    )
    val days = DayOfWeek.entries
    ToggleSquares(
        labels = days.map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
        descriptions = days.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
        selected = state.training.days.map { it.ordinal }.toSet(),
        onToggle = { actions.toggleDay(days[it]) },
    )
    Spacer(Modifier.height(10.dp))
    val count = state.training.days.size
    Text(
        when {
            count < TrainingPrefs.DAYS_RANGE.first -> "Pick at least two days."
            else -> "$count training days. The rest are rest days, and those count too."
        },
        style = Resurge.type.caption,
        color = if (state.training.validDays) Resurge.colors.textMuted else Resurge.colors.down,
    )

    Spacer(Modifier.height(24.dp))
    Overline("WHERE", color = Resurge.colors.textSubtle)
    Spacer(Modifier.height(10.dp))
    OptionChips(TrainingLocation.entries, state.training.location, actions.setLocation, { it.label })

    Spacer(Modifier.height(24.dp))
    Overline("SESSION LENGTH", color = Resurge.colors.textSubtle)
    Spacer(Modifier.height(10.dp))
    OptionChips(TrainingPrefs.SESSION_CHOICES, state.training.sessionMin, actions.setSessionMin, { "$it min" })

    Spacer(Modifier.height(24.dp))
    Overline("EXPERIENCE", color = Resurge.colors.textSubtle)
    Spacer(Modifier.height(10.dp))
    OptionChips(Experience.entries, state.training.experience, actions.setExperience, { it.label })

    // A run plan's strength work is a short supplement, so its split isn't asked.
    if (state.goal != TrainingGoal.RUN_FURTHER) {
        Spacer(Modifier.height(24.dp))
        Overline("WORKOUT STYLE", color = Resurge.colors.textSubtle)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutStyle.entries.forEach { style ->
                OptionRow(style.label, style.description, selected = style == state.training.style, onClick = { actions.setStyle(style) })
            }
        }
    }

    if (state.goal != TrainingGoal.RUN_FURTHER) {
        Spacer(Modifier.height(24.dp))
        Overline("RUNNING", color = Resurge.colors.textSubtle)
        Spacer(Modifier.height(6.dp))
        Text(
            "Runs on their own days, or added to your workouts as a warm-up or finisher?",
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
        )
        Spacer(Modifier.height(10.dp))
        OptionChips(RunMode.entries, state.training.runs, actions.setRuns, { it.label })
    }
}

@Composable
private fun PlanStep(state: OnboardingUiState, actions: OnboardingActions) {
    val plan = state.plan
    when {
        state.planLoading -> {
            StepHeader(title = "Building your week", subtitle = "Fitting sessions to your days, kit and level. This takes about 20 seconds.")
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Spinner(color = Resurge.colors.text, modifier = Modifier.size(28.dp))
            }
        }
        plan == null -> {
            StepHeader(title = "Couldn't build your plan", subtitle = state.planError ?: "Something went wrong.")
            Button(text = "Try again", onClick = { actions.buildPlan(null) }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        }
        else -> {
            val goal = state.goal
            StepHeader(
                // The name already says the goal and days; the overline adds how long.
                overline = "${MajorGoal.durationLabel(state.weeks)} plan",
                title = plan.name,
                subtitle = plan.summary,
            )
            state.planError?.let {
                Text(it, style = Resurge.type.caption, color = Resurge.colors.down)
                Spacer(Modifier.height(12.dp))
            }
            Card(contentPadding = PaddingValues(0.dp)) {
                var expanded by remember(plan.id) { mutableStateOf(plan.days.firstOrNull { !it.rest && it.exercises.isNotEmpty() }?.day) }
                plan.days.forEachIndexed { i, day ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
                    PlanDayRow(day, expanded = expanded == day.day, onClick = {
                        if (day.exercises.isNotEmpty()) expanded = if (expanded == day.day) null else day.day
                    })
                }
            }
            if (plan.progression.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(plan.progression, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Spacer(Modifier.height(20.dp))
            SwapSession(onSubmit = { actions.buildPlan(it) })
        }
    }
}

@Composable
private fun PlanDayRow(day: PlanDay, expanded: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (day.exercises.isNotEmpty()) Modifier.clickable(onClickLabel = "Show exercises", onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                day.day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                modifier = Modifier.width(36.dp),
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(Resurge.shape.pill)
                    .background(if (day.rest) Resurge.colors.borderStrong else Resurge.colors.text),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    day.title,
                    style = if (day.rest) Resurge.type.body else Resurge.type.label,
                    color = if (day.rest) Resurge.colors.textMuted else Resurge.colors.text,
                )
                val meta = if (day.rest) day.note else listOfNotNull(day.durationMin?.let { "$it min" }, day.focus.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            Text(
                day.exercises.joinToString(" · ") { "${it.name} ${it.sets}×${it.reps}" },
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
                modifier = Modifier.padding(start = 56.dp),
            )
        } else if (!day.rest && day.target.isNotBlank()) {
            Text(day.target, style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(start = 56.dp, top = 4.dp))
        }
    }
}

@Composable
private fun SwapSession(onSubmit: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!open) {
        Button(text = "Swap a session", onClick = { open = true }, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        return
    }
    val text = rememberTextFieldState()
    val submit = {
        val value = text.text.toString().trim()
        if (value.isNotEmpty()) {
            onSubmit(value)
            text.clearText()
            open = false
        }
    }
    Text("What should change?", style = Resurge.type.label, color = Resurge.colors.textMuted)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        InputField(text, placeholder = "e.g. Cycling instead of Saturday's run", modifier = Modifier.weight(1f), onDone = submit)
        Spacer(Modifier.width(8.dp))
        IconButton(ResurgeIcons.ArrowUp, contentDescription = "Rebuild plan", onClick = submit)
    }
}

@Composable
private fun DayStep(state: OnboardingUiState, actions: OnboardingActions) {
    StepHeader(
        title = "What counts as a good day?",
        subtitle = "Get through the whole list and the day goes on your streak. Keep the list short.",
    )
    val g = state.dayGoals
    Card(contentPadding = PaddingValues(0.dp)) {
        DayGoalRow("Today's session", "The workout, or the walk on rest days", trailing = {
            Text("Always on", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        })
        RowDivider()
        DayGoalToggle("Log 2 meals", if (state.goal?.byWeight == true) "Suggested for your goal" else "Snap or describe them", g.meals > 0) {
            actions.setDayGoals(g.copy(meals = if (it) 2 else 0))
        }
        RowDivider()
        DayGoalToggle("Sleep 7 h", "From Health Connect", g.sleep) { actions.setDayGoals(g.copy(sleep = it)) }
        RowDivider()
        DayGoalToggle("%,d steps".format(OnboardingViewModel.REST_DAY_STEPS), "From Health Connect", g.steps) { actions.setDayGoals(g.copy(steps = it)) }
        RowDivider()
        DayGoalToggle("Drink 2.5 L water", "Tap to log", g.water) { actions.setDayGoals(g.copy(water = it)) }
    }

    Spacer(Modifier.height(12.dp))
    Card(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Win the week", style = Resurge.type.label)
                Text("Good days you need each week", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
            StepButton("−", enabled = g.weeklyDays > DayGoals.WEEKLY_RANGE.first, onClick = { actions.setDayGoals(g.copy(weeklyDays = g.weeklyDays - 1)) }, contentDescription = "Fewer days", size = 40.dp)
            EditableValue(
                display = "${g.weeklyDays} / 7",
                editText = { "${g.weeklyDays}" },
                onSubmit = { t -> TypedNumbers.parseInt(t)?.let { actions.setDayGoals(g.copy(weeklyDays = it)) } },
                label = "Good days a week",
                style = Resurge.type.headline,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(56.dp),
            )
            StepButton("+", enabled = g.weeklyDays < DayGoals.WEEKLY_RANGE.last, onClick = { actions.setDayGoals(g.copy(weeklyDays = g.weeklyDays + 1)) }, contentDescription = "More days", size = 40.dp)
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Resurge.colors.border, Resurge.shape.md)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                state.healthConnected -> "Health Connect is connected. Runs, steps, sleep and weight come in on their own."
                state.healthAvailability == HealthConnectAvailability.NOT_SUPPORTED -> "Health Connect isn't available on this phone. You can log workouts yourself."
                else -> "Health Connect fills in runs, steps, sleep and weight, so you don't have to."
            },
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (!state.healthConnected && state.healthAvailability != HealthConnectAvailability.NOT_SUPPORTED) {
            Spacer(Modifier.width(12.dp))
            Button(
                text = if (state.healthAvailability == HealthConnectAvailability.AVAILABLE) "Connect" else "Install",
                onClick = actions.connectHealth,
                variant = ButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun DayGoalToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    DayGoalRow(title, subtitle, dim = !checked) {
        Switch(checked = checked, onCheckedChange = onChange, accessibilityLabel = title)
    }
}

@Composable
private fun DayGoalRow(title: String, subtitle: String, dim: Boolean = false, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Resurge.type.label, color = if (dim) Resurge.colors.textMuted else Resurge.colors.text)
            Text(subtitle, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        trailing()
    }
}

@Composable
private fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))

@Composable
private fun ReadyStep(state: OnboardingUiState) {
    val goal = state.majorGoal(LocalDate.now()) ?: return
    val by = goal.endDate.format(DateTimeFormatter.ofPattern("d MMMM"))
    StepHeader(
        title = when (goal.type) {
            TrainingGoal.LOSE_FAT, TrainingGoal.BUILD_MUSCLE -> "${formatWeight(goal.targetKg ?: 0.0, state.units)} by $by"
            TrainingGoal.RUN_FURTHER -> "Run ${MajorGoal.RACES.firstOrNull { it.first == goal.raceKm }?.second ?: "further"} by $by"
            TrainingGoal.GET_IN_SHAPE -> "${goal.weeks} strong weeks by $by"
        },
        subtitle = "Here's how today adds up to that.",
    )

    val items = buildList {
        add("Today's session")
        if (state.dayGoals.meals > 0) add("Log ${state.dayGoals.meals} meals")
        if (state.dayGoals.sleep) add("Sleep 7 h")
        if (state.dayGoals.steps) add("%,d steps".format(OnboardingViewModel.REST_DAY_STEPS))
        if (state.dayGoals.water) add("Drink 2.5 L water")
    }
    LadderCard("EVERY DAY", "+1 to your streak") {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(18.dp).border(1.5.dp, Resurge.colors.borderStrong, Resurge.shape.pill))
                Spacer(Modifier.width(10.dp))
                Text(item, style = Resurge.type.body)
            }
        }
    }
    LadderLink()
    LadderCard("EVERY WEEK", "wins a mini-goal") {
        Text("${state.dayGoals.weeklyDays} good days out of 7", style = Resurge.type.body)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(7) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .border(if (i == 0) 2.dp else 1.dp, if (i == 0) Resurge.colors.accent else Resurge.colors.borderStrong, Resurge.shape.sm),
                )
            }
        }
    }
    LadderLink()
    LadderCard("THE GOAL", MajorGoal.durationLabel(goal.weeks)) {
        Text(
            when (goal.type) {
                TrainingGoal.LOSE_FAT, TrainingGoal.BUILD_MUSCLE ->
                    "${formatWeight(goal.startKg ?: 0.0, state.units)} → ${formatWeight(goal.targetKg ?: 0.0, state.units)}"
                TrainingGoal.RUN_FURTHER -> "Build to ${MajorGoal.RACES.firstOrNull { it.first == goal.raceKm }?.second ?: "your race"}"
                TrainingGoal.GET_IN_SHAPE -> "Show up, week after week"
            },
            style = Resurge.type.headline,
        )
        state.plan?.let {
            Text(it.name, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(14.dp))
        GoalTrack(goal)
    }
}

/**
 * The goal as a row of solid bars, the first in the accent ("you are here"). A week per bar up to
 * 16 weeks; longer goals get a bar per month so the row stays readable.
 */
@Composable
private fun GoalTrack(goal: MajorGoal) {
    val byMonth = goal.weeks > 16
    val count = if (byMonth) Math.round(goal.weeks * 12 / 52.0).toInt().coerceAtLeast(1) else goal.weeks
    Row(horizontalArrangement = Arrangement.spacedBy(if (count > 12) 3.dp else 4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i == 0) Resurge.colors.accent else Resurge.colors.borderStrong),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(if (byMonth) "This month" else "This week", style = Resurge.type.caption, color = Resurge.colors.accent, modifier = Modifier.weight(1f))
        Text(goal.endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "${goal.weeks} weekly mini-goals" + if (byMonth) ", about ${Math.round(goal.weeks / count.toDouble())} in each month" else "",
        style = Resurge.type.caption,
        color = Resurge.colors.textSubtle,
    )
}

@Composable
private fun LadderCard(overline: String, trailing: String, content: @Composable () -> Unit) {
    Card(contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Overline(overline, color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
            Text(trailing, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun LadderLink() {
    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(Resurge.colors.borderStrong))
    }
}

private fun formatWeight(kg: Double, units: UnitSystem): String = when (units) {
    UnitSystem.METRIC -> "%.1f kg".format(kg)
    UnitSystem.IMPERIAL -> "${(kg / WeightMath.KG_PER_LB).roundToInt()} lb"
}

@Composable
private fun PaceSignal.color(): Color = when (this) {
    PaceSignal.UP -> Resurge.colors.up
    PaceSignal.MID -> Resurge.colors.mid
    PaceSignal.DOWN -> Resurge.colors.down
}

private fun TrainingGoal.icon(): Int = when (this) {
    TrainingGoal.LOSE_FAT -> ResurgeIcons.Scale
    TrainingGoal.BUILD_MUSCLE -> ResurgeIcons.Dumbbell
    TrainingGoal.RUN_FURTHER -> ResurgeIcons.Distance
    TrainingGoal.GET_IN_SHAPE -> ResurgeIcons.Heart
}

@Preview
@Composable
private fun OnboardingPreview() {
    ResurgeTheme {
        OnboardingScreen(state = OnboardingUiState(goal = TrainingGoal.LOSE_FAT), actions = OnboardingActions())
    }
}
