package com.resurgeai.feature.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.InputField
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeightGoal
import com.resurgeai.core.model.WeightMath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** A bottom sheet over a scrim; tapping outside closes it. */
@Composable
internal fun WeightSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClick = onDismiss).imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Resurge.colors.surface)
                    .clickable(interactionSource = null, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
                content()
            }
        }
    }
}

// --- Log weight ---

/**
 * Log a weigh-in ([editing] null) or edit one. Opens on [startKg] (the last reading), so most
 * mornings it's a short drag on the ruler. Date chips cover the last week; time is now for today.
 */
@Composable
internal fun LogWeightSheet(
    units: WeightUnits,
    startKg: Double,
    lastWeighIn: WeighIn?,
    editing: WeighIn?,
    onSave: (kg: Double, at: Instant, note: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Opens smart scale setup; shown on a new weigh-in only. */
    onSmartScale: (() -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var tenths by remember { mutableIntStateOf((units.toUnit(editing?.kg ?: startKg) * 10).roundToInt()) }
    var date by remember { mutableStateOf(editing?.date(zone) ?: today) }
    var showNote by remember { mutableStateOf(editing?.note != null) }
    val note = rememberTextFieldState(editing?.note.orEmpty())
    val range = if (units.label == "lb") 600..8800 else 300..4000

    WeightSheet(onDismiss) {
        Text(if (editing == null) "Log weight" else "Edit weigh-in", style = Resurge.type.title)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallStep("−", "Less") { tenths = (tenths - 1).coerceIn(range) }
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(WeightUnits.decimal(tenths / 10.0), style = Resurge.type.tide.copy(fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum"))
                    Spacer(Modifier.width(4.dp))
                    Text(units.label, style = Resurge.type.headline, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Spacer(Modifier.width(16.dp))
                SmallStep("+", "More") { tenths = (tenths + 1).coerceIn(range) }
            }
            lastWeighIn?.takeIf { editing == null }?.let { last ->
                Text("Last: ${units.format(last.kg)} · ${relativeDay(last.date(zone), today)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
        Ruler(tenths, range) { tenths = it }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (0L..6L).map { today.minusDays(it) }.forEach { d ->
                val label = when (d) {
                    today -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.getDefault()))
                }
                Chip(label, selected = d == date) { date = d }
            }
        }
        if (showNote) {
            InputField(note, placeholder = "Note (e.g. after a late dinner)", maxLength = 120)
        } else {
            Chip("Add a note", selected = false, icon = ResurgeIcons.Note) { showNote = true }
        }
        Button(
            if (editing == null) "Save" else "Save changes",
            onClick = {
                val at = when {
                    editing != null && editing.date(zone) == date -> editing.at
                    date == today -> Instant.now()
                    else -> date.atTime(LocalTime.of(8, 0)).atZone(zone).toInstant()
                }
                onSave(units.toKg(tenths / 10.0), at, note.text.toString())
            },
            variant = ButtonVariant.Accent,
            modifier = Modifier.fillMaxWidth(),
        )
        onDelete?.let { Button("Delete weigh-in", onClick = it, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth()) }
        onSmartScale?.let { Button("Smart scale", onClick = it, variant = ButtonVariant.Ghost, leadingIcon = ResurgeIcons.Scale, modifier = Modifier.fillMaxWidth()) }
    }
}

/** A horizontal ruler, 0.1 a tick, dragged under a fixed needle; a tick on each whole number. */
@Composable
private fun Ruler(tenths: Int, range: IntRange, onChange: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(tenths)
    var carry by remember { mutableFloatStateOf(0f) }
    val tickGap = 9.dp
    val tickColor = Resurge.colors.textSubtle
    val needleColor = Resurge.colors.accent
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surfaceMuted)
            .semantics { contentDescription = "Weight ruler" }
            .pointerInput(range) {
                val gap = tickGap.toPx()
                detectHorizontalDragGestures(onDragEnd = { carry = 0f }) { change, dx ->
                    change.consume()
                    carry -= dx
                    val steps = (carry / gap).toInt()
                    if (steps != 0) {
                        carry -= steps * gap
                        val next = (current + steps).coerceIn(range)
                        if (next / 10 != current / 10) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        else haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        onChange(next)
                    }
                }
            },
    ) {
        val gap = tickGap.toPx()
        val cx = size.width / 2
        val visible = (size.width / gap / 2).toInt() + 1
        for (k in -visible..visible) {
            val v = tenths + k
            if (v !in range) continue
            val x = cx + k * gap - carry
            val h = when {
                v % 10 == 0 -> 0.56f
                v % 5 == 0 -> 0.4f
                else -> 0.26f
            }
            drawLine(tickColor, Offset(x, size.height * (0.82f - h)), Offset(x, size.height * 0.82f), strokeWidth = (if (v % 10 == 0) 1.6f else 1f).dp.toPx())
        }
        drawLine(needleColor, Offset(cx, size.height * 0.12f), Offset(cx, size.height * 0.88f), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun SmallStep(symbol: String, description: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).semantics { contentDescription = "$description 0.1" },
        indication = LocalIndication.current,
    ) { Text(symbol, style = Resurge.type.headline) }
}

@Composable
internal fun Chip(label: String, selected: Boolean, icon: Int? = null, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier.clip(Resurge.shape.pill).background(if (selected) Resurge.colors.accentMuted else Resurge.colors.surfaceMuted).semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = if (selected) Resurge.colors.brandInk else Resurge.colors.text, size = 15.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                color = if (selected) Resurge.colors.brandInk else Resurge.colors.text,
            )
        }
    }
}

// --- Goal ---

/** Goal weight and pace; each pace shows its target date from today's trend. */
@Composable
internal fun GoalSheet(
    units: WeightUnits,
    currentKg: Double,
    goal: WeightGoal?,
    today: LocalDate,
    onSave: (goalKg: Double, pace: Double) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // Half-unit steps in the person's units.
    var halves by remember { mutableIntStateOf(((units.toUnit(goal?.goalKg ?: (currentKg - 5)) * 2).roundToInt())) }
    var pace by remember { mutableFloatStateOf((goal?.paceKgPerWeek ?: 0.5).toFloat()) }
    val goalKg = units.toKg(halves / 2.0)
    val same = abs(goalKg - currentKg) < 0.25
    val losing = goalKg < currentKg

    WeightSheet(onDismiss) {
        Text("Weight goal", style = Resurge.type.title)
        Text("You're at ${units.format(currentKg)} (trend).", style = Resurge.type.body, color = Resurge.colors.textMuted)
        Row(
            Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surfaceMuted).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigStep("−", "Lower goal") { halves = (halves - 1).coerceAtLeast(60) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(WeightUnits.decimal(halves / 2.0), style = Resurge.type.display.copy(fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(4.dp))
                Text(units.label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            BigStep("+", "Higher goal") { halves = (halves + 1).coerceAtMost(1600) }
        }
        Text(
            when {
                same -> "That's where you are now. Pick a different weight."
                losing -> "${units.signed(goalKg - currentKg).removePrefix("−")} to lose"
                else -> "${units.signed(goalKg - currentKg).removePrefix("+")} to gain"
            },
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Overline("Pace")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WeightGoal.PACES.forEach { (kgPerWeek, name) ->
                val on = abs(pace - kgPerWeek) < 0.01
                val by = if (same) null else WeightMath.dateAtPace(currentKg, goalKg, kgPerWeek, today)
                UnstyledButton(
                    onClick = { pace = kgPerWeek.toFloat() },
                    role = Role.RadioButton,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Resurge.shape.md)
                        .background(if (on) Resurge.colors.accentMuted else Resurge.colors.surfaceMuted)
                        .semantics { selected = on },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    indication = LocalIndication.current,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = if (on) Resurge.colors.brandInk else Resurge.colors.text)
                            Text("${units.pace(kgPerWeek)} a week", style = Resurge.type.caption, color = Resurge.colors.textMuted)
                        }
                        by?.let { Text("by ${shortDate(it, today)}", style = Resurge.type.label.copy(fontFeatureSettings = "tnum"), color = if (on) Resurge.colors.brandInk else Resurge.colors.text) }
                    }
                }
            }
        }
        Text(
            "Losing 0.5 to 1% of your weight a week is a pace most people can keep up. Your daily calorie target stays as it is unless you change it in Settings.",
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            modifier = Modifier.clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted).padding(12.dp),
        )
        Button("Save goal", onClick = { onSave(goalKg, pace.toDouble()) }, enabled = !same, variant = ButtonVariant.Accent, modifier = Modifier.fillMaxWidth())
        onRemove?.let { Button("Remove goal", onClick = it, variant = ButtonVariant.Ghost, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun BigStep(symbol: String, description: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(Resurge.shape.pill).background(Resurge.colors.surface).semantics { contentDescription = description },
        indication = LocalIndication.current,
    ) { Text(symbol, style = Resurge.type.headline) }
}
