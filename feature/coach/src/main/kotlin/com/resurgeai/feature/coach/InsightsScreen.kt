package com.resurgeai.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.CoachMascot
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.WeeklyArea
import com.resurgeai.core.model.WeeklyDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Insights", onBack = onBack)
        Box(Modifier.weight(1f)) { InsightsFeed(state) }
    }
}

// --- Insights ---

/** Insights grouped by day under section labels; a weekly review leads its day as the hero card. */
@Composable
internal fun InsightsFeed(state: InsightsUiState) {
    val commander = state.coachStyle == CoachStyle.COMMANDER
    when {
        !state.loaded -> InsightsSkeleton()
        state.insights.isEmpty() -> EmptyState(
            title = "No insights yet",
            body = "Finish a workout or a night of sleep and your coach will write about it. Every Saturday evening you'll also get a review of your week.",
        )
        else -> {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val days = state.insights.groupBy { it.createdAt.atZone(zone).toLocalDate() }.toList()
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                days.forEach { (date, insights) ->
                    item(key = "h$date") {
                        SectionLabel(
                            when (date) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                            },
                            Modifier.padding(top = 4.dp),
                            trailing = "${insights.size} ${if (insights.size == 1) "note" else "notes"}",
                        )
                    }
                    items(insights.sortedByDescending { it.kind == InsightKind.WEEKLY }, key = { it.id }) {
                        if (it.kind == InsightKind.WEEKLY) WeeklyReview(it, commander) else InsightCard(it, commander)
                    }
                }
            }
        }
    }
}

/**
 * One insight about a workout or a night: what it's about (category, in its colour) and when, the
 * headline, the note, the numbers it's based on, then the coach's suggestion in the persona's voice.
 */
@Composable
internal fun InsightCard(insight: CoachInsight, commander: Boolean = false) {
    val tint = categoryColor(insight.category, insight.kind)
    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(tint))
            Spacer(Modifier.width(8.dp))
            Text(categoryLabel(insight.category, insight.kind), style = Resurge.type.label, color = tint, modifier = Modifier.weight(1f))
            Text(timeLabel(insight.createdAt), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(insight.title, style = Resurge.type.headline)
            Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted)
        }
        if (insight.highlights.isNotEmpty()) Highlights(insight)
        insight.suggestion?.let { Suggestion(it, commander) }
    }
}

/** The numbers the note is based on, as up to three labelled values with hairlines between. */
@Composable
private fun Highlights(insight: CoachInsight) {
    Row(Modifier.fillMaxWidth().clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted).padding(vertical = 10.dp)) {
        insight.highlights.take(3).forEachIndexed { i, h ->
            if (i > 0) Box(Modifier.width(1.dp).height(36.dp).background(Resurge.colors.border))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(h.value, style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                Text(h.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
            }
        }
    }
}

/** The coach's next step, with its blob: Friend on `brand-tint`, Commander on `surface-muted`. */
@Composable
private fun Suggestion(text: String, commander: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.sm)
            .background(if (commander) Resurge.colors.surfaceMuted else Resurge.colors.accentMuted)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CoachMascot(commander = commander, size = 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = Resurge.type.label, color = Resurge.colors.text)
    }
}

/**
 * The Saturday review: a `brand-tint` header that states the week, then wins, how each area moved
 * (neutral pills: whether a change is good depends on the metric, so the words carry it), the habits
 * note and the focus for next week.
 */
@Composable
internal fun WeeklyReview(insight: CoachInsight, commander: Boolean = false) {
    val weekly = insight.weekly
    Column(Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface)) {
        Column(
            Modifier.fillMaxWidth().background(Resurge.colors.accentMuted).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(listOfNotNull("Weekly review", weekly?.let(::weekRange)).joinToString(" · "), Modifier.weight(1f), color = Resurge.colors.brandInk)
                CoachMascot(commander = commander, size = 28.dp)
            }
            Text(insight.title, style = Resurge.type.brand.copy(fontSize = Resurge.type.title.fontSize, lineHeight = Resurge.type.title.lineHeight))
            weekly?.summary?.takeIf { it.isNotBlank() }?.let { Text(it, style = Resurge.type.body, color = Resurge.colors.textMuted) }
        }
        if (weekly != null) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (weekly.wins.isNotEmpty()) ReviewSection("Wins") { weekly.wins.forEach { Bullet(it) } }
                if (weekly.areas.isNotEmpty()) ReviewSection("How it moved") {
                    weekly.areas.forEachIndexed { i, area ->
                        if (i > 0) Divider()
                        TrendRow(area)
                    }
                }
                if (weekly.habits.isNotBlank()) ReviewSection("Habits") { Text(weekly.habits, style = Resurge.type.body) }
                weekly.focus.firstOrNull()?.let { focus ->
                    ReviewSection("Next week") {
                        Row(
                            Modifier.fillMaxWidth().clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted).padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(ResurgeIcons.Target, contentDescription = null, tint = Resurge.colors.brandInk, size = 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(focus, style = Resurge.type.label)
                        }
                        weekly.focus.drop(1).forEach { Bullet(it) }
                    }
                }
            }
        } else {
            Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun ReviewSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(title)
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Box(Modifier.padding(top = 8.dp).size(5.dp).clip(Resurge.shape.pill).background(Resurge.colors.textSubtle))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Resurge.type.body)
    }
}

/** One area of the week with a neutral trend pill. */
@Composable
private fun TrendRow(area: WeeklyArea) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(area.area.replaceFirstChar { it.uppercase() }, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight))
            Text(area.note, style = Resurge.type.caption.copy(lineHeight = Resurge.type.label.lineHeight), color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            when (area.trend) { "up" -> "↑ Up"; "down" -> "↓ Down"; else -> "→ Steady" },
            style = Resurge.type.caption,
            color = Resurge.colors.textMuted,
            modifier = Modifier.clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** "6–12 Oct" from the review's week bounds (ISO dates), or null when they're missing. */
internal fun weekRange(weekly: WeeklyDetails): String? {
    val start = weekly.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val end = weekly.weekEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    return if (start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(month)}"
    else "${start.dayOfMonth} ${start.format(month)} – ${end.dayOfMonth} ${end.format(month)}"
}

@Composable
private fun timeLabel(at: Instant): String = at.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter())

@Composable
internal fun InsightsSkeleton() {
    SkeletonGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp)) {
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

