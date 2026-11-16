package com.resurgeai.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.model.MacroTargets
import com.resurgeai.core.model.Meal
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

/** One day of meals. */
data class FoodDay(val date: LocalDate, val meals: List<Meal>) {
    val totals: Nutrition get() = meals.fold(Nutrition.Zero) { acc, m -> acc + m.totals }
}

data class FoodUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    /** The day the page shows; the calendar keeps it in the middle. */
    val selected: LocalDate = today,
    val calendar: List<FoodFeed.CalendarDay> = emptyList(),
    /** The selected day's meals, oldest first. */
    val day: FoodDay = FoodDay(selected, emptyList()),
    val targets: Targets = Targets(),
    /** Meals the day streak asks for (0 when meals don't count). */
    val mealsGoal: Int = 2,
    val macroTargets: MacroTargets = MacroTargets.from(Targets.DEFAULT_CALORIES),
    /** Water on the selected day. */
    val waterMl: Int = 0,
    /** The 7 days ending on the selected day, for the glycaemic card. */
    val weekDays: List<FoodDay> = emptyList(),
    val deleted: Meal? = null,
    /** Settings › Data › Log meals on this device. Off hides snap / scan / type; meals stay editable. */
    val logOnDevice: Boolean = true,
    val hasAnyMeals: Boolean = false,
) {
    val isToday: Boolean get() = selected == today
}

private data class FoodSources(
    val meals: List<Meal>,
    val waterByDay: Map<LocalDate, Int>,
    val targets: Targets,
    val mealsGoal: Int,
    val deleted: Meal?,
    val logOnDevice: Boolean,
)

/** Food tab: a scrollable day calendar, then the picked day's fuel and meals. */
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: MealsRepository,
    sessionRepository: SessionRepository,
    preferences: PreferencesRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(FoodFeed.HISTORY_DAYS)
    private val deleted = MutableStateFlow<Meal?>(null)

    /** Null follows today, so the tab rolls over at midnight. */
    private val picked = MutableStateFlow<LocalDate?>(null)

    private val targets = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.targets to it.profile.dayGoals.meals }
    private val meals = repository.meals(since).catch { emit(emptyList()) }
    private val water = repository.water(since).catch { emit(emptyMap()) }

    private val sources = combine(meals, water, targets, deleted, preferences.logMealsOnDevice) { list, waterByDay, pair, del, onDevice ->
        FoodSources(list, waterByDay, pair.first, pair.second, del, onDevice)
    }

    val state: StateFlow<FoodUiState> = combine(sources, picked) { src, pick ->
        val today = LocalDate.now(zone)
        val selected = pick?.let { FoodFeed.clamp(it, today, since) } ?: today
        FoodUiState(
            loading = false,
            today = today,
            selected = selected,
            calendar = FoodFeed.calendar(src.meals, today, since, src.targets.calories),
            day = FoodFeed.day(src.meals, selected),
            targets = src.targets,
            mealsGoal = src.mealsGoal,
            macroTargets = MacroTargets.from(src.targets.calories),
            waterMl = src.waterByDay[selected] ?: 0,
            weekDays = FoodFeed.week(src.meals, selected),
            deleted = src.deleted,
            logOnDevice = src.logOnDevice,
            hasAnyMeals = src.meals.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodUiState())

    fun select(date: LocalDate) {
        val today = LocalDate.now(zone)
        picked.value = FoodFeed.clamp(date, today, since).takeIf { it != today }
    }

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
}
