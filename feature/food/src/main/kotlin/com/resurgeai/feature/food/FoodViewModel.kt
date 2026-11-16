package com.resurgeai.feature.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.data.prefs.PreferencesRepository
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
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One day of the feed. */
data class FoodDay(val date: LocalDate, val meals: List<Meal>) {
    val totals: Nutrition get() = meals.fold(Nutrition.Zero) { acc, m -> acc + m.totals }
}

data class FoodUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val todayTotals: Nutrition = Nutrition.Zero,
    val todayMealCount: Int = 0,
    val targets: Targets = Targets(),
    /** Meals the day streak asks for (0 when meals don't count). */
    val mealsGoal: Int = 2,
    val macroTargets: MacroTargets = MacroTargets.from(Targets.DEFAULT_CALORIES),
    val waterMl: Int = 0,
    val days: List<FoodDay> = emptyList(),
    /** Last 7 days, unfiltered, for the glycaemic card. */
    val weekDays: List<FoodDay> = emptyList(),
    val deleted: Meal? = null,
    /** Settings › Data › Log meals on this device. Off hides snap / scan / type; meals stay editable. */
    val logOnDevice: Boolean = true,
    val filters: FoodFeed.Filters = FoodFeed.Filters(),
    val rangeStart: LocalDate = LocalDate.now().minusDays(6),
    val rangeEnd: LocalDate = LocalDate.now(),
    val hasAnyMeals: Boolean = false,
)

private data class FoodSources(
    val meals: List<Meal>,
    val waterByDay: Map<LocalDate, Int>,
    val targets: Targets,
    val mealsGoal: Int,
    val deleted: Meal?,
    val logOnDevice: Boolean,
)

/** Food tab: today's fuel, then a filterable meal log. */
@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: MealsRepository,
    sessionRepository: SessionRepository,
    preferences: PreferencesRepository,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val since = LocalDate.now(zone).minusDays(FoodFeed.HISTORY_DAYS)
    private val deleted = MutableStateFlow<Meal?>(null)
    private val filters = MutableStateFlow(FoodFeed.Filters())

    private val targets = sessionRepository.session.filterIsInstance<SessionState.Ready>().map { it.profile.targets to it.profile.dayGoals.meals }
    private val meals = repository.meals(since).catch { emit(emptyList()) }
    private val water = repository.water(LocalDate.now(zone)).catch { emit(emptyMap()) }

    private val sources = combine(meals, water, targets, deleted, preferences.logMealsOnDevice) { list, waterByDay, pair, del, onDevice ->
        FoodSources(list, waterByDay, pair.first, pair.second, del, onDevice)
    }

    val state: StateFlow<FoodUiState> = combine(sources, filters) { src, f ->
        val today = LocalDate.now(zone)
        val (from, to) = FoodFeed.bounds(today, f)
        val weekStart = today.minusDays(6)
        val weekDays = src.meals.filter { it.date in weekStart..today }
            .groupBy { it.date }
            .map { (date, dayMeals) -> FoodDay(date, dayMeals.sortedBy { it.loggedAt }) }
        val todayMeals = src.meals.filter { it.date == today }
        FoodUiState(
            loading = false,
            today = today,
            todayTotals = todayMeals.fold(Nutrition.Zero) { acc, m -> acc + m.totals },
            todayMealCount = todayMeals.size,
            targets = src.targets,
            mealsGoal = src.mealsGoal,
            macroTargets = MacroTargets.from(src.targets.calories),
            waterMl = src.waterByDay[today] ?: 0,
            days = FoodFeed.days(src.meals, today, f),
            weekDays = weekDays,
            deleted = src.deleted,
            logOnDevice = src.logOnDevice,
            filters = f,
            rangeStart = from,
            rangeEnd = to,
            hasAnyMeals = src.meals.isNotEmpty(),
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

    fun setRange(range: FoodFeed.Range) {
        filters.update { current ->
            if (range == FoodFeed.Range.CUSTOM && current.customFrom == null) {
                val today = LocalDate.now(zone)
                current.copy(range = range, customFrom = today.minusDays(6), customTo = today)
            } else {
                current.copy(range = range)
            }
        }
    }

    fun pickCustomDate(date: LocalDate) {
        filters.update { current ->
            val start = current.customFrom
            val end = current.customTo
            if (current.range != FoodFeed.Range.CUSTOM || start == null || start != end) {
                current.copy(range = FoodFeed.Range.CUSTOM, customFrom = date, customTo = date)
            } else {
                current.copy(range = FoodFeed.Range.CUSTOM, customFrom = minOf(start, date), customTo = maxOf(start, date))
            }
        }
    }

    fun toggleType(type: MealType) {
        filters.update { it.copy(types = FoodFeed.toggleType(it.types, type)) }
    }

    fun clearTypes() {
        filters.update { it.copy(types = emptySet()) }
    }

    fun setSort(sort: FoodFeed.Sort) {
        filters.update { it.copy(sort = sort) }
    }

    fun setQuery(query: String) {
        filters.update { it.copy(query = query) }
    }
}
