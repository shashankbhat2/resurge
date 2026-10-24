package com.resurgeai.feature.progress

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge

/** A bottom sheet over a scrim. Tapping outside closes it unless [dismissible] is false. */
@Composable
internal fun ProgressSheet(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { if (dismissible) onDismiss() }.imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Resurge.colors.surface)
                    .clickable(interactionSource = null, indication = null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Resurge.shape.pill).background(Resurge.colors.border))
                content()
            }
        }
    }
}

/** A round icon button for the top bar. */
@Composable
internal fun RoundButton(icon: Int, label: String, accent: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier
            .size(40.dp)
            .clip(Resurge.shape.pill)
            .background(if (accent) Resurge.colors.accent else Resurge.colors.surface)
            .semantics { contentDescription = label },
        indication = LocalIndication.current,
    ) { Icon(icon, contentDescription = null, tint = if (accent) Resurge.colors.onAccent else Resurge.colors.text, size = 20.dp) }
}

/** A tinted round icon over a title and body, centred: for empty and locked states. */
@Composable
internal fun Explainer(icon: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(56.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Resurge.colors.text, size = 26.dp)
        }
        Spacer(Modifier.size(10.dp))
        Text(title, style = Resurge.type.title, textAlign = TextAlign.Center)
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted, textAlign = TextAlign.Center)
    }
}
