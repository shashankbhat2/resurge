package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

data class SegmentOption<T>(val value: T, val label: String, @DrawableRes val icon: Int? = null)

/** Single-choice pill switcher (e.g. Day / Week / Month, System / Light / Dark). */
@Composable
fun <T> SegmentedControl(
    options: List<SegmentOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.inverse)
            .border(1.dp, Resurge.colors.border.copy(alpha = 0.6f), Resurge.shape.pill)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            UnstyledButton(
                onClick = { onSelect(option.value) },
                role = Role.RadioButton,
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = isSelected }
                    .clip(Resurge.shape.pill)
                    .background(if (isSelected) Resurge.colors.strong else Color.Transparent),
                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp),
                indication = LocalIndication.current,
            ) {
                val color = if (isSelected) Resurge.colors.onStrong else Resurge.colors.onInverseMuted
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    option.icon?.let { Icon(it, contentDescription = null, tint = color, size = 16.dp) }
                    Text(option.label, style = Resurge.type.label, color = color)
                }
            }
        }
    }
}

/** Selectable card with icon, title, description and a check mark — used for multi-select choices. */
@Composable
fun ChoiceCard(
    title: String,
    description: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Resurge.colors.accent,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
        onClick = onClick,
        selected = selected,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                IconBadge(it, tint = iconTint)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = Resurge.type.headline)
                description?.let {
                    Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted)
                }
            }
            CheckMark(selected)
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    val shape = Resurge.shape.pill
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) Resurge.colors.strong else Color.Transparent)
            .border(1.5.dp, if (checked) Resurge.colors.strong else Resurge.colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onStrong, size = 14.dp)
    }
}

/** Numeric value with − / + buttons, for targets like calories and water. */
@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    step: Int,
    range: IntRange,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", enabled = value - step >= range.first) { onValueChange((value - step).coerceIn(range)) }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("%,d".format(value), style = Resurge.type.display, textAlign = TextAlign.Center)
            Text(unit, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        StepButton("+", enabled = value + step <= range.last) { onValueChange((value + step).coerceIn(range)) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = Resurge.shape.pill
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .clip(shape)
            .background(Resurge.colors.surfaceMuted)
            .border(1.dp, Resurge.colors.border, shape),
        indication = LocalIndication.current,
    ) {
        Text(
            symbol,
            style = Resurge.type.title,
            color = if (enabled) Resurge.colors.text else Resurge.colors.textSubtle,
        )
    }
}

/** Thin step indicator for multi-step flows. */
@Composable
fun StepIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Resurge.shape.pill)
                    .background(if (index <= current) Resurge.colors.strong else Resurge.colors.border),
            )
        }
    }
}
