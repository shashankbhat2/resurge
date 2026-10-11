package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledTextField
import com.resurgeai.core.designsystem.theme.Resurge

val AskBarHeight: Dp = 56.dp

/**
 * "Ask your coach" input that sits above the navbar on the main tabs: the persona's mascot on the
 * left, a single-line field, and a send button. Submitting hands the text to [onSubmit] (which opens
 * the coach chat and continues the conversation).
 */
@Composable
fun AskBar(
    commander: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Tapping the mascot opens the coach without a message. */
    onOpenCoach: (() -> Unit)? = null,
) {
    val state = rememberTextFieldState()
    val submit = {
        val text = state.text.toString().trim()
        if (text.isNotEmpty()) {
            onSubmit(text)
            state.clearText()
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(AskBarHeight)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.navBar)
            .border(1.dp, Resurge.colors.border, Resurge.shape.pill)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onOpenCoach != null) {
            UnstyledButton(
                onClick = onOpenCoach,
                role = Role.Button,
                modifier = Modifier
                    .size(40.dp)
                    .clip(Resurge.shape.pill)
                    .semantics { contentDescription = "Open coach" },
                indication = LocalIndication.current,
            ) { CoachMascot(commander = commander, size = 28.dp) }
        } else {
            CoachMascot(commander = commander, size = 28.dp, modifier = Modifier.padding(6.dp))
        }
        Spacer(Modifier.width(6.dp))
        UnstyledTextField(
            state = state,
            modifier = Modifier.weight(1f),
            textStyle = Resurge.type.body,
            textColor = Resurge.colors.text,
            cursorBrush = SolidColor(Resurge.colors.accent),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            onKeyboardAction = { submit() },
            accessibilityLabel = "Ask your coach",
        ) {
            TextInput(placeholder = { Text("Ask your coach…", style = Resurge.type.body, color = Resurge.colors.textSubtle) })
        }
        val canSend = state.text.isNotBlank()
        UnstyledButton(
            onClick = submit,
            enabled = canSend,
            role = Role.Button,
            modifier = Modifier
                .size(44.dp)
                .clip(Resurge.shape.pill)
                .background(if (canSend) Resurge.colors.accent else Resurge.colors.surfaceMuted),
            indication = LocalIndication.current,
        ) {
            Icon(ResurgeIcons.ArrowUp, contentDescription = "Send", tint = if (canSend) Resurge.colors.onAccent else Resurge.colors.textSubtle)
        }
    }
}
