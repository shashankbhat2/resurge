package com.resurgeai.feature.food

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.CoachMascot
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SoftNote
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.MealType
import kotlin.math.roundToInt

@Composable
fun MealReviewRoute(onDone: () -> Unit, viewModel: MealReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    MealReviewScreen(state, viewModel, onBack = onDone)
}

/** Meals that fill the tide's meals segment (TideMath.MEALS_GOAL on Today). */
private const val TIDE_MEALS = 2

/**
 * The scan / estimate result. While the model works: the photo (or your description) with a
 * light sweeping across it and the steps ticking off. Then: the meal as a hero, its macro split,
 * what it does to your day, the coach's question if it has one, editable foods, and Log.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealReviewScreen(state: MealReviewState, vm: MealReviewViewModel, onBack: () -> Unit) {
    val ready = state.analyzing == null && state.items.isNotEmpty()
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (state.isNew) "Review meal" else "Edit meal", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.analyzing != null) Analyzing(state)

            state.error?.let { message ->
                SoftNote(message)
                if (state.isNew && state.items.isEmpty()) Button("Try again", onClick = vm::retry, variant = ButtonVariant.Secondary)
            }

            if (ready) {
                MealHero(state)
                state.question?.let { question -> QuestionCard(question, state.options, onAnswer = vm::answer, onSkip = vm::dismissQuestion) }
                MacroCard(state)
                DayCard(state)

                SectionLabel("Meal", Modifier.padding(top = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { type -> ChoiceChip(type.label, selected = type == state.type) { vm.setType(type) } }
                }

                SectionLabel("Foods", Modifier.padding(top = 4.dp), trailing = "${state.items.size} ${if (state.items.size == 1) "item" else "items"} · adjust portions")
                FoodGroup {
                    state.items.forEachIndexed { i, e ->
                        if (i > 0) GroupDivider()
                        key(e.key) { ItemRow(e, vm) }
                    }
                    GroupDivider(inset = 0.dp)
                    AddItemRow(state.addingItem, vm::addByDescription)
                }
                state.notes?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 4.dp)) }
            } else if (!state.isNew && state.analyzing == null && state.error == null && !state.loading) {
                FoodGroup { AddItemRow(state.addingItem, vm::addByDescription) }
            }
            if (!state.isNew && !state.loading) {
                Spacer(Modifier.height(8.dp))
                Button("Delete meal", onClick = vm::delete, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
        }
        if (ready) {
            // Docked, so logging is one tap away however many foods there are.
            Box(Modifier.fillMaxWidth().background(Resurge.colors.bg).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    text = if (state.isNew) "Log to ${state.type.label} · ${kcal(state.totals.kcal)}" else "Save",
                    onClick = vm::log,
                    enabled = state.canLog,
                    variant = ButtonVariant.Accent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// --- Analyzing ---

@Composable
private fun Analyzing(state: MealReviewState) {
    val steps = if (state.preview != null) MealReviewViewModel.PHOTO_STEPS else MealReviewViewModel.TEXT_STEPS
    val current = steps.indexOf(state.analyzing).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
            if (state.preview != null) {
                JpegImage(state.preview, "Meal photo", Modifier.fillMaxSize(), shape = Resurge.shape.lg)
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Overline("You wrote")
                    Spacer(Modifier.height(8.dp))
                    Text("“${state.description.orEmpty()}”", style = Resurge.type.brand.copy(fontSize = 22.sp, lineHeight = 28.sp), maxLines = 5, overflow = TextOverflow.Ellipsis)
                }
            }
            ScanSweep(Modifier.matchParentSize(), dim = state.preview != null)
        }
        FoodGroup {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.dropLast(1).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                            when {
                                i < current -> Icon(ResurgeIcons.Check, contentDescription = "Done", tint = Resurge.colors.brandInk, size = 18.dp)
                                i == current -> Spinner(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else -> Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(Resurge.colors.ebb))
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            step.removeSuffix("…"),
                            style = Resurge.type.label,
                            color = if (i <= current) Resurge.colors.text else Resurge.colors.textSubtle,
                        )
                    }
                }
            }
        }
    }
}

/** A soft band of light sweeping down and back while it's being read; [dim] darkens a photo under it. */
@Composable
private fun ScanSweep(modifier: Modifier, dim: Boolean) {
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val glow = Resurge.colors.foam
    Canvas(modifier) {
        if (dim) drawRect(Color.Black.copy(alpha = 0.18f))
        val y = size.height * sweep
        val band = size.height * 0.28f
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.5f to glow.copy(alpha = 0.45f), 1f to Color.Transparent,
                startY = y - band / 2, endY = y + band / 2,
            ),
            topLeft = Offset(0f, y - band / 2),
            size = Size(size.width, band),
        )
        drawRect(glow, topLeft = Offset(0f, y - 1.dp.toPx()), size = Size(size.width, 2.dp.toPx()))
    }
}

// --- Result ---

/** The meal itself: its photo with the name and calories set on it (or the same on a card). */
@Composable
private fun MealHero(state: MealReviewState) {
    val title = state.title.ifBlank { "Meal" }
    if (state.preview != null) {
        Box(Modifier.fillMaxWidth().height(240.dp).clip(Resurge.shape.lg)) {
            JpegImage(state.preview, "Meal photo", Modifier.fillMaxSize(), shape = Resurge.shape.lg)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))
            HeroText(title, state.totals.kcal, onPhoto = true, modifier = Modifier.align(Alignment.BottomStart).padding(20.dp))
        }
    } else {
        Box(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp)) {
            HeroText(title, state.totals.kcal, onPhoto = false)
        }
    }
}

@Composable
private fun HeroText(title: String, kcal: Double, onPhoto: Boolean, modifier: Modifier = Modifier) {
    val ink = if (onPhoto) Color.White else Resurge.colors.text
    Column(modifier) {
        Text(title, style = Resurge.type.headline, color = ink.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kcalNumber(kcal), style = Resurge.type.tide.copy(fontSize = 48.sp, lineHeight = 50.sp), color = ink)
            Spacer(Modifier.width(6.dp))
            Text("kcal", style = Resurge.type.label, color = ink.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

/** Where the calories come from: a donut of protein, carbs and fat, with grams and shares. */
@Composable
private fun MacroCard(state: MealReviewState) {
    val t = state.totals
    val shares = MacroShares.of(t)
    FoodGroup {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Ring(
                segments = listOf(
                    RingSegment(shares.protein, MacroVisuals.protein()),
                    RingSegment(shares.carbs, MacroVisuals.carbs()),
                    RingSegment(shares.fat, MacroVisuals.fat()),
                ),
                modifier = Modifier.size(96.dp),
                strokeWidth = 12.dp,
                gapDegrees = 6f,
            ) {
                Text("Macros", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MacroLegendRow("Protein", t.protein, shares.protein, MacroVisuals.protein())
                MacroLegendRow("Carbs", t.carbs, shares.carbs, MacroVisuals.carbs())
                MacroLegendRow("Fat", t.fat, shares.fat, MacroVisuals.fat())
            }
        }
    }
}

/**
 * What this meal does to today: a bar of what's already eaten, this meal's slice and what's
 * left of the target, plus how it counts toward the tide's meals segment.
 */
@Composable
private fun DayCard(state: MealReviewState) {
    val target = state.targetKcal.coerceAtLeast(1).toDouble()
    val before = state.eatenBefore
    val meal = state.totals.kcal
    val after = before + meal
    val fuel = Resurge.colors.energy
    FoodGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Overline("Your day", Modifier.weight(1f))
                Text(
                    if (after <= target) "%,d kcal left after this".format((target - after).roundToInt()) else "%,d kcal over after this".format((after - target).roundToInt()),
                    style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                    color = if (after <= target) Resurge.colors.textMuted else fuel,
                )
            }
            Row(Modifier.fillMaxWidth().height(12.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted)) {
                val beforeShare = (before / target).toFloat().coerceIn(0f, 1f)
                val mealShare = (meal / target).toFloat().coerceIn(0f, 1f - beforeShare)
                if (beforeShare > 0f) Box(Modifier.weight(beforeShare).fillMaxHeight().background(fuel.copy(alpha = 0.4f)))
                if (mealShare > 0f) Box(Modifier.weight(mealShare).fillMaxHeight().background(fuel))
                val rest = 1f - beforeShare - mealShare
                if (rest > 0f) Spacer(Modifier.weight(rest))
            }
            Row {
                LegendDot(fuel.copy(alpha = 0.4f), "Earlier ${kcalNumber(before)}")
                Spacer(Modifier.width(14.dp))
                LegendDot(fuel, "This meal ${kcalNumber(meal)}")
                Spacer(Modifier.weight(1f))
                Text("of %,d".format(state.targetKcal), style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle)
            }
            if (state.isNew && state.mealsBefore < TIDE_MEALS) {
                val note = if (state.mealsBefore + 1 >= TIDE_MEALS) "Logging this completes today's meals for the tide."
                else "${TIDE_MEALS - state.mealsBefore - 1} more meal after this fills the tide's meals."
                Row(
                    Modifier.fillMaxWidth().clip(Resurge.shape.sm).background(Resurge.colors.accentMuted).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ResurgeIcons.Sparkles, contentDescription = null, tint = Resurge.colors.brandInk, size = 16.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(note, style = Resurge.type.label, color = Resurge.colors.brandInk)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textMuted)
    }
}

/** The model's clarifying question, as a note from the coach with tap-to-answer options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(question: String, options: List<String>, onAnswer: (String) -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.accentMuted).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            CoachMascot(commander = false, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("One question to sharpen this", style = Resurge.type.caption, color = Resurge.colors.brandInk)
                Text(question, style = Resurge.type.headline)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> ChoiceChip(option) { onAnswer(option) } }
            ChoiceChip("Skip", muted = true, onClick = onSkip)
        }
    }
}

/** One food: name (with a nudge when the model wasn't sure), calories, its macro split and portion. */
@Composable
private fun ItemRow(e: EditableItem, vm: MealReviewViewModel) {
    val item = e.item
    val n = item.nutrition
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.lowConfidence) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(ResurgeIcons.AlertCircle, contentDescription = null, tint = Resurge.colors.glowInk, size = 13.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Not sure about this one. Check the portion.", style = Resurge.type.caption, color = Resurge.colors.glowInk)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(kcal(n.kcal), style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 2.dp))
            UnstyledButton(
                onClick = { vm.removeItem(e.key) },
                modifier = Modifier.size(32.dp).clip(Resurge.shape.pill).semantics { contentDescription = "Remove ${item.name}" },
                indication = LocalIndication.current,
            ) { Icon(ResurgeIcons.Close, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            MacroSplit(n, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            val step = if (item.grams >= 200) 25.0 else 10.0
            PortionStepper(
                grams = item.grams,
                label = item.name,
                onDecrease = { vm.setGrams(e.key, (item.grams - step).coerceAtLeast(5.0)) },
                onIncrease = { vm.setGrams(e.key, item.grams + step) },
            )
        }
    }
}

/** − 150 g + in one `surface-muted` pill. */
@Composable
private fun PortionStepper(grams: Double, label: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Less $label", enabled = grams > 5.0, onClick = onDecrease)
        Text("${grams.roundToInt()} g", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"), modifier = Modifier.width(56.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", "More $label", enabled = true, onClick = onIncrease)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp).clip(Resurge.shape.pill).semantics { contentDescription = description },
        indication = LocalIndication.current,
    ) { Text(symbol, style = Resurge.type.headline, color = if (enabled) Resurge.colors.text else Resurge.colors.textSubtle) }
}

@Composable
private fun AddItemRow(busy: Boolean, onAdd: (String) -> Unit) {
    val text = rememberTextFieldState()
    val submit = {
        val value = text.text.toString()
        if (value.isNotBlank()) {
            onAdd(value)
            text.clearText()
        }
    }
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { InputField(text, placeholder = "Add a food (e.g. 1 banana)", maxLength = 80, onDone = submit) }
        Spacer(Modifier.width(8.dp))
        Button("Add", onClick = submit, variant = ButtonVariant.Secondary, loading = busy, enabled = !busy)
    }
}
