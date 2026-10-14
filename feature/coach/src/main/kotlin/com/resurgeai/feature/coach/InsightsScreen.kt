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
import androidx.compose.ui.unit.Dp
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
import com.resurgeai.core.designsystem.components.TopBar
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

/** Standalone list of past insights (activity, sleep and weekly reviews), newest first. */
@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(state, onBack)
}

@Composable
fun InsightsScreen(state: InsightsUiState, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Insights", onBack = onBack)
            Box(Modifier.weight(1f)) { InsightsFeed(state) }
        }
    }
}

// --- Insights ---

@Composable
internal fun InsightsFeed(state: InsightsUiState) {
    when {
        !state.loaded -> InsightsSkeleton()
        state.insights.isEmpty() -> EmptyState(
            title = "No insights yet",
            body = "Finish a workout or a night of sleep and your coach will write about it. Every Saturday evening you'll also get a review of your week.",
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.insights, key = { it.id }) {
                if (it.kind == InsightKind.WEEKLY) WeeklyCard(it) else InsightCard(it)
            }
        }
    }
}

@Composable
internal fun InsightCard(insight: CoachInsight) {
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
internal fun WeeklyCard(insight: CoachInsight) {
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
            if (weekly.habits.isNotBlank()) BulletSection("Habits", listOf(weekly.habits))
            if (weekly.focus.isNotEmpty()) BulletSection("Next week", weekly.focus)
        }
    }
}

@Composable
internal fun BulletSection(title: String, items: List<String>) {
    Spacer(Modifier.height(16.dp))
    Text(title.uppercase(), style = Resurge.type.caption, color = Resurge.colors.onInverseMuted)
    Spacer(Modifier.height(6.dp))
    items.forEach { Text("•  $it", style = Resurge.type.body, color = Resurge.colors.onInverse, modifier = Modifier.padding(vertical = 2.dp)) }
}

@Composable
internal fun InsightsSkeleton() {
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
internal fun categoryColor(category: String?, kind: InsightKind): Color = when {
    kind == InsightKind.SLEEP || category == "SLEEP" -> Resurge.colors.sleep
    category == "HEART" -> Resurge.colors.heart
    category == "NUTRITION" -> Resurge.colors.energy
    category == "RECOVERY" -> Resurge.colors.water
    else -> Resurge.colors.activity
}

internal fun categoryLabel(category: String?, kind: InsightKind): String = when {
    kind == InsightKind.SLEEP -> "Sleep"
    category != null -> category.lowercase().replaceFirstChar { it.uppercase() }
    else -> "Activity"
}

@Composable
internal fun whenLabel(at: Instant): String {
    val local = at.atZone(ZoneId.systemDefault())
    val day = when (local.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> local.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    return "$day, ${local.format(clockFormatter())}"
}

@Composable
internal fun EmptyState(title: String, body: String, bottomSpace: Dp = 0.dp) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 32.dp, bottom = bottomSpace),
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

