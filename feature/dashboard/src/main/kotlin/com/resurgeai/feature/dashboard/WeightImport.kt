package com.resurgeai.feature.dashboard

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge

/**
 * Weigh-ins now live in Resurge. People with weight history in Health Connect get one offer to bring
 * it in, and can choose to keep saving new weigh-ins there.
 */
@Composable
internal fun WeightImportCard(count: Int, importing: Boolean, onImport: (alsoWrite: Boolean) -> Unit, onDismiss: () -> Unit) {
    var alsoWrite by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Resurge.shape.sm).background(Resurge.colors.accentMuted), contentAlignment = Alignment.Center) {
                Icon(ResurgeIcons.Scale, contentDescription = null, tint = Resurge.colors.brandInk, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Bring in your weight history", style = Resurge.type.headline)
                Text(
                    "Health Connect has $count ${if (count == 1) "weigh-in" else "weigh-ins"}. Weight is now logged in Resurge.",
                    style = Resurge.type.caption,
                    color = Resurge.colors.textMuted,
                )
            }
        }
        UnstyledButton(
            onClick = { alsoWrite = !alsoWrite },
            role = Role.Checkbox,
            modifier = Modifier.fillMaxWidth().clip(Resurge.shape.sm).semantics { stateDescription = if (alsoWrite) "On" else "Off" },
            indication = LocalIndication.current,
        ) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp).clip(Resurge.shape.sm).background(if (alsoWrite) Resurge.colors.accent else Resurge.colors.surfaceMuted),
                    contentAlignment = Alignment.Center,
                ) { if (alsoWrite) Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.onAccent, size = 14.dp) }
                Spacer(Modifier.width(10.dp))
                Text("Also save new weigh-ins to Health Connect", style = Resurge.type.label, color = Resurge.colors.textMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Import $count", onClick = { onImport(alsoWrite) }, variant = ButtonVariant.Accent, loading = importing, enabled = !importing, modifier = Modifier.weight(1f))
            Button("Not now", onClick = onDismiss, variant = ButtonVariant.Secondary, enabled = !importing, modifier = Modifier.weight(1f))
        }
    }
}
