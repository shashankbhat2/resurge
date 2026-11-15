package com.resurgeai.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * The one way to log a meal in the app (when Settings › Data › Log meals on this device is on): a
 * meal type, a title and the meal's totals. Also edits or deletes a logged meal. Itemised meals
 * (usually from the AI assistant over MCP) keep their items; only the type and title change here.
 */
internal object ManualMeals {
    const val MAX_KCAL = 10_000.0
    const val MAX_GRAMS = 1_000.0

    /** A non-negative number from a field ("12", "12.5", "12,5"), or null. */
    fun parse(text: CharSequence): Double? = text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

    /** Totals as one 100 g item whose per-100 g values are the totals, as the MCP log_meal tool stores them. */
    fun items(title: String, kcal: Double, protein: Double, carbs: Double, fat: Double): List<FoodItem> =
        listOf(FoodItem(title.trim().ifEmpty { "Meal" }.take(60), 100.0, Nutrition(kcal, protein, carbs, fat)))

    /** Totals can be typed over only when the meal is a single entry; itemised meals keep their items. */
    fun totalsEditable(meal: Meal?): Boolean = meal == null || meal.items.size <= 1

    fun valid(kcal: Double?, vararg grams: Double?): Boolean =
        kcal != null && kcal <= MAX_KCAL && grams.all { it == null || it <= MAX_GRAMS }

    fun number(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(v)
}

data class ManualMealState(val loading: Boolean = false, val meal: Meal? = null, val missing: Boolean = false)

@HiltViewModel
class ManualMealViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val meals: MealsRepository,
) : ViewModel() {
    private val mealId: String? = savedStateHandle["mealId"]
    private val _state = MutableStateFlow(ManualMealState(loading = mealId != null))
    val state: StateFlow<ManualMealState> = _state.asStateFlow()

    init {
        mealId?.let { id ->
            viewModelScope.launch {
                val meal = runCatching { meals.meal(id) }.getOrNull()
                _state.value = ManualMealState(loading = false, meal = meal, missing = meal == null)
            }
        }
    }

    fun save(type: MealType, title: String, kcal: Double, protein: Double, carbs: Double, fat: Double) {
        val meal = _state.value.meal
        if (meal != null && !ManualMeals.totalsEditable(meal)) {
            meals.save(meal.id, type, meal.loggedAt, meal.source, title.ifBlank { meal.title }, meal.items, meal.thumbnail, null)
        } else {
            meals.save(
                id = meal?.id,
                type = type,
                loggedAt = meal?.loggedAt ?: Instant.now(),
                source = meal?.source ?: MealSource.MANUAL,
                title = title,
                items = ManualMeals.items(title, kcal, protein, carbs, fat),
                thumbnail = meal?.thumbnail,
                aiEstimate = null,
            )
        }
    }

    fun delete() {
        _state.value.meal?.let { meals.delete(it.id) }
    }
}

@Composable
fun ManualMealRoute(onDone: () -> Unit, viewModel: ManualMealViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ManualMealScreen(
        state = state,
        onBack = onDone,
        onSave = { type, title, kcal, p, c, f -> viewModel.save(type, title, kcal, p, c, f); onDone() },
        onDelete = { viewModel.delete(); onDone() },
    )
}

@Composable
internal fun ManualMealScreen(
    state: ManualMealState,
    onBack: () -> Unit,
    onSave: (MealType, String, Double, Double, Double, Double) -> Unit,
    onDelete: () -> Unit,
) {
    val meal = state.meal
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        TopBar(if (meal != null) "Edit meal" else "Log a meal", onBack = onBack)
        when {
            state.loading -> Spinner(Modifier.padding(24.dp))
            state.missing -> Text("This meal couldn't be loaded.", style = Resurge.type.body, color = Resurge.colors.textMuted, modifier = Modifier.padding(16.dp))
            else -> Form(meal, onSave, onDelete)
        }
    }
}

@Composable
private fun Form(meal: Meal?, onSave: (MealType, String, Double, Double, Double, Double) -> Unit, onDelete: () -> Unit) {
    val totals = meal?.totals ?: Nutrition.Zero
    var type by rememberSaveable { mutableStateOf(meal?.type ?: MealType.forTime(LocalTime.now(ZoneId.systemDefault()))) }
    val title = remember { TextFieldState(meal?.title.orEmpty()) }
    fun field(v: Double) = TextFieldState(if (meal == null) "" else ManualMeals.number(v))
    val kcal = remember { field(totals.kcal) }
    val protein = remember { field(totals.protein) }
    val carbs = remember { field(totals.carbs) }
    val fat = remember { field(totals.fat) }
    val editable = ManualMeals.totalsEditable(meal)
    val k = ManualMeals.parse(kcal.text)
    val (p, c, f) = Triple(ManualMeals.parse(protein.text), ManualMeals.parse(carbs.text), ManualMeals.parse(fat.text))
    val canSave = !editable || ManualMeals.valid(k, p, c, f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline("Meal")
        SegmentedControl(
            options = MealType.entries.map { SegmentOption(it, if (it == MealType.SNACK) "Snack" else it.label) },
            selected = type,
            onSelect = { type = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Overline("Title")
        InputField(title, placeholder = "Dal, rice and salad", maxLength = 80)
        Spacer(Modifier.height(6.dp))
        if (editable) {
            Overline("Totals for the meal")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Kcal", kcal, Modifier.weight(1f))
                NumberField("Protein g", protein, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Carbs g", carbs, Modifier.weight(1f))
                NumberField("Fat g", fat, Modifier.weight(1f))
            }
            Text(
                "Your AI assistant can log meals item by item through MCP; here it's one line per meal.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
            )
        } else {
            Overline("Items")
            meal!!.items.forEach { item ->
                Row {
                    Text(item.name, style = Resurge.type.label, modifier = Modifier.weight(1f))
                    Text("${item.grams.roundToInt()} G · ${kcal(item.nutrition.kcal).uppercase()}", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
            }
            Text(
                "Logged item by item, so only the meal and title change here. Ask your assistant to change the items.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            if (meal != null) "Save changes" else "Log meal",
            onClick = { onSave(type, title.text.toString(), k ?: 0.0, p ?: 0.0, c ?: 0.0, f ?: 0.0) },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        if (meal != null) Button("Delete meal", onClick = onDelete, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NumberField(label: String, state: TextFieldState, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        InputField(state, placeholder = "0", maxLength = 7, keyboardType = KeyboardType.Decimal)
    }
}
