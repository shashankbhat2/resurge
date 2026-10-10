package com.resurgeai.feature.auth.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.healthconnect.HealthConnectAvailability
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.Goal
import com.resurgeai.core.model.Targets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep { Goals, Targets, Health, Ready }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Goals,
    val goals: Set<Goal> = emptySet(),
    val targets: Targets = Targets(),
    val saving: Boolean = false,
    val error: String? = null,
    val healthAvailability: HealthConnectAvailability? = null,
    val healthConnected: Boolean = false,
) {
    val canContinue: Boolean
        get() = when (step) {
            OnboardingStep.Goals -> goals.isNotEmpty()
            OnboardingStep.Targets, OnboardingStep.Health, OnboardingStep.Ready -> !saving
        }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    val healthPermissionContract = healthConnect.permissionContract()
    val healthPermissionsToRequest: Set<String> get() = healthConnect.permissionsToRequest()

    init { refreshHealthStatus() }

    fun installHealthConnectIntent() = healthConnect.installIntent()

    fun refreshHealthStatus() {
        viewModelScope.launch {
            val availability = healthConnect.availability()
            val connected = availability == HealthConnectAvailability.AVAILABLE &&
                healthConnect.grantedPermissions().any { it in HealthPermissions.READ }
            _state.update { it.copy(healthAvailability = availability, healthConnected = connected) }
        }
    }

    fun toggleGoal(goal: Goal) = _state.update {
        it.copy(goals = if (goal in it.goals) it.goals - goal else it.goals + goal)
    }

    fun setCalories(value: Int) = _state.update { it.copy(targets = it.targets.copy(calories = value)) }

    fun setWater(value: Int) = _state.update { it.copy(targets = it.targets.copy(waterMl = value)) }

    fun back() = _state.update {
        val previous = OnboardingStep.entries.getOrNull(it.step.ordinal - 1) ?: return@update it
        it.copy(step = previous, error = null)
    }

    fun next(uid: String) {
        val current = _state.value
        if (!current.canContinue) return
        val nextStep = OnboardingStep.entries.getOrNull(current.step.ordinal + 1)
        if (nextStep != null) {
            _state.update { it.copy(step = nextStep) }
        } else {
            finish(uid)
        }
    }

    private fun finish(uid: String) {
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val current = _state.value
            runCatching { userRepository.completeOnboarding(uid, current.goals, current.targets) }
                .onFailure { e ->
                    _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save. Try again.") }
                }
            // On success the session flips to Ready and the app leaves onboarding.
        }
    }
}
