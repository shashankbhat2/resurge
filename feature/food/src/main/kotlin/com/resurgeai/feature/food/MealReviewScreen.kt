package com.resurgeai.feature.food

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.components.ValueStepper
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.MealType
import kotlin.math.roundToInt

@Composable
fun MealReviewRoute(onDone: () -> Unit, viewModel: MealReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    MealReviewScreen(state, viewModel, onBack = onDone)
}

/** Photo, editable foods with live totals, an optional clarifying question, then Log. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealReviewScreen(state: MealReviewState, vm: MealReviewViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (state.isNew) "Review meal" else "Edit meal", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.preview?.let { JpegImage(it, "Meal photo", Modifier.fillMaxWidth().height(220.dp)) }

            if (state.analyzing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Spinner(Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(state.analyzing, style = Resurge.type.label, color = Resurge.colors.textMuted)
                }
                SkeletonGroup {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth().height(96.dp), Resurge.shape.lg)
                        repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(84.dp), Resurge.shape.lg) }
                    }
                }
            }

            state.error?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ResurgeIcons.AlertCircle, contentDescription = null, tint = Resurge.colors.danger, size = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(message, style = Resurge.type.body, modifier = Modifier.weight(1f))
                    }
                    if (state.isNew && state.items.isEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Button("Try again", onClick = vm::retry, variant = ButtonVariant.Secondary)
                    }
                }
            }

            if (state.analyzing == null && state.items.isNotEmpty()) {
                state.question?.let { question ->
                    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        Text(question, style = Resurge.type.label)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.options.forEach { option -> Chip(option) { vm.answer(option) } }
                            Chip("Skip", muted = true, onClick = vm::dismissQuestion)
                        }
                    }
                }

                TotalsCard(state)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { type -> Chip(type.label, selected = type == state.type) { vm.setType(type) } }
                }

                SectionHeader("Foods")
                state.items.forEach { e ->
                    androidx.compose.runtime.key(e.key) { ItemCard(e, vm) }
                }
                AddItemRow(state.addingItem, vm::addByDescription)
                state.notes?.let {
                    Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    text = if (state.isNew) "Log to ${state.type.label.lowercase().replaceFirstChar { it.uppercase() }}" else "Save",
                    onClick = vm::log,
                    enabled = state.canLog,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (!state.isNew && state.analyzing == null && state.error == null && !state.loading) {
                AddItemRow(state.addingItem, vm::addByDescription)
            }
            if (!state.isNew && !state.loading) {
                Button("Delete meal", onClick = vm::delete, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TotalsCard(state: MealReviewState) {
    val t = state.totals
    Card(Modifier.fillMaxWidth()) {
        Text(state.title.ifBlank { "Meal" }, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(kcal(t.kcal), style = Resurge.type.display.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            MacroTotal("Protein", t.protein, ResurgeIcons.Protein, MacroVisuals.protein(), Modifier.weight(1f))
            MacroTotal("Carbs", t.carbs, ResurgeIcons.Carbs, MacroVisuals.carbs(), Modifier.weight(1f))
            MacroTotal("Fat", t.fat, ResurgeIcons.Fat, MacroVisuals.fat(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroTotal(label: String, grams: Double, icon: Int, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, size = 20.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text("${grams.roundToInt()} g", style = Resurge.type.title.copy(fontFeatureSettings = "tnum"))
            Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
    }
}

@Composable
private fun ItemCard(e: EditableItem, vm: MealReviewViewModel) {
    val item = e.item
    val n = item.nutrition
    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = Resurge.type.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.lowConfidence) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ResurgeIcons.AlertCircle, contentDescription = null, tint = Resurge.colors.energy, size = 13.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Not sure, check this", style = Resurge.type.caption, color = Resurge.colors.energy)
                    }
                }
            }
            Text(kcal(n.kcal), style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(4.dp))
            IconButton(ResurgeIcons.Close, contentDescription = "Remove ${item.name}", onClick = { vm.removeItem(e.key) })
        }
        Spacer(Modifier.height(8.dp))
        MacroChips(n)
        Spacer(Modifier.height(10.dp))
        val step = if (item.grams >= 200) 25.0 else 10.0
        ValueStepper(
            label = "Portion",
            value = item.grams,
            onValueChange = { vm.setGrams(e.key, it ?: item.grams) },
            step = step,
            range = 5.0..3000.0,
            format = { "${it.roundToInt()} g" },
            optional = false,
        )
    }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { InputField(text, placeholder = "Add food (e.g. 1 banana)", maxLength = 80, onDone = submit) }
        Spacer(Modifier.width(8.dp))
        Button("Add", onClick = submit, variant = ButtonVariant.Secondary, loading = busy, enabled = !busy)
    }
}

@Composable
private fun Chip(label: String, muted: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(
                when {
                    selected -> Resurge.colors.strong
                    muted -> Resurge.colors.surfaceMuted
                    else -> Resurge.colors.surface
                },
            )
            .border(1.dp, Resurge.colors.border, Resurge.shape.pill)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        indication = LocalIndication.current,
    ) {
        Text(
            label,
            style = Resurge.type.label,
            color = when {
                selected -> Resurge.colors.bg
                muted -> Resurge.colors.textMuted
                else -> Resurge.colors.text
            },
        )
    }
}
