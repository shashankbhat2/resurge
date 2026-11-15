package com.resurgeai.feature.food

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.data.session.SessionRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resurgeai.core.data.food.MealAnalysisException
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.MealEstimate
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject

/** A food row being edited; [key] keeps rows stable as items are added or removed. */
data class EditableItem(val key: Int, val item: FoodItem)

data class MealReviewState(
    val mealId: String? = null,
    val loading: Boolean = true,
    /** Progress label while the model works ("Identifying foods…"); null when idle. */
    val analyzing: String? = null,
    val error: String? = null,
    val title: String = "",
    val type: MealType = MealType.forTime(LocalTime.now()),
    val items: List<EditableItem> = emptyList(),
    val question: String? = null,
    val options: List<String> = emptyList(),
    val notes: String? = null,
    val thumbnail: ByteArray? = null,
    /** Photo to show at the top: the full image for a fresh scan, else the stored thumbnail. */
    val preview: ByteArray? = null,
    /** What the user typed, for a text estimate (shown while it's analysed). */
    val description: String? = null,
    val source: MealSource = MealSource.MANUAL,
    val loggedAt: Instant = Instant.now(),
    /** Adding an item by description is in flight. */
    val addingItem: Boolean = false,
    val done: Boolean = false,
    /** Calories already eaten today, not counting this meal (for "your day"). */
    val eatenBefore: Double = 0.0,
    /** Meals already logged today, not counting this one (for the tide's meals segment). */
    val mealsBefore: Int = 0,
    /** Meals the day streak asks for (0 when meals don't count). */
    val mealsGoal: Int = 2,
    val targetKcal: Int = Targets.DEFAULT_CALORIES,
    /** How many of this plate were eaten; every food's grams scale with it. */
    val servings: Int = 1,
    /** The estimate can be re-run with corrections (a fresh scan or description, not a logged meal). */
    val canCorrect: Boolean = false,
    /** The corrections behind the estimate in flight, shown while it's re-read; empty on the first read. */
    val fixes: List<String> = emptyList(),
) {
    val isNew: Boolean get() = mealId == null
    val totals: Nutrition get() = items.fold(Nutrition.Zero) { acc, e -> acc + e.item.nutrition }
    val canLog: Boolean get() = analyzing == null && items.isNotEmpty()
}

/**
 * Review screen for a scanned or typed meal (new) or a logged one (mealId). Nothing is saved until
 * the user taps Log / Save.
 */
@HiltViewModel
class MealReviewViewModel @Inject constructor(
    private val repository: MealsRepository,
    draftStore: MealDraftStore,
    savedState: SavedStateHandle,
    sessionRepository: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MealReviewState())
    val state: StateFlow<MealReviewState> = _state.asStateFlow()

    private val draft: MealDraft? = if (savedState.get<String>("mealId") == null) draftStore.take() else null
    private var estimate: MealEstimate? = null
    private var nextKey = 0
    private var job: Job? = null
    /** What the person said was off, stacked across re-estimates ("smaller portion", "no ghee"). */
    private val corrections = mutableListOf<String>()

    init {
        val mealId = savedState.get<String>("mealId")
        // Today so far (minus this meal when editing) and the calorie target, for the "your day" bar.
        viewModelScope.launch {
            runCatching {
                val today = LocalDate.now()
                repository.meals(today).first().filter { it.date == today && it.id != mealId }
            }.getOrNull()?.let { earlier ->
                _state.update { it.copy(eatenBefore = earlier.sumOf { m -> m.totals.kcal }, mealsBefore = earlier.size) }
            }
        }
        viewModelScope.launch {
            sessionRepository.session.filterIsInstance<SessionState.Ready>().collect { s ->
                _state.update { it.copy(targetKcal = s.profile.targets.calories, mealsGoal = s.profile.dayGoals.meals) }
            }
        }
        if (mealId != null) {
            viewModelScope.launch {
                val meal = runCatching { repository.meal(mealId) }.getOrNull()
                _state.value = if (meal == null) {
                    MealReviewState(loading = false, error = "This meal couldn't be loaded.")
                } else {
                    MealReviewState(
                        mealId = meal.id,
                        loading = false,
                        title = meal.title,
                        type = meal.type,
                        items = meal.items.map { EditableItem(nextKey++, it) },
                        thumbnail = meal.thumbnail,
                        preview = meal.thumbnail,
                        source = meal.source,
                        loggedAt = meal.loggedAt,
                    )
                }
            }
        } else if (draft == null) {
            _state.value = MealReviewState(loading = false, error = "Nothing to review. Go back and scan or type a meal.")
        } else if (draft.product != null) {
            // Found by barcode: the product's own values, nothing to analyse.
            val product = draft.product
            _state.update {
                it.copy(
                    loading = false,
                    title = product.name,
                    items = listOf(EditableItem(nextKey++, product.toItem())),
                    source = MealSource.BARCODE,
                    notes = if (product.servingGrams == null) "Portion set to 100 g. Adjust it to what you ate." else null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    thumbnail = draft.photo?.thumbnail,
                    preview = draft.photo?.jpeg,
                    description = draft.text,
                    source = when {
                        draft.barcode != null -> MealSource.BARCODE
                        draft.photo != null -> MealSource.SCAN
                        else -> MealSource.TEXT
                    },
                    canCorrect = true,
                )
            }
            analyze(answer = null)
        }
    }

    /** Runs (or re-runs with an answer to the model's question) the estimate for the draft. */
    private fun analyze(answer: String?) {
        val d = draft ?: return
        job?.cancel()
        job = viewModelScope.launch {
            val steps = launch { cycleSteps(stepsFor(_state.value)) }
            try {
                val result = repository.analyze(d.photo?.jpeg, describe(d), _state.value.type, answer, d.barcode)
                estimate = result
                _state.update {
                    it.copy(
                        analyzing = null,
                        error = null,
                        title = result.title,
                        items = result.items.map { item -> EditableItem(nextKey++, item.scaled(it.servings.toDouble())) },
                        question = result.question,
                        options = result.options,
                        notes = result.notes,
                    )
                }
            } catch (e: MealAnalysisException) {
                _state.update { it.copy(analyzing = null, error = e.message) }
            } finally {
                steps.cancel()
            }
        }
    }

    /** The description sent to the model: what was typed plus any corrections, within its 500 chars. */
    private fun describe(d: MealDraft): String? {
        if (corrections.isEmpty()) return d.text
        val fixes = "Corrections from the person: ${corrections.joinToString("; ")}."
        return listOfNotNull(d.text?.take(500 - fixes.length - 1), fixes).joinToString("\n").take(500)
    }

    private suspend fun cycleSteps(steps: List<String>) {
        var i = 0
        while (true) {
            _state.update { it.copy(analyzing = steps[i.coerceAtMost(steps.lastIndex)]) }
            delay(1_600)
            i++
        }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        analyze(answer = null)
    }

    fun answer(option: String) {
        _state.update { it.copy(question = null, options = emptyList()) }
        analyze(answer = option)
    }

    /** Re-runs the estimate with what was off ("Smaller portion", "it's brown rice"). */
    fun correct(fixes: List<String>) {
        val clean = fixes.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty() || draft == null) return
        corrections += clean
        _state.update { it.copy(error = null, question = null, options = emptyList(), fixes = clean) }
        analyze(answer = null)
    }

    /** Scales every food to [n] servings of the plate. */
    fun setServings(n: Int) = _state.update { s ->
        val next = n.coerceIn(1, 9)
        val factor = next.toDouble() / s.servings
        s.copy(servings = next, items = s.items.map { it.copy(item = it.item.scaled(factor)) })
    }

    fun dismissQuestion() = _state.update { it.copy(question = null, options = emptyList()) }

    fun setType(type: MealType) = _state.update { it.copy(type = type) }
    fun setTitle(title: String) = _state.update { it.copy(title = title) }

    fun setGrams(key: Int, grams: Double) = _state.update { s ->
        s.copy(items = s.items.map { if (it.key == key) it.copy(item = it.item.copy(grams = grams.coerceIn(1.0, 3000.0))) else it })
    }

    fun removeItem(key: Int) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    /** Adds foods from a short description ("1 banana") using the same estimate. */
    fun addByDescription(text: String) {
        if (text.isBlank() || _state.value.addingItem) return
        viewModelScope.launch {
            _state.update { it.copy(addingItem = true, error = null) }
            try {
                val result = repository.analyze(null, text, _state.value.type)
                _state.update { s -> s.copy(addingItem = false, items = s.items + result.items.map { EditableItem(nextKey++, it) }) }
            } catch (e: MealAnalysisException) {
                _state.update { it.copy(addingItem = false, error = e.message) }
            }
        }
    }

    fun log() {
        val s = _state.value
        if (!s.canLog) return
        repository.save(
            id = s.mealId,
            type = s.type,
            loggedAt = if (s.isNew) Instant.now() else s.loggedAt,
            source = s.source,
            title = s.title,
            items = s.items.map { it.item },
            thumbnail = if (s.isNew) s.thumbnail else null,
            aiEstimate = if (s.isNew) estimate else null,
        )
        _state.update { it.copy(done = true) }
    }

    fun delete() {
        val id = _state.value.mealId ?: return
        repository.delete(id)
        _state.update { it.copy(done = true) }
    }

    companion object {
        val PHOTO_STEPS = listOf("Identifying foods…", "Estimating portions…", "Matching foods to nutrition tables…", "Almost there…")
        val TEXT_STEPS = listOf("Reading your description…", "Matching foods to nutrition tables…", "Almost there…")
        val LABEL_STEPS = listOf("Reading the label…", "Checking the values…", "Almost there…")
        val FIX_STEPS = listOf("Applying your fixes…", "Working out nutrition…", "Almost there…")

        fun stepsFor(s: MealReviewState): List<String> = when {
            s.fixes.isNotEmpty() -> FIX_STEPS
            s.source == MealSource.BARCODE -> LABEL_STEPS
            s.preview != null -> PHOTO_STEPS
            else -> TEXT_STEPS
        }
    }
}

private fun FoodItem.scaled(factor: Double): FoodItem = if (factor == 1.0) this else copy(grams = (grams * factor).coerceIn(1.0, 3000.0))
