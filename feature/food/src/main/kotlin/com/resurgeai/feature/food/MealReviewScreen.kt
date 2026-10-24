package com.resurgeai.feature.food

import androidx.compose.ui.text.input.KeyboardType
import com.resurgeai.core.designsystem.components.TypedNumbers
import com.resurgeai.core.designsystem.components.EditableValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SoftNote
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import kotlin.math.roundToInt

@Composable
fun MealReviewRoute(onDone: () -> Unit, viewModel: MealReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    MealReviewScreen(state, viewModel, onBack = onDone)
}

/** Meals that fill the tide's meals segment (TideMath.MEALS_GOAL on Today). */

/** One-tap corrections in Fix results; each is sent to the estimate as said. */
private val QUICK_FIXES = listOf("Smaller portion", "Bigger portion", "No oil or ghee", "Cooked in more oil", "Add a drink")

private val PHOTO_HEADER = 320.dp

/**
 * The scan / estimate result, with the photo on screen the whole time. While the model works: the
 * photo full-bleed under a sweeping band of light and a card ticking off the steps. Then: the photo
 * with each food as a chip along its bottom edge, and a sheet with the meal, title and servings,
 * calories and macros as tiles, plain balance tags, and what it does to your day. Fix results opens
 * quick corrections, a line to the coach and every food's portion.
 */
@Composable
internal fun MealReviewScreen(state: MealReviewState, vm: MealReviewViewModel, onBack: () -> Unit) {
    var fixing by rememberSaveable { mutableStateOf(false) }
    val ready = state.analyzing == null && state.items.isNotEmpty()
    BackHandler(fixing) { fixing = false }
    Box(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        when {
            state.loading -> Spinner(Modifier.align(Alignment.Center).size(28.dp))
            state.analyzing != null || (state.isNew && state.items.isEmpty()) -> Reading(state, vm, onBack)
            else -> Result(state, vm, ready, onBack, onFix = { fixing = true })
        }
        if (fixing && ready) FixSheet(state, vm, onClose = { fixing = false })
    }
}

// --- Reading ---

/** The photo (or what was typed) full-screen under the sweep, with the steps or the error below. */
@Composable
private fun Reading(state: MealReviewState, vm: MealReviewViewModel, onBack: () -> Unit) {
    val photo = state.preview
    Box(Modifier.fillMaxSize().background(if (photo != null) Color.Black else Resurge.colors.bg)) {
        if (photo != null) {
            JpegImage(photo, "Meal photo", Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 160.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // While fixes are applied, show what changed rather than the first description again.
                val fixing = state.fixes.isNotEmpty() && state.analyzing != null
                Overline(if (fixing) "Updating with" else "You wrote")
                Spacer(Modifier.height(10.dp))
                Text(
                    if (fixing) state.fixes.joinToString(" · ") else "“${state.description.orEmpty()}”",
                    style = Resurge.type.brand.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.analyzing != null) ScanSweep(Modifier.matchParentSize(), dim = photo != null)

        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp)) {
            if (photo != null) GlassButton(ResurgeIcons.Close, "Cancel", onClick = onBack)
            else TopBarButton(ResurgeIcons.Close, "Cancel", onClick = onBack)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .clip(Resurge.shape.lg)
                .background(Resurge.colors.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.analyzing != null) {
                val steps = MealReviewViewModel.stepsFor(state)
                val current = steps.indexOf(state.analyzing).coerceAtLeast(0)
                Text(
                    when {
                        state.fixes.isNotEmpty() -> "Updating your meal"
                        photo != null -> "Reading your plate"
                        else -> "Reading your meal"
                    },
                    style = Resurge.type.headline,
                )
                if (state.fixes.isNotEmpty() && photo != null) {
                    Text(state.fixes.joinToString(" · "), style = Resurge.type.label, color = Resurge.colors.brandInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
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
                        Text(step.removeSuffix("…"), style = Resurge.type.label, color = if (i <= current) Resurge.colors.text else Resurge.colors.textSubtle)
                    }
                }
            } else {
                Text(state.error ?: "Couldn't read this meal.", style = Resurge.type.body)
                if (state.canCorrect) Button("Try again", onClick = vm::retry, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
                Button("Back", onClick = onBack, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A plain round back/close button for the screens without a photo. */
@Composable
private fun TopBarButton(icon: Int, label: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).background(Resurge.colors.surface).semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = Resurge.colors.text, size = 20.dp) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Result(state: MealReviewState, vm: MealReviewViewModel, ready: Boolean, onBack: () -> Unit, onFix: () -> Unit) {
    val photo = state.preview
    Column(Modifier.fillMaxSize().imePadding()) {
        if (photo == null) TopBar(if (state.isNew) "Your meal" else "Edit meal", onBack = onBack)
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (photo != null) {
                    Box(Modifier.fillMaxWidth().height(PHOTO_HEADER)) {
                        JpegImage(photo, "Meal photo", Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))))
                        if (ready) FoodChips(state, Modifier.align(Alignment.BottomStart).padding(bottom = 36.dp), onFix)
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .let { if (photo != null) it.offsetSheet() else it }
                        .background(Resurge.colors.bg)
                        .padding(horizontal = 16.dp, vertical = if (photo != null) 20.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealType.entries.forEach { type -> ChoiceChip(type.label, selected = type == state.type) { vm.setType(type) } }
                    }
                    if (ready) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.title.ifBlank { "Meal" },
                                style = Resurge.type.brand.copy(fontSize = 26.sp, lineHeight = 31.sp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.isNew) {
                                Spacer(Modifier.width(12.dp))
                                ServingsStepper(state.servings, vm::setServings)
                            }
                        }
                        NutritionTiles(state.totals, onFix)
                        BalanceTags(state.totals)
                        state.question?.let { question -> QuestionCard(question, state.options, onAnswer = vm::answer, onSkip = vm::dismissQuestion) }
                        state.error?.let { SoftNote(it) }
                        DayCard(state)
                        state.notes?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(horizontal = 4.dp)) }
                    } else if (!state.isNew) {
                        FoodGroup { AddItemRow(state.addingItem, vm::addByDescription) }
                    }
                    if (!state.isNew) Button("Delete meal", onClick = vm::delete, variant = ButtonVariant.Danger, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (photo != null) {
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    GlassButton(ResurgeIcons.ArrowLeft, "Back", onClick = onBack)
                }
            }
        }
        if (ready) {
            // Docked, so logging is one tap away however long the sheet is.
            Row(
                Modifier.fillMaxWidth().background(Resurge.colors.bg).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button("Fix results", onClick = onFix, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Sparkles, modifier = Modifier.weight(1f))
                Button(if (state.isNew) "Log meal" else "Save", onClick = vm::log, enabled = state.canLog, variant = ButtonVariant.Accent, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Pulls the sheet up over the photo's bottom edge with rounded top corners. */
private fun Modifier.offsetSheet(): Modifier = this
    .layoutOffsetUp(24.dp)
    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))

/** Moves content up by [by] and shrinks its measured height to match, so nothing below gaps. */
private fun Modifier.layoutOffsetUp(by: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = by.roundToPx()
        layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
    },
)

/** Each food as a white chip along the bottom of the photo; tapping one opens Fix results. */
@Composable
private fun FoodChips(state: MealReviewState, modifier: Modifier, onFix: () -> Unit) {
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.items, key = { it.key }) { e ->
            UnstyledButton(
                onClick = onFix,
                modifier = Modifier.clip(Resurge.shape.pill).background(Color.White.copy(alpha = 0.94f)).semantics { contentDescription = "${e.item.name}, ${kcal(e.item.nutrition.kcal)}. Fix" },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                indication = LocalIndication.current,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.item.lowConfidence) {
                        Icon(ResurgeIcons.AlertCircle, contentDescription = null, tint = Color(0xFFB4690E), size = 14.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(e.item.name, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = Color(0xFF111127), maxLines = 1)
                    Text(" · ${kcalNumber(e.item.nutrition.kcal)}", style = Resurge.type.label.copy(fontFeatureSettings = "tnum"), color = Color(0xFF55556B))
                }
            }
        }
    }
}

/** − 1 serving + */
@Composable
private fun ServingsStepper(servings: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", "Fewer servings", enabled = servings > 1) { onChange(servings - 1) }
            EditableValue(
                display = "$servings",
                editText = { "$servings" },
                onSubmit = { t -> TypedNumbers.parseInt(t)?.let { onChange(it.coerceIn(1, 9)) } },
                label = "Servings",
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(24.dp),
            )
            StepButton("+", "More servings", enabled = servings < 9) { onChange(servings + 1) }
        }
        Text(if (servings == 1) "serving" else "servings", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Calories and the three macros as tiles; each opens Fix results. */
@Composable
private fun NutritionTiles(n: Nutrition, onFix: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Calories", kcalNumber(n.kcal), "kcal", ResurgeIcons.Flame, Resurge.colors.energy, Modifier.weight(1f), onFix)
            Tile("Protein", "${n.protein.roundToInt()}", "g", ResurgeIcons.Protein, MacroVisuals.protein(), Modifier.weight(1f), onFix)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Carbs", "${n.carbs.roundToInt()}", "g", ResurgeIcons.Carbs, MacroVisuals.carbs(), Modifier.weight(1f), onFix)
            Tile("Fat", "${n.fat.roundToInt()}", "g", ResurgeIcons.Fat, MacroVisuals.fat(), Modifier.weight(1f), onFix)
        }
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String, icon: Int, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier.clip(Resurge.shape.md).background(Resurge.colors.surface).semantics { contentDescription = "$label $value $unit. Fix" },
        contentPadding = PaddingValues(14.dp),
        indication = LocalIndication.current,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(Resurge.shape.pill).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, size = 15.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
                Icon(ResurgeIcons.Pencil, contentDescription = null, tint = Resurge.colors.textSubtle, size = 14.dp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Resurge.type.metric)
                Spacer(Modifier.width(4.dp))
                Text(unit, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** Plain facts about the balance of the plate: no score, nothing in red. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceTags(n: Nutrition) {
    val tags = balanceTags(n)
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            Text(
                tag,
                style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight),
                color = Resurge.colors.brandInk,
                modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.accentMuted).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

internal fun balanceTags(n: Nutrition): List<String> {
    if (n.kcal <= 0) return emptyList()
    val s = MacroShares.of(n)
    return buildList {
        add(
            when {
                s.protein >= 0.25f -> "High protein"
                s.protein >= 0.15f -> "Moderate protein"
                else -> "Light on protein"
            },
        )
        if (s.carbs >= 0.55f) add("Carb-heavy") else if (s.carbs < 0.2f) add("Low carb")
        if (s.fat >= 0.4f) add("Rich in fat")
        when {
            n.kcal < 300 -> add("Light meal")
            n.kcal > 850 -> add("Hearty meal")
        }
    }
}

// --- Fix results ---

/**
 * What's off: quick corrections and a line to the coach (sent together as a re-estimate), and every
 * food with its own portion stepper. The button shows the new total before anything is logged.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FixSheet(state: MealReviewState, vm: MealReviewViewModel, onClose: () -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    val note = rememberTextFieldState()
    val corrections = picked + listOfNotNull(note.text.toString().trim().takeIf { it.isNotEmpty() })
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = null, indication = null, onClick = onClose))
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Resurge.colors.bg)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(
                Modifier.weight(1f, fill = false).heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
                Text("What's off?", style = Resurge.type.title)
                if (state.canCorrect) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_FIXES.forEach { fix ->
                            ChoiceChip(fix, selected = fix in picked) { if (fix in picked) picked.remove(fix) else picked.add(fix) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoachMascot(commander = false, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) { InputField(note, placeholder = "Tell the coach (e.g. it's brown rice)", maxLength = 120) }
                    }
                }
                SectionLabel("Foods", Modifier.padding(top = 4.dp), trailing = "adjust portions")
                FoodGroup {
                    state.items.forEachIndexed { i, e ->
                        if (i > 0) GroupDivider()
                        key(e.key) { ItemRow(e, vm) }
                    }
                    GroupDivider(inset = 0.dp)
                    AddItemRow(state.addingItem, vm::addByDescription)
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (corrections.isNotEmpty()) {
                    Button(
                        "Re-estimate",
                        onClick = {
                            vm.correct(corrections)
                            onClose()
                        },
                        variant = ButtonVariant.Accent,
                        leadingIcon = ResurgeIcons.Sparkles,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Button("Done · ${kcal(state.totals.kcal)}", onClick = onClose, variant = ButtonVariant.Primary, modifier = Modifier.fillMaxWidth())
                }
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
            if (state.isNew && state.mealsBefore < state.mealsGoal) {
                val left = state.mealsGoal - state.mealsBefore - 1
                val note = if (left == 0) "This completes today's meals for your streak." else "$left more after this for your streak."
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
            // A gram at a time; bigger changes are typed.
            PortionStepper(
                grams = item.grams,
                label = item.name,
                onDecrease = { vm.setGrams(e.key, (item.grams.roundToInt() - 1.0).coerceAtLeast(1.0)) },
                onIncrease = { vm.setGrams(e.key, item.grams.roundToInt() + 1.0) },
                onType = { vm.setGrams(e.key, it.coerceIn(1.0, 3000.0)) },
            )
        }
    }
}

/** − 150 g + in one `surface-muted` pill. */
@Composable
private fun PortionStepper(grams: Double, label: String, onDecrease: () -> Unit, onIncrease: () -> Unit, onType: (Double) -> Unit) {
    Row(Modifier.height(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Less $label", enabled = grams > 1.0, onClick = onDecrease)
        EditableValue(
            display = "${grams.roundToInt()} g",
            editText = { "${grams.roundToInt()}" },
            onSubmit = { t -> TypedNumbers.parse(t)?.let(onType) },
            label = "$label grams",
            style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
            modifier = Modifier.width(56.dp),
        )
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
