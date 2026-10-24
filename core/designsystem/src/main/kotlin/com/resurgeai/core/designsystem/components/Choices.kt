package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

/**
 * A row of equal-width choices (8 wk / 12 wk / 16 wk). The selected one gets the accent outline over
 * the accent tint, as every Mono selection does; the rest are outlined in grey.
 */
@Composable
fun <T> OptionChips(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            ChoiceBox(
                text = label(option),
                selected = isSelected,
                role = Role.RadioButton,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Several on/off squares in a row, like the days of the week. [labels] are what's drawn ("M"),
 * [descriptions] what's read out ("Monday").
 */
@Composable
fun ToggleSquares(
    labels: List<String>,
    descriptions: List<String>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, text ->
            ChoiceBox(
                text = text,
                selected = i in selected,
                role = Role.Checkbox,
                onClick = { onToggle(i) },
                description = descriptions.getOrNull(i),
                modifier = Modifier.weight(1f),
                minHeight = 56,
            )
        }
    }
}

@Composable
private fun ChoiceBox(
    text: String,
    selected: Boolean,
    role: Role,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    minHeight: Int = 48,
) {
    val shape = Resurge.shape.sm
    UnstyledButton(
        onClick = onClick,
        role = role,
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .semantics {
                this.selected = selected
                description?.let { contentDescription = it }
            }
            .clip(shape)
            .background(if (selected) Resurge.colors.accentTint else Resurge.colors.bg, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Resurge.colors.accent else Resurge.colors.borderStrong, shape),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
        indication = LocalIndication.current,
    ) {
        Text(
            text,
            style = Resurge.type.label.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Resurge.colors.text else Resurge.colors.textMuted,
        )
    }
}

/** A full-width option with its label and a short description; the selected one gets the accent outline. */
@Composable
fun OptionRow(label: String, description: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = Resurge.shape.sm
    UnstyledButton(
        onClick = onClick,
        role = Role.RadioButton,
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clip(shape)
            .background(if (selected) Resurge.colors.accentTint else Resurge.colors.bg, shape)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) Resurge.colors.accent else Resurge.colors.borderStrong, shape),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        indication = LocalIndication.current,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Resurge.type.label, color = if (selected) Resurge.colors.text else Resurge.colors.textMuted, modifier = Modifier.weight(1f))
            Text(description, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
    }
}
