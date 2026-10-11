package com.resurgeai.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.MacroTargets
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.Targets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One day of the feed: meals grouped by slot, in breakfast → snacks order. */
data class FoodDay(val date: LocalDate, val slots: List<Pair<MealType, List<Meal>>>) {
    val totals: Nutrition get() = slots.flatMap { it.second }.fold(Nutrition.Zero) { acc, m -> acc + m.totals }
}

data class FoodUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val todayTotals: Nutrition = Nutrition.Zero,
    val targets: Targets = Targets(),
    val macroTargets: MacroTargets = MacroTargets.from(Targets.DEFAULT_CALORIES),
    val waterMl: Int = 0,
    val days: List<FoodDay> = emptyList(),
    val deleted: Meal? = null,
)

/** Food tab: today's calories, macros and water, then the last week of meals. */
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: MealsRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(DAYS)
    private val deleted = MutableStateFlow<Meal?>(null)

    private val targets = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.targets }
    private val meals = repository.meals(since).catch { emit(emptyList()) }
    private val water = repository.water(LocalDate.now(zone)).catch { emit(emptyMap()) }

    val state: StateFlow<FoodUiState> = combine(meals, water, targets, deleted) { list, waterByDay, t, del ->
        val today = LocalDate.now(zone)
        val days = list.groupBy { it.date }.map { (date, dayMeals) ->
            FoodDay(
                date = date,
                slots = MealType.entries.mapNotNull { type ->
                    dayMeals.filter { it.type == type }.sortedBy { it.loggedAt }.takeIf { it.isNotEmpty() }?.let { type to it }
                },
            )
        }.sortedByDescending { it.date }
        FoodUiState(
            loading = false,
            today = today,
            todayTotals = days.firstOrNull { it.date == today }?.totals ?: Nutrition.Zero,
            targets = t,
            macroTargets = MacroTargets.from(t.calories),
            waterMl = waterByDay[today] ?: 0,
            days = days,
            deleted = del,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodUiState())

    fun addWater(ml: Int) = repository.addWater(LocalDate.now(zone), ml)

    fun delete(meal: Meal) {
        repository.delete(meal.id)
        deleted.value = meal
    }

    fun undoDelete() {
        deleted.value?.let(repository::restore)
        deleted.value = null
    }

    fun clearDeleted() {
        deleted.value = null
    }

    companion object {
        const val DAYS = 6L
    }
}
