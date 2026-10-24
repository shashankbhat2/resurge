package com.resurgeai.feature.fitness

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.plan.PlanGenerationException
import com.resurgeai.core.data.plan.PlanRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.data.user.UserRepository
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.TrainingPrefs
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WorkoutPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val loaded: Boolean = false,
    val uid: String? = null,
    val goal: MajorGoal? = null,
    val training: TrainingPrefs = TrainingPrefs(),
    val units: UnitSystem = UnitSystem.METRIC,
    val plan: WorkoutPlan? = null,
    /** A rebuilt plan waiting to be accepted, and the schedule it was built for. */
    val draft: WorkoutPlan? = null,
    val draftTraining: TrainingPrefs? = null,
    val building: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
)

/**
 * The plan screen: the plan in full, and rebuilding it with a change ("swap Friday for cycling") or
 * a new schedule. A rebuild is a draft until it's accepted, so the current plan stays in use.
 */
@HiltViewModel
class PlanViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val plans: PlanRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile }.distinctUntilChanged().collect { p ->
                val plan = p.activePlanId?.let { runCatching { plans.plan(it) }.getOrNull() }
                _state.update {
                    it.copy(
                        loaded = true,
                        uid = p.uid,
                        goal = p.majorGoal,
                        training = p.training ?: TrainingPrefs(),
                        units = p.units,
                        plan = plan,
                    )
                }
            }
        }
    }

    /** Rebuilds the plan with [change] applied to the current one, or for a new [training] schedule. */
    fun rebuild(change: String? = null, training: TrainingPrefs = _state.value.training) {
        val s = _state.value
        val goal = s.goal ?: return
        if (!training.validDays) return
        job?.cancel()
        _state.update { it.copy(building = true, error = null) }
        job = viewModelScope.launch {
            try {
                val plan = plans.generate(
                    goal = goal,
                    training = training,
                    units = s.units,
                    change = change?.takeIf { it.isNotBlank() },
                    previousPlanId = s.plan?.id.takeIf { !change.isNullOrBlank() },
                )
                _state.update { it.copy(building = false, draft = plan, draftTraining = training) }
            } catch (e: PlanGenerationException) {
                _state.update { it.copy(building = false, error = e.message) }
            }
        }
    }

    fun accept() {
        val s = _state.value
        val uid = s.uid ?: return
        val draft = s.draft ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { users.setActivePlan(uid, draft.id, s.draftTraining ?: s.training) }
                .onSuccess { _state.update { it.copy(saving = false, plan = draft, training = s.draftTraining ?: s.training, draft = null, draftTraining = null) } }
                .onFailure { _state.update { it.copy(saving = false, error = "Couldn't switch plans. Try again.") } }
        }
    }

    fun discard() = _state.update { it.copy(draft = null, draftTraining = null, error = null) }
}
