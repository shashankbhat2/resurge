package com.resurgeai.feature.food

import com.resurgeai.core.designsystem.components.OverviewFooterHeight
import com.resurgeai.core.designsystem.components.OverviewBodyHeight
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
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
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.LocalFloatingBarInset
import com.resurgeai.core.designsystem.components.LocalHeaderInset
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.MagnitudeBar
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.StatCell
import com.resurgeai.core.designsystem.components.StatDivider
import com.resurgeai.core.designsystem.components.SwipeToDelete
import com.resurgeai.core.designsystem.components.UndoBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.Nutrition
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FoodRoute(
    onOpenMeal: (String) -> Unit,
    onLogMeal: () -> Unit,
    onOpenMcpKeys: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FoodScreen(
        state = state,
        onOpenMeal = onOpenMeal,
        onLogMeal = onLogMeal,
        onOpenMcpKeys = onOpenMcpKeys,
        onAddWater = viewModel::addWater,
        onDelete = viewModel::delete,
        onUndo = viewModel::undoDelete,
        onUndoTimeout = viewModel::clearDeleted,
    )
}

/** Meals that fill the tide's meals segment (TideMath.MEALS_GOAL on Today). */

/**
 * Food tab: today's fuel (calories, macros, meals and water), then meals by day. In-app logging
 * (photo, barcode, type) is on by default; the AI assistant can also log meals over MCP. With
 * "Log meals on this device" off, rows are read-only.
 */
@Composable
fun FoodScreen(
    state: FoodUiState,
    onOpenMeal: (String) -> Unit,
    onLogMeal: () -> Unit,
    onOpenMcpKeys: () -> Unit,
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
                    SkeletonBox(Modifier.fillMaxWidth().height(300.dp), Resurge.shape.lg)
                    repeat(3) { SkeletonBox(Modifier.fillMaxWidth().height(84.dp), Resurge.shape.md) }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "fuel") { FuelCard(state, onAddWater.takeIf { state.logOnDevice }) }
                if (!state.logOnDevice) item(key = "mcp") { McpPrompt(onOpenMcpKeys) }
                if (state.todayTotals.micros.isNotEmpty() || state.todayTotals.fiber > 0) item(key = "micros") { TodayMicros(state.todayTotals, state.macroTargets.fiber) }
                if (state.days.isNotEmpty()) item(key = "glycemic") { GlycemicCard(state) }
                if (state.days.isEmpty() && state.logOnDevice) item(key = "empty") { EmptyFeed(onLogMeal) }
                state.days.forEach { day ->
                    val meals = day.slots.flatMap { it.second }
                    item(key = "d${day.date}") {
                        SectionLabel(
                            dayLabel(day.date, state.today),
                            Modifier.padding(top = 8.dp),
                            trailing = "${meals.size} ${if (meals.size == 1) "meal" else "meals"} · ${kcal(day.totals.kcal)}",
                        )
                    }
                    items(meals, key = { it.id }) { meal ->
                        if (state.logOnDevice) {
                            SwipeToDelete(onDelete = { onDelete(meal) }) { MealRow(meal) { onOpenMeal(meal.id) } }
                        } else {
                            MealRow(meal, onClick = null)
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

/**
 * The tab's hero, like Today's tide card: the calorie ring and macro bars on top, then a footer of
 * meals (toward the tide's meals segment) and water with a quick +250.
 */
@Composable
private fun FuelCard(state: FoodUiState, onAddWater: ((Int) -> Unit)?) {
    val eaten = state.todayTotals
    val target = state.targets.calories
    val left = target - eaten.kcal.roundToInt()
    val mealsToday = state.days.firstOrNull { it.date == state.today }?.slots?.sumOf { it.second.size } ?: 0
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Column(Modifier.fillMaxWidth().height(OverviewBodyHeight).padding(20.dp)) {
            Overline("Fuel today")
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(
                    // Over target keeps the fuel colour: going over is information, not an error.
                    segments = listOf(RingSegment((eaten.kcal / target).toFloat().coerceIn(0f, 1f), Resurge.colors.energy)),
                    modifier = Modifier.size(128.dp).semantics { contentDescription = "${eaten.kcal.roundToInt()} of $target kcal eaten" },
                    strokeWidth = 12.dp,
                    trackColor = Resurge.colors.surfaceMuted,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(kcalNumber(eaten.kcal), style = Resurge.type.metric.copy(fontSize = 30.sp, lineHeight = 34.sp))
                        Text("of %,d kcal".format(target), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (left >= 0) "%,d left".format(left) else "%,d over".format(-left),
                        style = Resurge.type.headline,
                        color = if (left >= 0) Resurge.colors.text else Resurge.colors.energy,
                    )
                    MacroBar("Protein", eaten.protein, state.macroTargets.protein, MacroVisuals.protein())
                    MacroBar("Carbs", eaten.carbs, state.macroTargets.carbs, MacroVisuals.carbs())
                    MacroBar("Fat", eaten.fat, state.macroTargets.fat, MacroVisuals.fat())
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().height(OverviewFooterHeight)) {
            StatCell(
                icon = ResurgeIcons.Bowl,
                iconTint = Resurge.colors.energy,
                iconBackground = Resurge.colors.surfaceMuted,
                value = "$mealsToday ${if (mealsToday == 1) "meal" else "meals"}",
                label = when {
                    state.mealsGoal == 0 -> "Logged today"
                    mealsToday >= state.mealsGoal -> "Streak complete"
                    else -> "${state.mealsGoal - mealsToday} more to go"
                },
                modifier = Modifier.weight(1f),
            )
            StatDivider()
            WaterCell(state.waterMl, state.targets.waterMl, onAdd = onAddWater?.let { add -> { add(250) } }, modifier = Modifier.weight(1f))
        }
    }
}

/** Today's micronutrients from the meals logged so far, against an adult's daily guide. */
@Composable
private fun TodayMicros(n: Nutrition, fiberTarget: Int) {
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp)) {
        Overline("Fibre and micronutrients today")
        Spacer(Modifier.height(4.dp))
        Text("From the foods' nutrition tables, as a share of an adult's daily guide", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        Spacer(Modifier.height(14.dp))
        MicronutrientList(n, collapsed = 6, fiberTarget = fiberTarget)
    }
}

@Composable
private fun MacroBar(label: String, grams: Double, target: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
            Text("${grams.roundToInt()} / $target g", style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle)
        }
        MagnitudeBar((grams / target.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f), color, height = 8.dp)
    }
}

/** Water so far against the target, with a one-tap +250 ml when logging on this device is on. */
@Composable
private fun WaterCell(ml: Int, target: Int, onAdd: (() -> Unit)?, modifier: Modifier) {
    Row(modifier.fillMaxHeight().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ResurgeIcons.Droplet, contentDescription = null, tint = Resurge.colors.water, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("%,.1f L".format(ml / 1000.0), style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 22.sp))
            }
            Text("of %,.1f L water".format(target / 1000.0), style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
        }
        if (onAdd != null) UnstyledButton(
            onClick = onAdd,
            modifier = Modifier.clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted).semantics { contentDescription = "Add 250 ml of water" },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            indication = LocalIndication.current,
        ) { Text("+250", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = Resurge.colors.water) }
    }
}

/** A logged meal: photo, title, slot and time, calories, and its macro split. Tapping opens it (when [onClick] is set). */
@Composable
private fun MealRow(meal: Meal, onClick: (() -> Unit)?) {
    val totals = meal.totals
    UnstyledButton(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MealThumb(meal.thumbnail)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(meal.title, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val time = meal.loggedAt.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter())
                        Text("${meal.type.label} · $time", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(kcalNumber(totals.kcal), style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 22.sp))
                        Spacer(Modifier.width(2.dp))
                        Text("kcal", style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                MacroSplit(totals)
            }
        }
    }
}

@Composable
private fun EmptyFeed(onLogMeal: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(Resurge.shape.md).background(Resurge.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Log your first meal", style = Resurge.type.headline)
        Text(
            "Snap a photo, scan a barcode or type what you had. Your AI assistant can also log meals through MCP.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
        )
        Button("Log a meal", onClick = onLogMeal, variant = ButtonVariant.Accent)
    }
}

/**
 * Shown while "Log meals on this device" is off: meals arrive from the AI assistant over MCP, so the
 * tab points there instead of offering its own logging.
 */
@Composable
private fun McpPrompt(onOpenMcpKeys: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(Resurge.shape.md)
            .border(1.dp, Resurge.colors.border, Resurge.shape.md)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent))
            Spacer(Modifier.width(8.dp))
            Overline("Meal logging · MCP", color = Resurge.colors.text)
        }
        Text("Meals are logged through your AI assistant via MCP.", style = Resurge.type.body)
        Text(
            "Ask it to log what you ate and the meals show up here. To also snap, scan or type on this phone, turn on Settings › Data › Log meals on this device.",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
        )
        Button("MCP API keys", onClick = onOpenMcpKeys, variant = ButtonVariant.Secondary, leadingIcon = ResurgeIcons.Key)
    }
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}
