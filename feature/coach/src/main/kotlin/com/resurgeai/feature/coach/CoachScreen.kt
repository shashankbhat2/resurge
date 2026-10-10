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
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.TopShade
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Height reserved at the bottom of scrolling content for the floating composer. */
private val ComposerSpace = 96.dp

@Composable
fun CoachRoute(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CoachScreen(state = state, onBack = onBack, onTabChange = viewModel::selectTab, onSend = viewModel::send)
}

/**
 * Full-screen coach (no navbar, no main header): Insights feed and Chat as two tabs, with a
 * floating composer pinned above the keyboard.
 */
@Composable
fun CoachScreen(
    state: CoachUiState,
    onBack: () -> Unit,
    onTabChange: (CoachTab) -> Unit,
    onSend: (String) -> Unit,
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
                    CoachTab.INSIGHTS -> InsightsFeed(state.insights)
                    CoachTab.CHAT -> ChatThread(state, onSuggestion = { input.edit { replace(0, length, it) } })
                }
            }
        }
        Composer(
            state = input,
            enabled = !state.replying,
            onSend = {
                onSend(input.text.toString())
                input.clearText()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// --- Insights ---

@Composable
private fun InsightsFeed(insights: List<Insight>) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = ComposerSpace + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(insights, key = { it.id }) { InsightCard(it) }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val clock = clockFormatter()
    val tint = categoryColor(insight.category)
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(tint))
            Spacer(Modifier.width(8.dp))
            Text(insight.category.label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.weight(1f))
            Text("${insight.dayLabel}, ${insight.at.format(clock)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Spacer(Modifier.height(14.dp))
        Text(insight.title, style = Resurge.type.headline)
        Spacer(Modifier.height(8.dp))
        Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
}

private val Insight.dayLabel: String
    get() = when (at.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> at.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }


@Composable
private fun categoryColor(category: InsightCategory): Color = when (category) {
    InsightCategory.SLEEP -> Resurge.colors.sleep
    InsightCategory.ACTIVITY -> Resurge.colors.activity
    InsightCategory.HEART -> Resurge.colors.heart
    InsightCategory.NUTRITION -> Resurge.colors.energy
}

// --- Chat ---

@Composable
private fun ChatThread(state: CoachUiState, onSuggestion: (String) -> Unit) {
    if (state.messages.isEmpty()) {
        EmptyChat(onSuggestion)
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.replying) {
        listState.animateScrollToItem((state.messages.size + if (state.replying) 1 else 0).coerceAtLeast(1) - 1)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = ComposerSpace + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.messages, key = { it.id }) { Bubble(it) }
        if (state.replying) item { TypingBubble() }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val user = message.author == Author.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(Resurge.shape.lg)
                .background(if (user) Resurge.colors.strong else Resurge.colors.surface)
                .then(if (user) Modifier else Modifier.border(1.dp, Resurge.colors.border, Resurge.shape.lg))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(message.text, style = Resurge.type.body, color = if (user) Resurge.colors.onStrong else Resurge.colors.text)
        }
    }
}

@Composable
private fun TypingBubble() {
    Box(
        Modifier
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface)
            .border(1.dp, Resurge.colors.border, Resurge.shape.lg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Thinking…", style = Resurge.type.body, color = Resurge.colors.textMuted)
    }
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
                ) {
                    Text(suggestion, style = Resurge.type.label)
                }
            }
        }
    }
}

private val SUGGESTIONS = listOf("Why am I tired today?", "How was my sleep this week?", "Plan a light workout")

// --- Composer ---

/** Floating pill input + send button, kept above the keyboard and navigation bar. */
@Composable
private fun Composer(
    state: TextFieldState,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canSend = enabled && state.text.isNotBlank()
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .heightIn(min = 60.dp)
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.inverse)
            .border(1.dp, Resurge.colors.border, Resurge.shape.lg)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
}
