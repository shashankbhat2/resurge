package com.resurgeai.feature.coach

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledTextField
import com.resurgeai.core.designsystem.components.CoachBadge
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.PlanChangeOffer
import com.resurgeai.core.model.TurnRole

@Composable
fun CoachRoute(
    onBack: () -> Unit,
    onOpenChats: () -> Unit = {},
    onOpenPlanChange: (String) -> Unit = {},
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoachScreen(
        state = state,
        onBack = onBack,
        onSend = viewModel::send,
        onModelChange = viewModel::selectModel,
        onOpenChats = onOpenChats,
        onNewChat = viewModel::newChat,
        onDismissError = viewModel::dismissError,
        onAcceptOffer = { offer -> onOpenPlanChange(offer.change) },
        onDismissOffer = viewModel::dismissOffer,
    )
}

/**
 * The coach (full screen, Resurge Mono): who it is and what it sees, then the conversation (what
 * it's about first, the person's messages in grey bubbles, the coach's as plain text with any plan
 * change as buttons under it), quick questions, and the composer.
 */
@Composable
fun CoachScreen(
    state: CoachUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onModelChange: (CoachModel) -> Unit = {},
    onOpenChats: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onAcceptOffer: (PlanChangeOffer) -> Unit = {},
    onDismissOffer: (String) -> Unit = {},
) {
    val input = rememberTextFieldState()
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg).imePadding()) {
        Header(onBack, onOpenChats)
        Box(Modifier.weight(1f)) {
            if (state.visibleTurns.isEmpty() && state.pending == null) {
                EmptyChat(state.aboutTitle)
            } else {
                Messages(state, onAcceptOffer, onDismissOffer)
            }
        }
        state.error?.let { ErrorBanner(it, onDismissError) }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.sending) {
                Suggestions(state.suggestions) { q ->
                    onSend(q)
                }
            }
            Composer(input, enabled = !state.sending, model = state.model, onModelChange = onModelChange) {
                onSend(input.text.toString())
                input.clearText()
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, onOpenChats: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.width(8.dp))
            CoachBadge(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Coach", style = Resurge.type.headline)
                Text("Sees your plan, goal and health data", style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
            }
            IconButton(ResurgeIcons.History, contentDescription = "Insights and past chats", onClick = onOpenChats)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
    }
}

/** Fast / Smart under the input: which model replies. */
@Composable
private fun ModelSwitch(model: CoachModel, onChange: (CoachModel) -> Unit) {
    Row(
        Modifier.clip(Resurge.shape.pill).background(Resurge.colors.border).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CoachModel.entries.forEach { option ->
            val on = option == model
            UnstyledButton(
                onClick = { onChange(option) },
                role = Role.RadioButton,
                modifier = Modifier
                    .semantics { selected = on }
                    .clip(Resurge.shape.pill)
                    .then(if (on) Modifier.background(Resurge.colors.strong) else Modifier),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                indication = LocalIndication.current,
            ) { Text(option.label, style = Resurge.type.caption, color = if (on) Resurge.colors.onStrong else Resurge.colors.textMuted) }
        }
    }
}

@Composable
private fun AboutCard(title: String) {
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).border(1.dp, Resurge.colors.border, Resurge.shape.md).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("About the insight", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        Text(title, style = Resurge.type.label, color = Resurge.colors.text)
    }
}

@Composable
private fun Messages(state: CoachUiState, onAccept: (PlanChangeOffer) -> Unit, onDismiss: (String) -> Unit) {
    val listState = rememberLazyListState()
    val pending = state.pending
    val about = state.aboutTitle
    val count = state.visibleTurns.size + (if (pending != null) 2 else 0) + (if (about != null) 1 else 0)
    LaunchedEffect(count, pending?.streamedText?.length) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    // The last coach reply is the only one whose offer can still be taken up.
    val lastCoach = state.visibleTurns.lastOrNull { it.role == TurnRole.COACH }?.id
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        about?.let { item(key = "about") { AboutCard(it) } }
        items(state.visibleTurns, key = { it.id }) { turn ->
            when (turn.role) {
                TurnRole.USER -> UserBubble(turn.text)
                TurnRole.COACH -> CoachReply(
                    turn,
                    offer = turn.offer?.takeIf { turn.id == lastCoach && pending == null && turn.id !in state.dismissedOffers },
                    onAccept = onAccept,
                    onDismiss = { onDismiss(turn.id) },
                )
            }
        }
        if (pending != null) {
            item(key = "pending-user") { UserBubble(pending.userText) }
            item(key = "pending-reply") {
                val text = pending.streamedText.ifBlank { if (pending.usingTool != null) toolLabel(pending.usingTool) else "Thinking…" }
                Text(text.cleanMarkdown(), style = Resurge.type.body, color = if (pending.streamedText.isBlank()) Resurge.colors.textSubtle else Resurge.colors.text)
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                .background(Resurge.colors.border)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) { Text(text, style = Resurge.type.body, color = Resurge.colors.text) }
    }
}

/** The coach's reply as plain text, and a plan change under it as "Update plan" / "Not now". */
@Composable
private fun CoachReply(turn: CoachTurn, offer: PlanChangeOffer?, onAccept: (PlanChangeOffer) -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth(0.94f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            turn.text.cleanMarkdown(),
            style = Resurge.type.body,
            color = if (turn.notice) Resurge.colors.textSubtle else Resurge.colors.text,
        )
        if (offer != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(offer.label, primary = true) { onAccept(offer) }
                PillButton("Not now", primary = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(Resurge.shape.pill)
            .then(if (primary) Modifier.background(Resurge.colors.strong) else Modifier.border(1.dp, Resurge.colors.borderStrong, Resurge.shape.pill)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        indication = LocalIndication.current,
    ) { Text(label, style = Resurge.type.label, color = if (primary) Resurge.colors.onStrong else Resurge.colors.textMuted) }
}

@Composable
private fun EmptyChat(about: String?) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        about?.let { AboutCard(it) }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoachBadge(size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Text(if (about != null) "Ask about this insight" else "Ask your coach", style = Resurge.type.title)
            Spacer(Modifier.height(6.dp))
            Text(
                "It knows your goal, your plan, your streak and today’s Recovery, Effort and Fuel.",
                style = Resurge.type.body,
                color = Resurge.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun Suggestions(items: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { q ->
            UnstyledButton(
                onClick = { onPick(q) },
                role = Role.Button,
                modifier = Modifier.clip(Resurge.shape.pill).border(1.dp, Resurge.colors.borderStrong, Resurge.shape.pill),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                indication = LocalIndication.current,
            ) { Text(q, style = Resurge.type.label, color = Resurge.colors.text, maxLines = 1) }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    UnstyledButton(
        onClick = onDismiss,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .border(1.dp, Resurge.colors.down, Resurge.shape.md),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
        indication = LocalIndication.current,
    ) { Text(message, style = Resurge.type.label, color = Resurge.colors.down) }
}

/** The input with a white send button, and the Fast / Smart switch under it, on one muted card. */
@Composable
private fun Composer(state: TextFieldState, enabled: Boolean, model: CoachModel, onModelChange: (CoachModel) -> Unit, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Resurge.colors.surfaceMuted)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 10.dp),
    ) {
        ComposerInput(state, enabled, onSend)
        Spacer(Modifier.height(4.dp))
        ModelSwitch(model, onModelChange)
    }
}

@Composable
private fun ComposerInput(state: TextFieldState, enabled: Boolean, onSend: () -> Unit) {
    val canSend = enabled && state.text.isNotBlank()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        UnstyledTextField(
            state = state,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            textStyle = Resurge.type.body,
            textColor = Resurge.colors.text,
            cursorBrush = SolidColor(Resurge.colors.accent),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            onKeyboardAction = { if (canSend) onSend() },
            accessibilityLabel = "Message your coach",
        ) {
            TextInput(placeholder = { Text("Ask your coach", style = Resurge.type.body, color = Resurge.colors.textSubtle) })
        }
        Spacer(Modifier.width(8.dp))
        UnstyledButton(
            onClick = onSend,
            enabled = canSend,
            role = Role.Button,
            modifier = Modifier
                .size(40.dp)
                .clip(Resurge.shape.pill)
                .background(if (canSend) Resurge.colors.strong else Resurge.colors.border),
            indication = LocalIndication.current,
        ) {
            Icon(ResurgeIcons.ArrowUp, contentDescription = "Send", tint = if (canSend) Resurge.colors.onStrong else Resurge.colors.textSubtle)
        }
    }
}

private fun toolLabel(name: String): String = when (name) {
    "get_meals" -> "Looking at your meals…"
    "get_workout_logs", "get_activities" -> "Looking at your workouts…"
    "get_days_and_scores" -> "Looking at your days and scores…"
    "suggest_plan_change" -> "Drafting a plan change…"
    else -> "Looking at your data…"
}

/** Light cleanup so replies read well as plain text: bullets and bold markers. */
private fun String.cleanMarkdown(): String = lines().joinToString("\n") { line ->
    val trimmed = line.trimStart()
    val bullet = if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) "•  " + trimmed.drop(2) else line
    bullet.replace("**", "").replace(Regex("^#{1,6}\\s*"), "")
}
