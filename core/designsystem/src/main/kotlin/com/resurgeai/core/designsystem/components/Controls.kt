package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.UnstyledTextField
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import java.time.LocalTime

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

/** On/off switch built on Compose Unstyled's ToggleSwitch. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accessibilityLabel: String? = null,
) {
    UnstyledSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        accessibilityLabel = accessibilityLabel,
    ) {
        Track(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(Resurge.shape.pill)
                .background(if (checked) Resurge.colors.accent else Resurge.colors.surfaceMuted)
                .border(1.dp, if (checked) Resurge.colors.accent else Resurge.colors.border, Resurge.shape.pill)
                .padding(4.dp),
        ) {
            Thumb(
                Modifier
                    .size(24.dp)
                    .clip(Resurge.shape.pill)
                    .background(if (checked) Resurge.colors.onAccent else Resurge.colors.textSubtle),
                animationSpec = tween(150),
            )
        }
    }
}

/** Clock time with − / + buttons in [stepMinutes] steps; wraps around midnight. */
@Composable
fun TimeStepper(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    stepMinutes: Long = 30,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled = true) { onTimeChange(time.minusMinutes(stepMinutes)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time.format(clockFormatter()), style = Resurge.type.display, textAlign = TextAlign.Center)
            Text(caption, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        StepButton("+", enabled = true) { onTimeChange(time.plusMinutes(stepMinutes)) }
    }
}

/** Single-line text input in a rounded field. */
@Composable
fun InputField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 60,
    onDone: (() -> Unit)? = null,
) {
    UnstyledTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surfaceMuted)
            .border(1.dp, Resurge.colors.border, Resurge.shape.md)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = Resurge.type.body,
        textColor = Resurge.colors.text,
        cursorBrush = SolidColor(Resurge.colors.accent),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLength(maxLength),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone?.invoke() },
        accessibilityLabel = placeholder,
    ) {
        TextInput(placeholder = { Text(placeholder, style = Resurge.type.body, color = Resurge.colors.textSubtle) })
    }
}
