package com.resurgeai.feature.weight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.WeighIn

/** What the Weight screen shows about a paired smart scale while it's open. */
internal sealed interface ScaleStrip {
    /** Quietly listening; nobody is on the scale. */
    data class Listening(val label: String) : ScaleStrip

    /** Someone is on the scale and it reads [kg] so far. */
    data class Weighing(val model: String, val kg: Double) : ScaleStrip

    /** Logged; [weighIn] is what Undo removes. */
    data class Saved(val model: String, val weighIn: WeighIn) : ScaleStrip

    /** Far from recent weigh-ins, so it waits for the person to say it's theirs. */
    data class Unusual(val model: String, val kg: Double) : ScaleStrip
}

internal class ScaleStripActions(
    val onOpen: () -> Unit = {},
    val onUndo: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/**
 * A small live line near the top of the Weight screen: a quiet caption while listening, a slim
 * card while someone is on the scale or a reading just arrived.
 */
@Composable
internal fun ScaleStripView(strip: ScaleStrip, units: WeightUnits, actions: ScaleStripActions) {
    AnimatedContent(
        targetState = strip,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "scale-strip",
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) { s ->
        when (s) {
            is ScaleStrip.Listening -> Listening(s.label, actions.onOpen)
            is ScaleStrip.Weighing -> Strip {
                Spinner(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                StripText("${units.format(s.kg)}…", "On ${s.model}")
            }
            is ScaleStrip.Saved -> Strip {
                Dot(Resurge.colors.accent, ResurgeIcons.Check)
                Spacer(Modifier.width(10.dp))
                StripText("Logged ${units.format(s.weighIn.kg)}", "From ${s.model}")
                StripAction("Undo", actions.onUndo)
            }
            is ScaleStrip.Unusual -> Strip {
                Dot(Resurge.colors.glow, ResurgeIcons.Scale)
                Spacer(Modifier.width(10.dp))
                StripText("${units.format(s.kg)}, was that you?", "From ${s.model}")
                StripAction("Not me", actions.onDismiss, emphasis = false)
                StripAction("Log it", actions.onConfirm)
            }
        }
    }
}

@Composable
private fun Listening(label: String, onOpen: () -> Unit) {
    UnstyledButton(
        onClick = onOpen,
        role = Role.Button,
        indication = LocalIndication.current,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.pill),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent))
            Spacer(Modifier.width(8.dp))
            Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Strip(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StripText(title: String, subtitle: String) {
    androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
        Text(title, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Dot(color: Color, icon: Int) {
    Box(Modifier.size(24.dp).clip(Resurge.shape.pill).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = if (color == Resurge.colors.glow) Resurge.colors.glowInk else Resurge.colors.brandInk, size = 14.dp)
    }
}

@Composable
private fun StripAction(label: String, onClick: () -> Unit, emphasis: Boolean = true) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        indication = LocalIndication.current,
        modifier = Modifier.clip(Resurge.shape.pill).then(if (emphasis) Modifier.background(Resurge.colors.accentMuted) else Modifier),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight), color = if (emphasis) Resurge.colors.brandInk else Resurge.colors.textMuted)
    }
}
