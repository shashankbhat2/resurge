package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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

/** Space the navbar takes at the bottom (bar plus system navigation inset); scrolling tab content should end with at least this much space. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

private val BarHeight = 64.dp
private val BarRadius = 28.dp
private val ItemSize = 48.dp

private val BarPaddingY = 8.dp

/** Height of the navbar above the system navigation-bar inset. */
val FloatingNavBarHeight: Dp = BarHeight + BarPaddingY * 2

/**
 * Navigation bar (Resurge Tide), docked to the bottom edge: `navBar` with `radius-lg` top corners
 * and a soft shadow, running behind the system navigation bar. Icon-only tabs are spread evenly,
 * and the selected tab is a 48dp `navActive` circle holding its filled icon. An optional [center]
 * item (e.g. the Coach) sits between the two halves of [items].
 */
@Composable
fun <T> FloatingNavBar(
    items: List<NavItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    center: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(topStart = BarRadius, topEnd = BarRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(shape)
            .background(Resurge.colors.navBar)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = BarPaddingY)
            .height(BarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        val half = (items.size + 1) / 2
        items.forEachIndexed { index, item ->
            if (index == half) center?.invoke()
            val isSelected = item.key == selected
            NavTab(
                icon = if (isSelected) item.selectedIcon else item.icon,
                label = item.label,
                selected = isSelected,
                onClick = { onSelect(item.key) },
            )
        }
        if (half >= items.size) center?.invoke()
    }
}

/** One tab: an outline icon, or when [selected] a filled icon in a `navActive` circle. */
@Composable
private fun NavTab(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) Resurge.colors.navActive else Color.Transparent, tween(180), label = "tab")
    val fg by animateColorAsState(if (selected) Resurge.colors.navOnActive else Resurge.colors.navInactive, tween(180), label = "tabIcon")
    UnstyledButton(
        onClick = onClick,
        role = Role.Tab,
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
        Icon(icon, contentDescription = null, tint = fg, size = 24.dp)
    }
}

/**
 * The coach in the middle of [FloatingNavBar]: the persona's flat blob (Friend or Commander) in a
 * 48dp tap target. It keeps its own colours; tapping starts a new chat with the coach.
 */
@Composable
fun NavCoach(commander: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = modifier
            .size(ItemSize)
            .clip(Resurge.shape.pill)
            .semantics { contentDescription = "Coach" },
        indication = LocalIndication.current,
    ) {
        CoachMascot(commander = commander, size = 32.dp)
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
 * underneath.
 */
@Composable
fun MainHeader(
    title: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val bg = Resurge.colors.bg
    val ramp = arrayOf(0f to 1f, 0.6f to 0.9f, 1f to 0f)
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(*ramp.map { (p, a) -> p to bg.copy(alpha = a) }.toTypedArray())),
    ) {
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
