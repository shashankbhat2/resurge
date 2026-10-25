package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Column
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.theme.Resurge

data class NavItem<T>(
    val key: T,
    val label: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int = icon,
)

/** Space the navbar takes at the bottom (bar plus system navigation inset, and room for the Coach button); scrolling tab content should end with at least this much space. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

private val BarHeight = 80.dp
private val TabHeight = 56.dp
private val CoachSize = 56.dp
private val CoachMargin = 16.dp

/** Height of the navbar above the system navigation-bar inset. */
val NavBarHeight: Dp = BarHeight

/** How far the Coach button sticks up above the navbar, for content padding. */
val CoachButtonClearance: Dp = CoachSize + CoachMargin

/**
 * Navigation bar (Resurge Mono): docked to the bottom edge, full width, icons only. The selected
 * tab sits on a lighter pill; tabs are a full 56dp tall so they're easy to hit, and each is announced
 * by its label. An optional [center] item (Add) sits between the two halves.
 */
@Composable
fun <T> NavBar(
    items: List<NavItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    center: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Resurge.colors.navBar)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight - 1.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val half = (items.size + 1) / 2
            items.forEachIndexed { index, item ->
                if (index == half) center?.invoke(this)
                val isSelected = item.key == selected
                NavTab(
                    icon = if (isSelected) item.selectedIcon else item.icon,
                    label = item.label,
                    selected = isSelected,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (half >= items.size) center?.invoke(this)
        }
    }
}

/** One tab: its icon, on a lighter pill when [selected]. */
@Composable
private fun NavTab(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(if (selected) Resurge.colors.borderStrong else Color.Transparent, tween(180), label = "tab")
    val fg by animateColorAsState(if (selected) Resurge.colors.text else Resurge.colors.navInactive, tween(180), label = "tabIcon")
    Box(modifier, contentAlignment = Alignment.Center) {
        UnstyledButton(
            onClick = onClick,
            role = Role.Tab,
            modifier = Modifier
                .fillMaxWidth()
                .height(TabHeight)
                .padding(horizontal = 2.dp)
                .clip(Resurge.shape.pill)
                .background(bg)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                },
            indication = LocalIndication.current,
        ) { Icon(icon, contentDescription = null, tint = fg, size = 28.dp) }
    }
}

/** Add, in the middle of [NavBar]: a plus drawn like an unselected tab. It opens quick logging. */
@Composable
fun RowScope.NavAdd(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.weight(1f), contentAlignment = Alignment.Center) {
        UnstyledButton(
            onClick = onClick,
            role = Role.Button,
            modifier = Modifier
                .fillMaxWidth()
                .height(TabHeight)
                .padding(horizontal = 2.dp)
                .clip(Resurge.shape.pill)
                .semantics { contentDescription = "Add" },
            indication = LocalIndication.current,
        ) { Icon(ResurgeIcons.Plus, contentDescription = null, tint = Resurge.colors.navInactive, size = 28.dp) }
    }
}

/**
 * The coach's mark: a sparkle in a solid circle (white on black, black on white). Used for the Coach
 * button and beside its notes; there's no character, so it reads the same in every persona.
 */
@Composable
fun CoachBadge(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(Resurge.shape.pill).background(Resurge.colors.strong),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.onStrong, size = size * 0.55f)
    }
}

/**
 * The Coach, floating over the right end of [NavBar]: a 56dp [CoachBadge] with a shadow. Place it
 * at the bottom end of the screen; it keeps clear of the bar and the system navigation inset.
 * Tapping starts a new chat.
 */
@Composable
fun CoachButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(end = CoachMargin, bottom = BarHeight + CoachMargin)
            .size(CoachSize)
            .shadow(16.dp, Resurge.shape.pill, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(Resurge.shape.pill)
            .semantics { contentDescription = "Coach" },
        indication = LocalIndication.current,
    ) { CoachBadge(size = CoachSize) }
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
 * [trailing] (profile photo) on the right. Below the bar it extends by [fade], over which its
 * background fades to clear, so content scrolls softly underneath (pair it with a backdrop blur).
 */
@Composable
fun MainHeader(
    title: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    fade: Dp = 40.dp,
) {
    val bg = Resurge.colors.bg
    val ramp = arrayOf(0f to 0.92f, 0.55f to 0.75f, 1f to 0f)
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(*ramp.map { (p, a) -> p to bg.copy(alpha = a) }.toTypedArray())),
    ) {
        Column {
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
            Spacer(Modifier.height(fade))
        }
    }
}
