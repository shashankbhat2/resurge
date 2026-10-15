package com.resurgeai.core.designsystem.components

import androidx.compose.ui.unit.Dp
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge

/**
 * The overview cards that lead Today (tide), Fitness (move week) and Food (fuel) share one shape:
 * a body of this height, a hairline, then a two-cell footer of [OverviewFooterHeight].
 */
val OverviewBodyHeight: Dp = 200.dp
val OverviewFooterHeight: Dp = 68.dp

/** Uppercase section label (`overline`). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = Resurge.colors.textMuted) {
    Text(text.uppercase(), style = Resurge.type.overline, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Label above a group of cards on a tab, with an optional trailing note (e.g. "3 of 5 done"). */
@Composable
fun SectionLabel(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f))
        trailing?.let { Text(it, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle) }
    }
}

/**
 * One cell of a card footer: an icon in a 36dp circle, a value in Bricolage and a caption label.
 * Footers put two side by side, end to end, split by a hairline. Tappable when [onClick] is set.
 */
@Composable
fun StatCell(
    @DrawableRes icon: Int?,
    iconTint: Color,
    iconBackground: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    description: String = "$value, $label",
    onClick: (() -> Unit)? = null,
    /** Draws in place of [icon] (e.g. a [ProgressFlame]). */
    iconContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxHeight()
            .then(
                if (onClick != null) {
                    // Not focusable, so the cell doesn't stay highlighted after what it opened closes.
                    Modifier.focusProperties { canFocus = false }.clickable(onClickLabel = "Show details", onClick = onClick)
                } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(Resurge.shape.pill).background(iconBackground), contentAlignment = Alignment.Center) {
            when {
                iconContent != null -> iconContent()
                icon != null -> Icon(icon, contentDescription = null, tint = iconTint, size = 20.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(value, style = Resurge.type.metric.copy(fontSize = 18.sp, lineHeight = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Full-height hairline between two [StatCell]s. */
@Composable
fun StatDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Resurge.colors.border))
}
