package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

data class NavItem<T>(
    val key: T,
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int = icon,
)

/** Height of the floating bar plus its gap; scrolling tab content should end with at least this much space. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

private val BarHeight = 64.dp
private val ItemSize = 48.dp
private val BarGap = 12.dp

/** Total space the floating bar occupies above the navigation-bar inset. */
val FloatingNavBarHeight: Dp = BarHeight + BarGap

/**
 * Floating pill navigation: dark rounded bar, the active tab drawn as an inverted circle with a
 * filled icon, inactive tabs as outline icons. An optional [action] (e.g. Scan) sits at the end
 * in the accent colour.
 */
@Composable
fun <T> FloatingNavBar(
    items: List<NavItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    action: NavItem<Unit>? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 20.dp, end = 20.dp, bottom = BarGap)
            .fillMaxWidth()
            .height(BarHeight)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.navBar)
            .border(1.dp, Resurge.colors.border, Resurge.shape.pill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            val isSelected = item.key == selected
            NavCircle(
                icon = if (isSelected) item.selectedIcon else item.icon,
                label = item.label,
                background = if (isSelected) Resurge.colors.strong else Color.Transparent,
                tint = if (isSelected) Resurge.colors.onStrong else Resurge.colors.navInactive,
                role = Role.Tab,
                selected = isSelected,
                onClick = { onSelect(item.key) },
            )
        }
        action?.let {
            NavCircle(
                icon = it.icon,
                label = it.label,
                background = Resurge.colors.accent,
                tint = Resurge.colors.onAccent,
                role = Role.Button,
                selected = false,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun NavCircle(
    @DrawableRes icon: Int,
    label: String,
    background: Color,
    tint: Color,
    role: Role,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(background, tween(180), label = "nav-bg")
    val fg by animateColorAsState(tint, tween(180), label = "nav-fg")
    UnstyledButton(
        onClick = onClick,
        role = role,
        modifier = Modifier
            .size(ItemSize)
            .clip(Resurge.shape.pill)
            .background(bg)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        indication = LocalIndication.current,
    ) {
        Icon(icon, contentDescription = null, tint = fg, size = 22.dp)
    }
}

/** Simple top bar: optional back button, title, optional trailing action. Applies status bar insets. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (onBack != null) {
            IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            title,
            style = Resurge.type.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        trailing?.invoke()
    }
}
