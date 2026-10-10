package com.resurgeai.feature.coach

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.TextInput
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledTextField
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.components.TopShade
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachModel
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.CoachTurn
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.TurnRole
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Height reserved at the bottom of scrolling content for the floating composer. */
private val ComposerSpace = 132.dp

@Composable
fun CoachRoute(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoachScreen(
        state = state,
        onBack = onBack,
        onTabChange = viewModel::selectTab,
        onSend = viewModel::send,
        onModelChange = viewModel::selectModel,
        onToggleHistory = viewModel::toggleHistory,
        onOpenThread = viewModel::openThread,
        onNewChat = viewModel::newChat,
        onDismissError = viewModel::dismissError,
    )
}

/**
 * Full-screen coach (no navbar, no main header): Insights feed and Chat as two tabs, with a
 * floating composer (Fast / Smart switch) pinned above the keyboard.
 */
@Composable
fun CoachScreen(
    state: CoachUiState,
    onBack: () -> Unit,
    onTabChange: (CoachTab) -> Unit,
    onSend: (String) -> Unit,
    onModelChange: (CoachModel) -> Unit = {},
    onToggleHistory: () -> Unit = {},
    onOpenThread: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onDismissError: () -> Unit = {},
) {
    val input = rememberTextFieldState()
    Box(Modifier.fillMaxSize()) {
        TopShade()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
                Spacer(Modifier.width(12.dp))
                SegmentedControl(
                    options = CoachTab.entries.map { SegmentOption(it, it.label) },
                    selected = state.tab,
                    onSelect = onTabChange,
                    modifier = Modifier.weight(1f),
                )
            }
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.weight(1f),
                label = "coach-tab",
            ) { tab ->
                when (tab) {
                    CoachTab.INSIGHTS -> InsightsFeed(state)
                    CoachTab.CHAT -> ChatPane(
                        state = state,
                        onToggleHistory = onToggleHistory,
                        onOpenThread = onOpenThread,
                        onNewChat = onNewChat,
                        onSuggestion = { input.edit { replace(0, length, it) } },
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            state.error?.let { ErrorBanner(it, onDismissError) }
            Composer(
                state = input,
                enabled = !state.sending,
                model = state.model,
                onModelChange = onModelChange,
                onSend = {
                    onSend(input.text.toString())
                    input.clearText()
                },
            )
        }
    }
}

// --- Insights ---

@Composable
private fun InsightsFeed(state: CoachUiState) {
    when {
        !state.insightsLoaded -> InsightsSkeleton()
        state.insights.isEmpty() -> EmptyState(
            title = "No insights yet",
            body = "Finish a workout or a night of sleep and your coach will write about it. Every Saturday evening you'll also get a review of your week.",
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = ComposerSpace + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.insights, key = { it.id }) {
                if (it.kind == InsightKind.WEEKLY) WeeklyCard(it) else InsightCard(it)
            }
        }
    }
}

@Composable
private fun InsightCard(insight: CoachInsight) {
    val tint = categoryColor(insight.category, insight.kind)
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(tint))
            Spacer(Modifier.width(8.dp))
            Text(
                categoryLabel(insight.category, insight.kind),
                style = Resurge.type.label,
                color = Resurge.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(whenLabel(insight.createdAt), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Spacer(Modifier.height(14.dp))
        Text(insight.title, style = Resurge.type.headline)
        Spacer(Modifier.height(8.dp))
        Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted)
        if (insight.highlights.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                insight.highlights.forEach { h ->
                    Column(
                        Modifier
                            .clip(Resurge.shape.md)
                            .background(Resurge.colors.surfaceMuted)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(h.value, style = Resurge.type.label, maxLines = 1)
                        Text(h.label, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1)
                    }
                }
            }
        }
        insight.suggestion?.let {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.accent, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = Resurge.type.label)
            }
        }
    }
}

@Composable
private fun WeeklyCard(insight: CoachInsight) {
    val weekly = insight.weekly
    Card(Modifier.fillMaxWidth(), inverse = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.accent, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Text("Weekly review", style = Resurge.type.label, color = Resurge.colors.onInverseMuted, modifier = Modifier.weight(1f))
            Text(whenLabel(insight.createdAt), style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
        }
        Spacer(Modifier.height(14.dp))
        Text(insight.title, style = Resurge.type.title, color = Resurge.colors.onInverse)
        if (weekly != null) {
            Spacer(Modifier.height(10.dp))
            Text(weekly.summary, style = Resurge.type.body, color = Resurge.colors.onInverseMuted)
            if (weekly.areas.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                weekly.areas.forEach { area ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            when (area.trend) { "up" -> "↑"; "down" -> "↓"; else -> "→" },
                            style = Resurge.type.label,
                            color = Resurge.colors.onInverse,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            "${area.area.replaceFirstChar { it.uppercase() }} · ${area.note}",
                            style = Resurge.type.label,
                            color = Resurge.colors.onInverse,
                        )
                    }
                }
            }
            if (weekly.wins.isNotEmpty()) BulletSection("Wins", weekly.wins)
            if (weekly.focus.isNotEmpty()) BulletSection("Next week", weekly.focus)
        }
    }
}

@Composable
private fun BulletSection(title: String, items: List<String>) {
    Spacer(Modifier.height(16.dp))
    Text(title.uppercase(), style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
    Spacer(Modifier.height(6.dp))
    items.forEach { Text("•  $it", style = Resurge.type.body, color = Resurge.colors.onInverse, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
private fun InsightsSkeleton() {
    SkeletonGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Card(Modifier.fillMaxWidth()) {
                    SkeletonLine(90.dp)
                    Spacer(Modifier.height(14.dp))
                    SkeletonLine(200.dp, 18.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonLine(280.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(240.dp)
                }
            }
        }
    }
}

@Composable
private fun categoryColor(category: String?, kind: InsightKind): Color = when {
    kind == InsightKind.SLEEP || category == "SLEEP" -> Resurge.colors.sleep
    category == "HEART" -> Resurge.colors.heart
    category == "NUTRITION" -> Resurge.colors.energy
    category == "RECOVERY" -> Resurge.colors.water
    else -> Resurge.colors.activity
}

private fun categoryLabel(category: String?, kind: InsightKind): String = when {
    kind == InsightKind.SLEEP -> "Sleep"
    category != null -> category.lowercase().replaceFirstChar { it.uppercase() }
    else -> "Activity"
}

@Composable
private fun whenLabel(at: Instant): String {
    val local = at.atZone(ZoneId.systemDefault())
    val day = when (local.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> local.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return "$day, ${local.format(clockFormatter())}"
}

// --- Chat ---

@Composable
private fun ChatPane(
    state: CoachUiState,
    onToggleHistory: () -> Unit,
    onOpenThread: (String) -> Unit,
    onNewChat: () -> Unit,
    onSuggestion: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val title = if (state.showHistory) "Conversations"
            else state.threads.firstOrNull { it.id == state.threadId }?.title ?: "New conversation"
            Text(title, style = Resurge.type.label, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            ChipButton(if (state.showHistory) "Close" else "History", onToggleHistory)
            Spacer(Modifier.width(8.dp))
            ChipButton("New chat", onNewChat)
        }
        Box(Modifier.weight(1f)) {
            when {
                state.showHistory -> ThreadList(state.threads, state.threadId, onOpenThread)
                state.visibleTurns.isEmpty() && state.pending == null -> EmptyChat(onSuggestion)
                else -> Messages(state)
            }
        }
    }
}

@Composable
private fun ChipButton(label: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surface)
            .border(1.dp, Resurge.colors.border, Resurge.shape.pill),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        indication = LocalIndication.current,
    ) { Text(label, style = Resurge.type.caption) }
}

@Composable
private fun ThreadList(threads: List<CoachThread>, current: String?, onOpen: (String) -> Unit) {
    if (threads.isEmpty()) {
        EmptyState("No conversations yet", "Ask your coach anything about your sleep, training or heart.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ComposerSpace + 24.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                threads.forEachIndexed { i, t ->
                    if (i > 0) Divider()
                    UnstyledButton(
                        onClick = { onOpen(t.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (t.id == current) Resurge.colors.surfaceMuted else Color.Transparent),
                        contentAlignment = Alignment.CenterStart,
                        indication = LocalIndication.current,
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                t.updatedAt?.let { Text(whenLabel(it), style = Resurge.type.caption, color = Resurge.colors.textSubtle) }
                            }
                            t.preview?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Messages(state: CoachUiState) {
    val listState = rememberLazyListState()
    val pending = state.pending
    val count = state.visibleTurns.size + (if (pending != null) 2 else 0)
    LaunchedEffect(count, pending?.streamedText?.length) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ComposerSpace + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.visibleTurns, key = { it.id }) { Bubble(it) }
        if (pending != null) {
            item(key = "pending-user") { UserBubble(pending.userText) }
            item(key = "pending-reply") {
                CoachBubble(
                    text = pending.streamedText.ifBlank {
                        if (pending.usingTool != null) "Looking at your data…" else "Thinking…"
                    }.cleanMarkdown(),
                    muted = pending.streamedText.isBlank(),
                    model = state.model,
                )
            }
        }
    }
}

@Composable
private fun Bubble(turn: CoachTurn) = when (turn.role) {
    TurnRole.USER -> UserBubble(turn.text)
    TurnRole.COACH -> CoachBubble(turn.text.cleanMarkdown(), muted = turn.notice, model = turn.model.takeIf { !turn.notice })
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(Resurge.shape.lg)
                .background(Resurge.colors.strong)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(text, style = Resurge.type.body, color = Resurge.colors.onStrong) }
    }
}

@Composable
private fun CoachBubble(text: String, muted: Boolean, model: CoachModel?) {
    Column(Modifier.widthIn(max = 320.dp)) {
        Box(
            Modifier
                .clip(Resurge.shape.lg)
                .background(Resurge.colors.surface)
                .border(1.dp, Resurge.colors.border, Resurge.shape.lg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(text, style = Resurge.type.body, color = if (muted) Resurge.colors.textMuted else Resurge.colors.text) }
        model?.let {
            Text(it.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        }
    }
}

/** Light cleanup so replies read well as plain text: bullets and bold markers. */
private fun String.cleanMarkdown(): String = lines().joinToString("\n") { line ->
    val trimmed = line.trimStart()
    val bullet = if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) "•  " + trimmed.drop(2) else line
    bullet.replace("**", "").replace(Regex("^#{1,6}\\s*"), "")
}

@Composable
private fun EmptyChat(onSuggestion: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, bottom = ComposerSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.accent, size = 32.dp)
        Spacer(Modifier.height(16.dp))
        Text("Ask your coach", style = Resurge.type.brand)
        Spacer(Modifier.height(8.dp))
        Text(
            "Questions about your sleep, training, heart or food — answered with your own data.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SUGGESTIONS.forEach { suggestion ->
                UnstyledButton(
                    onClick = { onSuggestion(suggestion) },
                    modifier = Modifier
                        .clip(Resurge.shape.pill)
                        .background(Resurge.colors.surface)
                        .border(1.dp, Resurge.colors.border, Resurge.shape.pill),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    indication = LocalIndication.current,
                ) { Text(suggestion, style = Resurge.type.label) }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = ComposerSpace),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.accent, size = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = Resurge.type.headline)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted, textAlign = TextAlign.Center)
    }
}

private val SUGGESTIONS = listOf("Why am I tired today?", "How was my sleep this week?", "Plan a light workout")

// --- Composer ---

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    UnstyledButton(
        onClick = onDismiss,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.danger.copy(alpha = 0.12f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
        indication = LocalIndication.current,
    ) { Text(message, style = Resurge.type.label, color = Resurge.colors.danger) }
}

/** Floating input with the Fast / Smart model switch and a send button, kept above the keyboard. */
@Composable
private fun Composer(
    state: TextFieldState,
    enabled: Boolean,
    model: CoachModel,
    onModelChange: (CoachModel) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = enabled && state.text.isNotBlank()
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.inverse)
            .border(1.dp, Resurge.colors.border, Resurge.shape.lg)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            UnstyledTextField(
                state = state,
                modifier = Modifier.weight(1f),
                textStyle = Resurge.type.body,
                textColor = Resurge.colors.onInverse,
                cursorBrush = SolidColor(Resurge.colors.accent),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                onKeyboardAction = { if (canSend) onSend() },
                accessibilityLabel = "Message your coach",
            ) {
                TextInput(placeholder = { Text("Ask about your health…", style = Resurge.type.body, color = Resurge.colors.onInverseMuted) })
            }
            Spacer(Modifier.width(8.dp))
            UnstyledButton(
                onClick = onSend,
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
        Spacer(Modifier.height(6.dp))
        ModelSwitch(model, onModelChange)
    }
}

@Composable
private fun ModelSwitch(model: CoachModel, onChange: (CoachModel) -> Unit) {
    Row(
        Modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CoachModel.entries.forEach { option ->
            val selected = option == model
            UnstyledButton(
                onClick = { onChange(option) },
                role = Role.RadioButton,
                modifier = Modifier
                    .semantics { this.selected = selected }
                    .clip(Resurge.shape.pill)
                    .background(if (selected) Resurge.colors.strong else Color.Transparent),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                indication = LocalIndication.current,
            ) {
                Text(option.label, style = Resurge.type.caption, color = if (selected) Resurge.colors.onStrong else Resurge.colors.textMuted)
            }
        }
    }
}
