package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * Floating pill navigation across the width: the active tab is a highlighted pill with icon + label,
 * inactive tabs are outline icons, and an optional [action] (Coach) is the last item, in the accent.
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
    // One pill across the width: tabs, then the action (e.g. Coach) as the last item.
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = BarGap)
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
                background = if (isSelected) Resurge.colors.navHighlight else Color.Transparent,
                tint = if (isSelected) Resurge.colors.onNavHighlight else Resurge.colors.navInactive,
                role = Role.Tab,
                selected = isSelected,
                onClick = { onSelect(item.key) },
            )
        }
        action?.let {
            NavCircle(
                icon = it.icon,
                label = it.label,
                background = Color.Transparent,
                tint = Resurge.colors.navHighlight,
                role = Role.Button,
                selected = false,
                onClick = onAction,
            )
        }
    }
}

/** Nav item: icon-only circle when inactive; a pill with icon + label when [selected]. */
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
    val bg = background
    val fg = tint
    UnstyledButton(
        onClick = onClick,
        role = role,
        modifier = Modifier
            .height(ItemSize)
            .widthIn(min = ItemSize)
            .clip(Resurge.shape.pill)
            .background(bg)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentPadding = PaddingValues(horizontal = if (selected) 16.dp else 0.dp),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg, size = 22.dp)
            if (selected) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = Resurge.type.label, color = fg, maxLines = 1)
                }
            }
        }
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

/** Space taken by [MainHeader] (status bar + bar); tab content should start at least this far down. */
val LocalHeaderInset = compositionLocalOf { 0.dp }

val MainHeaderHeight: Dp = 60.dp

/**
 * Permanent header for the main tabs: [title] on the left (or any [leading] content, e.g. a greeting),
 * [trailing] (profile photo) on the right. Its background fades downward so content scrolls softly
 * underneath, and it repaints the [TopShade] so the shade stays continuous across the header edge.
 */
@Composable
fun MainHeader(
    title: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    glow: Color = Resurge.colors.accent,
) {
    val bg = Resurge.colors.bg
    // Same alpha ramp for the background and for the header's copy of the shade: wherever the
    // background hides the shade behind it, this copy shows through by exactly that amount.
    val ramp = arrayOf(0f to 1f, 0.6f to 0.9f, 1f to 0f)
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(*ramp.map { (p, a) -> p to bg.copy(alpha = a) }.toTypedArray())),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clipToBounds()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(*ramp.map { (p, a) -> p to Color.Black.copy(alpha = a) }.toTypedArray()),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) { TopShade(glow) }
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(MainHeaderHeight)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (leading != null) leading()
                else Text(title.orEmpty(), style = Resurge.type.title, maxLines = 1)
            }
            trailing()
        }
    }
}
