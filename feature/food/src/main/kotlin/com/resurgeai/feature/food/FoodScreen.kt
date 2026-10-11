package com.resurgeai.feature.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.MagnitudeBar
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SwipeToDelete
import com.resurgeai.core.designsystem.components.UndoBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.Meal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodRoute(
    onOpenMeal: (String) -> Unit,
    onLogMeal: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FoodScreen(
        state = state,
        onOpenMeal = onOpenMeal,
        onLogMeal = onLogMeal,
        onAddWater = viewModel::addWater,
        onDelete = viewModel::delete,
        onUndo = viewModel::undoDelete,
        onUndoTimeout = viewModel::clearDeleted,
    )
}

/** Food tab: today's ring, macros and water, then meals by day and slot. */
@Composable
fun FoodScreen(
    state: FoodUiState,
    onOpenMeal: (String) -> Unit,
    onLogMeal: () -> Unit,
    onAddWater: (Int) -> Unit,
    onDelete: (Meal) -> Unit,
    onUndo: () -> Unit,
    onUndoTimeout: () -> Unit,
) {
    val top = LocalHeaderInset.current + 12.dp
    val bottom = LocalFloatingBarInset.current + 24.dp
    Box(Modifier.fillMaxSize()) {
        if (state.loading) {
            SkeletonGroup {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = top), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth().height(200.dp), Resurge.shape.lg)
                    repeat(2) { SkeletonBox(Modifier.fillMaxWidth().height(96.dp), Resurge.shape.lg) }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { SummaryCard(state) }
                item(key = "water") { WaterCard(state.waterMl, state.targets.waterMl, onAddWater) }
                if (state.days.isEmpty()) item(key = "empty") { EmptyFeed(onLogMeal) }
                state.days.forEach { day ->
                    item(key = "d${day.date}") {
                        SectionHeader(
                            dayLabel(day.date, state.today),
                            trailing = { Text(kcal(day.totals.kcal), style = Resurge.type.caption, color = Resurge.colors.textMuted) },
                        )
                    }
                    day.slots.forEach { (type, meals) ->
                        item(key = "s${day.date}$type") {
                            val slotKcal = meals.sumOf { it.totals.kcal }
                            Row(Modifier.padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(type.label, style = Resurge.type.label, modifier = Modifier.weight(1f))
                                Text(kcal(slotKcal), style = Resurge.type.caption, color = Resurge.colors.textMuted)
                            }
                        }
                        items(meals, key = { it.id }) { meal ->
                            SwipeToDelete(onDelete = { onDelete(meal) }) { MealCard(meal) { onOpenMeal(meal.id) } }
                        }
                    }
                }
            }
        }
        UndoBar(
            message = state.deleted?.let { "${it.title} deleted" },
            onUndo = onUndo,
            onTimeout = onUndoTimeout,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = LocalFloatingBarInset.current + 8.dp),
        )
    }
}

@Composable
private fun SummaryCard(state: FoodUiState) {
    val eaten = state.todayTotals
    val target = state.targets.calories
    val left = target - eaten.kcal.roundToInt()
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(
                segments = listOf(RingSegment((eaten.kcal / target).toFloat().coerceIn(0f, 1f), if (left < 0) Resurge.colors.danger else Resurge.colors.energy)),
                modifier = Modifier.size(120.dp),
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%,d".format(eaten.kcal.roundToInt()), style = Resurge.type.title.copy(fontFeatureSettings = "tnum"))
                    Text("of %,d".format(target), style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(if (left >= 0) "%,d kcal left".format(left) else "%,d kcal over".format(-left), style = Resurge.type.title)
                Spacer(Modifier.height(2.dp))
                Text("Today", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
        }
        Spacer(Modifier.height(18.dp))
        MacroBar("Protein", eaten.protein, state.macroTargets.protein, ResurgeIcons.Protein, MacroVisuals.protein())
        Spacer(Modifier.height(12.dp))
        MacroBar("Carbs", eaten.carbs, state.macroTargets.carbs, ResurgeIcons.Carbs, MacroVisuals.carbs())
        Spacer(Modifier.height(12.dp))
        MacroBar("Fat", eaten.fat, state.macroTargets.fat, ResurgeIcons.Fat, MacroVisuals.fat())
    }
}

@Composable
private fun MacroBar(label: String, grams: Double, target: Int, icon: Int, tint: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = Resurge.type.label, modifier = Modifier.weight(1f))
            Text("${grams.roundToInt()} / $target g", style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(6.dp))
        MagnitudeBar(fraction = (grams / target.coerceAtLeast(1)).toFloat(), color = tint, height = 8.dp)
    }
}

@Composable
private fun WaterCard(ml: Int, target: Int, onAdd: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(ResurgeIcons.Droplet, tint = Resurge.colors.water, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("%,d / %,d ml".format(ml, target), style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.height(6.dp))
                MagnitudeBar(fraction = ml.toFloat() / target.coerceAtLeast(1), color = Resurge.colors.water, height = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            Button("+250", onClick = { onAdd(250) }, variant = ButtonVariant.Secondary)
        }
    }
}

@Composable
private fun MealCard(meal: Meal, onClick: () -> Unit) {
    val totals = meal.totals
    Card(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val thumb = meal.thumbnail
            if (thumb != null) JpegImage(thumb, null, Modifier.size(56.dp))
            else IconBadge(ResurgeIcons.Bowl, tint = Resurge.colors.energy, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meal.title, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val time = meal.loggedAt.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter())
                Text("$time · ${meal.items.size} ${if (meal.items.size == 1) "item" else "items"}", style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Spacer(Modifier.width(8.dp))
            Text(kcal(totals.kcal), style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
        }
        Spacer(Modifier.height(12.dp))
        MacroChips(totals)
    }
}

@Composable
private fun EmptyFeed(onLogMeal: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(ResurgeIcons.Camera, tint = Resurge.colors.energy, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text("Log your first meal", style = Resurge.type.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Snap a photo or describe what you ate. You can adjust everything before it's logged.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button("Log a meal", onClick = onLogMeal, leadingIcon = ResurgeIcons.Plus)
    }
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
}
