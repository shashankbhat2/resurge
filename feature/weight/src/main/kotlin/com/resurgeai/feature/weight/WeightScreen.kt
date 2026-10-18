package com.resurgeai.feature.weight

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun WeightRoute(
    onBack: () -> Unit,
    openLog: Boolean = false,
    openNewGoal: Boolean = false,
    progressStrip: (@Composable () -> Unit)? = null,
    viewModel: WeightViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WeightScreen(
        state = state,
        onBack = onBack,
        onRange = viewModel::setRange,
        onCustom = viewModel::setCustom,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onSetGoal = viewModel::setGoal,
        onClearGoal = viewModel::clearGoal,
        onMaintain = viewModel::maintain,
        openLog = openLog,
        openNewGoal = openNewGoal,
        progressStrip = progressStrip,
    )
}

private sealed interface WeightSheetState {
    data class Log(val editingId: String?) : WeightSheetState

    /** [fresh]: a new goal after reaching one (or maintaining), not an edit of the current one. */
    data class Goal(val fresh: Boolean = false) : WeightSheetState
}

/**
 * Weight: the trend (not the last reading) with how much it changed over the chosen range, the
 * chart, a goal with milestones and a projection, and every weigh-in. Custom shows two handles on
 * the chart to measure any period.
 */
@Composable
internal fun WeightScreen(
    state: WeightUiState,
    onBack: () -> Unit,
    onRange: (WeightRange) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onSave: (id: String?, kg: Double, at: Instant, note: String?) -> Unit,
    onDelete: (WeighIn) -> Unit,
    onSetGoal: (goalKg: Double, pace: Double) -> Unit,
    onClearGoal: () -> Unit,
    onMaintain: () -> Unit = {},
    openLog: Boolean = false,
    /** Opens the goal editor for a fresh goal straight away (from the goal celebration). */
    openNewGoal: Boolean = false,
    /** Progress photos' entry row, under the goal (supplied by the app). */
    progressStrip: (@Composable () -> Unit)? = null,
) {
    val units = remember(state.units) { WeightUnits(state.units) }
    var sheet by remember {
        mutableStateOf<WeightSheetState?>(
            when {
                openLog -> WeightSheetState.Log(null)
                openNewGoal -> WeightSheetState.Goal(fresh = true)
                else -> null
            },
        )
    }
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            "Weight",
            onBack = onBack,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.current != null) RoundButton(ResurgeIcons.Target, if (state.goal == null) "Set a goal" else "Edit goal", accent = false) { sheet = WeightSheetState.Goal() }
                    RoundButton(ResurgeIcons.Plus, "Log weight", accent = true) { sheet = WeightSheetState.Log(null) }
                }
            },
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            state.weighIns.isEmpty() -> EmptyWeight { sheet = WeightSheetState.Log(null) }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Hero(
                    state,
                    units,
                    onEditGoal = { sheet = WeightSheetState.Goal() },
                    onNewGoal = { sheet = WeightSheetState.Goal(fresh = true) },
                    onMaintain = onMaintain,
                )
                RangeChips(state.range, onRange)
                if (state.range == WeightRange.CUSTOM) PeriodCard(state, units)
                state.chartSpan?.let { span ->
                    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
                        WeightChart(
                            trend = state.trend,
                            weighIns = state.weighIns,
                            span = span,
                            units = units,
                            goalKg = state.goal?.goalKg?.takeIf { state.showsGoalLine },
                            plan = state.plan,
                            selection = if (state.range == WeightRange.CUSTOM) state.period else null,
                            onSelect = onCustom,
                        )
                        Legend(state, units)
                    }
                }
                Stats(state, units)
                progressStrip?.invoke()

                val newestFirst = state.weighIns.asReversed()
                SectionLabel("Weigh-ins", Modifier.padding(top = 6.dp), trailing = "${newestFirst.size} logged")
                Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
                    newestFirst.take(shown).forEachIndexed { i, w ->
                        if (i > 0) Divider(Modifier.padding(start = 16.dp))
                        WeighInRow(w, newestFirst.getOrNull(i + 1), units, state.today) { sheet = WeightSheetState.Log(w.id) }
                    }
                }
                if (newestFirst.size > shown) {
                    Button("Show more", onClick = { shown += PAGE }, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    when (val s = sheet) {
        is WeightSheetState.Log -> {
            val editing = s.editingId?.let { id -> state.weighIns.firstOrNull { it.id == id } }
            val last = state.weighIns.lastOrNull()
            LogWeightSheet(
                units = units,
                startKg = last?.kg ?: 70.0,
                lastWeighIn = last,
                editing = editing,
                onSave = { kg, at, note ->
                    onSave(editing?.id, kg, at, note)
                    sheet = null
                },
                onDelete = editing?.let { w -> { onDelete(w); sheet = null } },
                onDismiss = { sheet = null },
            )
        }
        is WeightSheetState.Goal -> state.current?.let { now ->
            GoalSheet(
                units = units,
                currentKg = now,
                goal = state.goal?.takeIf { !s.fresh && !it.maintaining },
                today = state.today,
                onSave = { kg, pace ->
                    onSetGoal(kg, pace)
                    sheet = null
                },
                onRemove = state.goal?.takeIf { !s.fresh }?.let { { onClearGoal(); sheet = null } },
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
    }
}

private const val PAGE = 20

@Composable
private fun RoundButton(icon: Int, label: String, accent: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier
            .size(40.dp)
            .clip(Resurge.shape.pill)
            .background(if (accent) Resurge.colors.accent else Resurge.colors.surface)
            .semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = if (accent) Resurge.colors.onAccent else Resurge.colors.text, size = 20.dp) }
}

@Composable
private fun EmptyWeight(onLog: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(Resurge.shape.pill).background(Resurge.colors.accentMuted), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Scale, contentDescription = null, tint = Resurge.colors.brandInk, size = 26.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Log your first weigh-in", style = Resurge.type.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Weigh yourself in the morning, a few times a week. Resurge smooths the readings into a trend, so one high day doesn't count against you.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button("Log weight", onClick = onLog, variant = ButtonVariant.Accent, leadingIcon = ResurgeIcons.Plus)
    }
}

/**
 * The top card: the trend in big numbers with how much it moved over the selected period, and,
 * with a goal, how far to the next milestone over one stepped bar for the whole goal. Without a
 * goal, a slim row to set one.
 */
@Composable
private fun Hero(state: WeightUiState, units: WeightUnits, onEditGoal: () -> Unit, onNewGoal: () -> Unit, onMaintain: () -> Unit) {
    val now = state.current ?: return
    val change = state.change
    val goal = state.goal
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (goal == null) 14.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(units.number(now), style = Resurge.type.tide.copy(fontSize = 48.sp, lineHeight = 52.sp, fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(4.dp))
                Text(units.label, style = Resurge.type.headline, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                if (change != null && state.range != WeightRange.CUSTOM) ChangeChip(change, units, Modifier.padding(bottom = 10.dp))
            }
            Text(
                state.period?.first?.takeIf { state.range != WeightRange.CUSTOM }?.let { "Trend weight since ${shortDate(it, state.today)}" } ?: "Trend weight",
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
            )
        }
        if (goal != null) GoalProgress(state, goal, now, units, onEditGoal, onNewGoal, onMaintain)
    }
    if (goal == null) SetGoalRow(onEditGoal)
}

@Composable
private fun GoalProgress(state: WeightUiState, goal: WeightGoal, now: Double, units: WeightUnits, onEdit: () -> Unit, onNewGoal: () -> Unit, onMaintain: () -> Unit) {
    when {
        goal.maintaining -> Maintaining(goal, now, units, onEdit)
        goal.reached(now) -> Reached(state, goal, units, onNewGoal, onMaintain)
        else -> InProgress(state, goal, now, units, onEdit)
    }
}

@Composable
private fun InProgress(state: WeightUiState, goal: WeightGoal, now: Double, units: WeightUnits, onEdit: () -> Unit) {
    val next = WeightMath.nextMilestone(goal, now) ?: return
    val justCrossed = WeightMath.newMilestone(goal, state.trend, state.today) != null
    GoalSection(onEdit, "Goal ${units.format(goal.goalKg)}. Edit") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${units.format(next.remainingKg)} to ${units.format(next.targetKg)}",
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1f),
            )
            // Milestones earned so far, as awards.
            if (goal.milestonesCelebrated > 0) {
                Icon(ResurgeIcons.Medal, contentDescription = null, tint = Resurge.colors.glow, size = 16.dp)
                Spacer(Modifier.width(3.dp))
                Text("${goal.milestonesCelebrated}", style = Resurge.type.caption.copy(fontWeight = Resurge.type.headline.fontWeight), color = Resurge.colors.glowInk,
                    modifier = Modifier.semantics { contentDescription = "${goal.milestonesCelebrated} milestones reached" })
            }
        }
        GoalSteps(goal, now, glowLast = justCrossed)
        Ends("Start ${units.format(goal.startKg)}", "Goal ${units.format(goal.goalKg)}")
    }
}

/** After the goal: when it was reached, and the two ways on. */
@Composable
private fun Reached(state: WeightUiState, goal: WeightGoal, units: WeightUnits, onNewGoal: () -> Unit, onMaintain: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.Trophy, contentDescription = null, tint = Resurge.colors.glow, size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                "Reached ${units.format(goal.goalKg)}" + (goal.reachedOn?.let { " on ${shortDate(it, state.today)}" } ?: ""),
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
            )
        }
        GoalSteps(goal, goal.goalKg, glowLast = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("New goal", onClick = onNewGoal, variant = ButtonVariant.Accent, modifier = Modifier.weight(1f))
            Button("Maintain", onClick = onMaintain, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f))
        }
    }
}

/** Holding a weight: the range around it, where the trend sits in it, and whether it's inside. */
@Composable
private fun Maintaining(goal: WeightGoal, now: Double, units: WeightUnits, onEdit: () -> Unit) {
    val band = WeightGoal.MAINTAIN_BAND_KG
    val off = now - goal.goalKg
    GoalSection(onEdit, "Maintaining ${units.format(goal.goalKg)}. Change") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Maintaining ${units.format(goal.goalKg)}", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), modifier = Modifier.weight(1f))
            Text(
                when {
                    abs(off) <= band -> "In range"
                    off > 0 -> "${units.format(off - band)} above"
                    else -> "${units.format(-off - band)} below"
                },
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = if (abs(off) <= band) Resurge.colors.brandInk else Resurge.colors.textMuted,
            )
        }
        // The track spans twice the range either side; the range is the tinted middle half.
        BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
            val w = maxWidth
            val pos = ((off / (band * 2) + 1) / 2).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.fillMaxWidth().height(10.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted))
            Box(Modifier.offset(x = w * 0.25f).width(w * 0.5f).height(10.dp).clip(Resurge.shape.pill).background(Resurge.colors.accentMuted))
            Box(
                Modifier.offset(x = (w - 14.dp) * pos).size(14.dp).clip(Resurge.shape.pill).background(Resurge.colors.surface)
                    .padding(2.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent),
            )
        }
        Text(
            "Range ${units.number(goal.goalKg - band)} to ${units.format(goal.goalKg + band)}",
            style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
            color = Resurge.colors.textSubtle,
        )
    }
}

/** A tappable section of the top card (it opens the goal editor). */
@Composable
private fun GoalSection(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        indication = LocalIndication.current,
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Labels for the two ends of a bar. */
@Composable
private fun Ends(start: String, end: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(start, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle, modifier = Modifier.weight(1f))
        Text(end, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle)
    }
}

/**
 * The whole goal as one bar cut into milestone steps ([WeightMath.milestoneSteps]), each as wide
 * as its share. Finished steps are solid, the current one fills as the trend moves,
 * the rest wait. [glowLast] lights the latest finished step for a week after crossing it.
 */
@Composable
private fun GoalSteps(goal: WeightGoal, now: Double, glowLast: Boolean) {
    val done = goal.doneKg(now)
    val steps = WeightMath.milestoneSteps(goal)
    val lastDone = steps.indexOfLast { (_, to) -> done >= to - 1e-9 }
    Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(if (steps.size > 10) 2.dp else 3.dp)) {
        steps.forEachIndexed { i, (from, to) ->
            val fill = ((done - from) / (to - from)).coerceIn(0.0, 1.0).toFloat()
            val color = if (glowLast && i == lastDone) Resurge.colors.glow else Resurge.colors.accent
            Box(Modifier.weight((to - from).toFloat()).fillMaxHeight().clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted)) {
                if (fill > 0f) Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(Resurge.shape.pill).background(color))
            }
        }
    }
}

@Composable
private fun SetGoalRow(onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        indication = LocalIndication.current,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.Target, contentDescription = null, tint = Resurge.colors.brandInk, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Text("Set a goal", style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), modifier = Modifier.weight(1f))
            Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 18.dp)
        }
    }
}

/** "↓ 1.0 kg": the change as a brand-tint chip; up or down, never red. */
@Composable
private fun ChangeChip(change: Double, units: WeightUnits, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Resurge.shape.pill).background(Resurge.colors.accentMuted).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (abs(change) >= 0.05) {
            Icon(ResurgeIcons.ArrowUp, contentDescription = if (change < 0) "Down" else "Up", tint = Resurge.colors.brandInk, size = 13.dp, modifier = if (change < 0) Modifier.rotate(180f) else Modifier)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            units.signed(change).removePrefix("−").removePrefix("+"),
            style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
            color = Resurge.colors.brandInk,
        )
    }
}

@Composable
private fun RangeChips(selected: WeightRange, onSelect: (WeightRange) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Resurge.shape.pill).background(Resurge.colors.surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WeightRange.entries.forEach { r ->
            val on = r == selected
            UnstyledButton(
                onClick = { onSelect(r) },
                role = Role.Tab,
                modifier = Modifier
                    .weight(if (r == WeightRange.CUSTOM) 1.7f else 1f)
                    .clip(Resurge.shape.pill)
                    .background(if (on) Resurge.colors.brandInk else Resurge.colors.surface)
                    .semantics { this.selected = on },
                contentPadding = PaddingValues(vertical = 8.dp),
                indication = LocalIndication.current,
            ) {
                Text(
                    r.label,
                    style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                    color = if (on) Resurge.colors.onAccent else Resurge.colors.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Custom period: the two dates, what changed between them, how long and how many weigh-ins. */
@Composable
private fun PeriodCard(state: WeightUiState, units: WeightUnits) {
    val (from, to) = state.period ?: return
    val change = state.change ?: return
    val days = ChronoUnit.DAYS.between(from, to)
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DatePill(shortDate(from, state.today))
            Text("  to  ", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            DatePill(shortDate(to, state.today))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(units.signed(change).substringBefore(" ").removePrefix("−").removePrefix("+"), style = Resurge.type.tide.copy(fontSize = 34.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(4.dp))
            Text(
                "${units.label} ${when {
                    abs(change) < 0.05 -> "change"
                    change < 0 -> "lost"
                    else -> "gained"
                }}",
                style = Resurge.type.label,
                color = Resurge.colors.textMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text("Over ${spanLabel(days)}, ${state.countInPeriod} weigh-ins", style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun DatePill(text: String) {
    Text(
        text,
        style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"),
        modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun Legend(state: WeightUiState, units: WeightUnits) {
    Row(Modifier.padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(Resurge.colors.accent, "Trend", wide = true)
        LegendItem(Resurge.colors.textSubtle, "Weigh-ins", wide = false)
        val span = state.chartSpan
        val plan = state.plan
        if (plan != null && span != null && planShows(plan, state.trend, state.weighIns, span, units, plan.goalKg.takeIf { state.showsGoalLine })) {
            LegendItem(Resurge.colors.brandInk.copy(alpha = 0.5f), "Plan", wide = true)
        }
        state.goal?.takeIf { state.showsGoalLine }?.let { LegendItem(Resurge.colors.glow, "Goal ${units.format(it.goalKg)}", wide = true) }
    }
    if (state.range == WeightRange.CUSTOM) {
        Text("Drag a handle, or tap the chart, to change the period", style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String, wide: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (wide) 12.dp else 6.dp, height = if (wide) 3.dp else 6.dp).clip(Resurge.shape.pill).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}

/** Start (or From/To for a custom period), weekly pace, weigh-ins. */
@Composable
private fun Stats(state: WeightUiState, units: WeightUnits) {
    val (from, to) = state.period ?: return
    val rate = state.weeklyRate
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.range == WeightRange.CUSTOM) {
            Stat("From", WeightMath.trendOn(state.trend, from)?.let(units::number) ?: "–", units.label, Modifier.weight(1f))
            Stat("To", WeightMath.trendOn(state.trend, to)?.let(units::number) ?: "–", units.label, Modifier.weight(1f))
        } else {
            Stat("Start", WeightMath.trendOn(state.trend, from)?.let(units::number) ?: "–", units.label, Modifier.weight(1f))
        }
        Stat("Per week", rate?.let { units.signed(it).substringBefore(" ") } ?: "–", if (rate != null) units.label else null, Modifier.weight(1f))
        if (state.range != WeightRange.CUSTOM) Stat("Weigh-ins", "${state.countInPeriod}", null, Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String?, modifier: Modifier) {
    Column(modifier.clip(Resurge.shape.md).background(Resurge.colors.surface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"))
            unit?.let {
                Spacer(Modifier.width(2.dp))
                Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun WeighInRow(w: WeighIn, previous: WeighIn?, units: WeightUnits, today: LocalDate, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${units.format(w.kg)}, ${relativeDay(w.date(), today)}. Edit" },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(relativeDay(w.date(), today), style = Resurge.type.label)
                val sub = listOfNotNull(w.note, if (w.source == WeighInSource.HEALTH_CONNECT) "From Health Connect" else null).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
            }
            Text(units.format(w.kg), style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"))
            Text(
                previous?.let { units.signed(w.kg - it.kg).substringBefore(" ") } ?: "",
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = Resurge.colors.textSubtle,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(52.dp),
            )
        }
    }
}
