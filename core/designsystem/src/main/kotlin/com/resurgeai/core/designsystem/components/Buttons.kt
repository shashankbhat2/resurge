package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.LocalIndication
import com.composeunstyled.ProvideContentColor
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

enum class ButtonVariant { Primary, Accent, Secondary, Ghost, Danger }

@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) {
    val shape = Resurge.shape.pill
    val (container, content, border) = when (variant) {
        // Raised pill that follows the theme (white in light, charcoal in dark), like the navbar.
        ButtonVariant.Primary -> Triple(Resurge.colors.inverse, Resurge.colors.onInverse, Resurge.colors.border)
        // brandInk rather than the brand fill, so the label passes 4.5:1 in both themes.
        ButtonVariant.Accent -> Triple(Resurge.colors.brandInk, Resurge.colors.onAccent, Color.Transparent)
        ButtonVariant.Secondary -> Triple(Resurge.colors.surfaceMuted, Resurge.colors.text, Color.Transparent)
        ButtonVariant.Ghost -> Triple(Color.Transparent, Resurge.colors.text, Color.Transparent)
        ButtonVariant.Danger -> Triple(Resurge.colors.surface, Resurge.colors.danger, Resurge.colors.border)
    }
    val interactive = enabled && !loading

    UnstyledButton(
        onClick = onClick,
        enabled = interactive,
        modifier = modifier
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, border, shape),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        indication = LocalIndication.current,
    ) {
        ProvideContentColor(content) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier.alpha(if (loading) 0f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon?.let { Icon(it, contentDescription = null, size = 18.dp) }
                    Text(text, style = Resurge.type.label, color = content)
                    trailingIcon?.let { Icon(it, contentDescription = null, size = 18.dp) }
                }
                if (loading) Spinner(color = content, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun IconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Resurge.colors.text,
    enabled: Boolean = true,
    background: Color = Resurge.colors.surface,
) {
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(44.dp)
            .clip(Resurge.shape.pill)
            .background(background),
        indication = LocalIndication.current,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}
