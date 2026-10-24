package com.resurgeai.feature.auth.onboarding

import com.resurgeai.core.model.PlanNames
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.plan.PlanGenerationException
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.DayGoals
import com.resurgeai.core.model.Experience
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.RunMode
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.TrainingLocation
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.model.WorkoutStyle
import com.resurgeai.core.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class OnboardingStep { Goal, Target, Schedule, Plan, Day, Ready }

/** What a plan was built from; a new plan is needed when any of it changes. */
data class PlanInputs(
    val goal: TrainingGoal,
    val weeks: Int,
    val startKg: Double?,
    val targetKg: Double?,
    val raceKm: Double?,
    val training: TrainingPrefs,
)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Goal,
    val goal: TrainingGoal? = null,
    val weeks: Int = 12,
    /** The duration is set with the custom stepper rather than a quick pick. */
    val customWeeks: Boolean = false,
    /** Weight now and at the goal, in kg. Filled from the latest weigh-in when there is one. */
    val startKg: Double = DEFAULT_START_KG,
    val targetKg: Double = DEFAULT_START_KG,
    val raceKm: Double = MajorGoal.RACES.first().first,
    val training: TrainingPrefs = TrainingPrefs(),
    val units: UnitSystem = UnitSystem.METRIC,
    val plan: WorkoutPlan? = null,
    val planFor: PlanInputs? = null,
    val planLoading: Boolean = false,
    val planError: String? = null,
    val dayGoals: DayGoals = DayGoals(steps = false, meals = 0),
    val saving: Boolean = false,
    val error: String? = null,
    val healthAvailability: HealthConnectAvailability? = null,
    val healthConnected: Boolean = false,
) {
    val inputs: PlanInputs?
        get() = goal?.let {
            PlanInputs(
                goal = it,
                weeks = weeks,
                startKg = startKg.takeIf { _ -> it.byWeight },
                targetKg = targetKg.takeIf { _ -> it.byWeight },
                raceKm = raceKm.takeIf { _ -> it == TrainingGoal.RUN_FURTHER },
                training = training,
            )
        }

    fun majorGoal(today: LocalDate): MajorGoal? = inputs?.let {
        MajorGoal(it.goal, it.weeks, today, it.startKg, it.targetKg, it.raceKm)
    }

    /** The target moves in the goal's direction: down to lose fat, up to build muscle. */
    val targetValid: Boolean
        get() = when (goal) {
            TrainingGoal.LOSE_FAT -> targetKg < startKg
            TrainingGoal.BUILD_MUSCLE -> targetKg > startKg
            else -> true
        }

    val planStale: Boolean get() = plan == null || planFor != inputs

    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.Goal -> goal != null
            OnboardingStep.Target -> targetValid
            OnboardingStep.Schedule -> training.validDays
            OnboardingStep.Plan -> plan != null && !planLoading
            OnboardingStep.Day, OnboardingStep.Ready -> !saving
        }

    /** One step of the weight steppers: a tenth of a kilo, or a pound (weights show in whole pounds). */
    val weightStepKg: Double get() = if (units == UnitSystem.IMPERIAL) WeightMath.KG_PER_LB else 0.1

    companion object {
        const val DEFAULT_START_KG = 75.0
        val WEIGHT_RANGE_KG = 35.0..250.0
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val planRepository: PlanRepository,
    private val weightRepository: WeightRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    val healthPermissionContract = healthConnect.permissionContract()
    val healthPermissionsToRequest: Set<String> get() = healthConnect.permissionsToRequest()

    private var planJob: Job? = null

    init {
        refreshHealthStatus()
        viewModelScope.launch {
            val latest = runCatching { weightRepository.latestKg() }.getOrNull() ?: return@launch
            val start = roundKg(latest)
            _state.update { it.copy(startKg = start, targetKg = defaultTarget(it.goal, start)) }
        }
    }

    fun installHealthConnectIntent() = healthConnect.installIntent()

    fun refreshHealthStatus() {
        viewModelScope.launch {
            val availability = healthConnect.availability()
            val connected = availability == HealthConnectAvailability.AVAILABLE &&
                healthConnect.grantedPermissions().any { it in HealthPermissions.READ }
            _state.update { it.copy(healthAvailability = availability, healthConnected = connected) }
        }
    }

    fun setUnits(units: UnitSystem) = _state.update { it.copy(units = units) }

    fun selectGoal(goal: TrainingGoal) = _state.update {
        if (it.goal == goal) it
        else it.copy(
            goal = goal,
            targetKg = defaultTarget(goal, it.startKg),
            // Logging meals is suggested when the goal is about weight; it stays a choice.
            dayGoals = it.dayGoals.copy(meals = if (goal.byWeight) 2 else 0),
            // A run goal always runs on its own days.
            training = if (goal == TrainingGoal.RUN_FURTHER) it.training.copy(runs = RunMode.SEPARATE) else it.training,
        )
    }

    /** A quick pick (8, 12, 16 weeks). */
    fun setWeeks(weeks: Int) = _state.update { it.copy(weeks = weeks.coerceIn(MajorGoal.WEEKS_RANGE), customWeeks = false) }

    /** Switches to the custom duration stepper, starting from the current length. */
    fun useCustomWeeks() = _state.update { it.copy(customWeeks = true) }

    /** Custom duration: a week at a time up to 12 weeks, then a month at a time. */
    fun stepWeeks(steps: Int) = _state.update { it.copy(weeks = MajorGoal.stepDuration(it.weeks, steps), customWeeks = true) }

    fun stepStart(steps: Int) = _state.update {
        val start = roundKg((it.startKg + steps * it.weightStepKg).coerceIn(OnboardingUiState.WEIGHT_RANGE_KG))
        it.copy(startKg = start)
    }

    /** A typed weight in kg (converted from pounds by the screen). */
    fun setStartKg(kg: Double) = _state.update { it.copy(startKg = roundKg(kg.coerceIn(OnboardingUiState.WEIGHT_RANGE_KG))) }

    fun setTargetKg(kg: Double) = _state.update { it.copy(targetKg = roundKg(kg.coerceIn(OnboardingUiState.WEIGHT_RANGE_KG))) }

    /** A typed custom duration in weeks; it needn't sit on the stepper's notches. */
    fun setCustomWeeks(weeks: Int) = _state.update { it.copy(weeks = weeks.coerceIn(MajorGoal.WEEKS_RANGE), customWeeks = true) }

    fun stepTarget(steps: Int) = _state.update {
        it.copy(targetKg = roundKg((it.targetKg + steps * it.weightStepKg).coerceIn(OnboardingUiState.WEIGHT_RANGE_KG)))
    }

    fun setRace(km: Double) = _state.update { it.copy(raceKm = km) }

    fun toggleDay(day: DayOfWeek) = _state.update {
        val days = it.training.days
        val next = if (day in days) days - day else days + day
        // Seven days leaves no rest; the plan service accepts two to six.
        if (next.size > TrainingPrefs.DAYS_RANGE.last) it else it.copy(training = it.training.copy(days = next))
    }

    fun setLocation(location: TrainingLocation) = _state.update { it.copy(training = it.training.copy(location = location)) }

    fun setSessionMin(minutes: Int) = _state.update { it.copy(training = it.training.copy(sessionMin = minutes)) }

    fun setExperience(experience: Experience) = _state.update { it.copy(training = it.training.copy(experience = experience)) }

    fun setStyle(style: WorkoutStyle) = _state.update { it.copy(training = it.training.copy(style = style)) }

    fun setRuns(runs: RunMode) = _state.update { it.copy(training = it.training.copy(runs = runs)) }

    fun setDayGoals(goals: DayGoals) = _state.update {
        it.copy(dayGoals = goals.copy(weeklyDays = goals.weeklyDays.coerceIn(DayGoals.WEEKLY_RANGE)))
    }

    /** Builds the plan for the current answers (or rebuilds it with [change] applied). */
    fun buildPlan(change: String? = null) {
        val current = _state.value
        val inputs = current.inputs ?: return
        val goal = current.majorGoal(LocalDate.now()) ?: return
        planJob?.cancel()
        _state.update { it.copy(planLoading = true, planError = null) }
        planJob = viewModelScope.launch {
            try {
                val plan = planRepository.generate(
                    goal = goal,
                    training = inputs.training,
                    units = current.units,
                    change = change,
                    previousPlanId = current.plan?.id.takeIf { change != null },
                )
                _state.update { it.copy(plan = plan.copy(name = PlanNames.of(goal, inputs.training)), planFor = inputs, planLoading = false) }
            } catch (e: PlanGenerationException) {
                _state.update { it.copy(planLoading = false, planError = e.message) }
            }
        }
    }

    fun back() = _state.update {
        val previous = OnboardingStep.entries.getOrNull(it.step.ordinal - 1) ?: return@update it
        it.copy(step = previous, error = null)
    }

    fun next(uid: String) {
        val current = _state.value
        if (!current.canContinue) return
        val nextStep = OnboardingStep.entries.getOrNull(current.step.ordinal + 1)
        if (nextStep == null) {
            finish(uid)
            return
        }
        _state.update { it.copy(step = nextStep) }
        if (nextStep == OnboardingStep.Plan && current.planStale && !current.planLoading) buildPlan()
    }

    private fun finish(uid: String) {
        val current = _state.value
        val goal = current.majorGoal(LocalDate.now()) ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                userRepository.completeOnboarding(
                    uid = uid,
                    goal = goal,
                    training = current.training,
                    planId = current.plan?.id,
                    targets = Targets(steps = REST_DAY_STEPS),
                    dayGoals = current.dayGoals,
                )
            }.onFailure { e ->
                _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save. Try again.") }
            }
            // On success the session flips to Ready and the app leaves onboarding.
        }
    }

    private fun defaultTarget(goal: TrainingGoal?, startKg: Double): Double = roundKg(
        when (goal) {
            TrainingGoal.LOSE_FAT -> startKg - 6.0
            TrainingGoal.BUILD_MUSCLE -> startKg + 3.0
            else -> startKg
        }.coerceIn(OnboardingUiState.WEIGHT_RANGE_KG),
    )

    /** Hundredths, so pound steps (0.4536 kg) don't drift. */
    private fun roundKg(kg: Double): Double = (kg * 100).roundToInt() / 100.0

    companion object {
        /** The walk rest days ask for. */
        const val REST_DAY_STEPS = 8_000
    }
}
