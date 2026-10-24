package com.resurgeai.feature.coach

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachThread
import com.resurgeai.core.model.DayStreakMath
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.WeeklyDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    onOpenInsight: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(state, onBack, viewModel::setFilter, onOpenInsight, onOpenChat, onNewChat)
}

/**
 * Insights and past chats (Resurge Mono): filter chips, the latest weekly review as the lead card,
 * then each note as a row (unseen ones dotted in the accent). Chats lists past conversations.
 */
@Composable
fun InsightsScreen(
    state: InsightsUiState,
    onBack: () -> Unit,
    onFilter: (InsightFilter) -> Unit = {},
    onOpenInsight: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            UnstyledButton(
                onClick = onNewChat,
                role = Role.Button,
                modifier = Modifier.clip(Resurge.shape.pill).border(1.dp, Resurge.colors.borderStrong, Resurge.shape.pill),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                indication = LocalIndication.current,
            ) { Text("New chat", style = Resurge.type.label, color = Resurge.colors.text) }
        }
        Text("Insights", style = Resurge.type.display, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp))
        Filters(state.filter, onFilter)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.weight(1f)) {
            when {
                !state.loaded -> InsightsSkeleton()
                state.filter == InsightFilter.CHATS -> ChatList(state.threads, onOpenChat)
                state.shown.isEmpty() -> EmptyState(
                    title = if (state.filter == InsightFilter.ALL) "No insights yet" else "Nothing here yet",
                    body = "Finish a workout or a night of sleep and your coach will write about it. Every Saturday evening you'll also get a review of your week.",
                )
                else -> InsightList(state, onOpenInsight)
            }
        }
    }
}

@Composable
private fun Filters(selected: InsightFilter, onSelect: (InsightFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InsightFilter.entries.forEach { f ->
            val on = f == selected
            UnstyledButton(
                onClick = { onSelect(f) },
                role = Role.Tab,
                modifier = Modifier
                    .semantics { this.selected = on }
                    .clip(Resurge.shape.pill)
                    .then(if (on) Modifier.background(Resurge.colors.strong) else Modifier.border(1.dp, Resurge.colors.borderStrong, Resurge.shape.pill)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                indication = LocalIndication.current,
            ) { Text(f.label, style = Resurge.type.label, color = if (on) Resurge.colors.onStrong else Resurge.colors.textMuted) }
        }
    }
}

@Composable
private fun InsightList(state: InsightsUiState, onOpen: (String) -> Unit) {
    val items = state.shown
    // The newest weekly review leads as its own card; in Weekly every review gets one.
    val leads = if (state.filter == InsightFilter.WEEKLY) items else listOfNotNull(items.firstOrNull { it.kind == InsightKind.WEEKLY && isRecent(it) })
    val rows = items - leads.toSet()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp + navBottom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(leads, key = { "w" + it.id }) { WeeklyCard(it, state.goal) { onOpen(it.id) } }
        if (rows.isNotEmpty()) {
            item(key = "rows") {
                Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
                    rows.forEachIndexed { i, insight ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
                        InsightRow(insight) { onOpen(insight.id) }
                    }
                }
            }
        }
    }
}

private fun isRecent(i: CoachInsight): Boolean = ChronoUnit.DAYS.between(i.createdAt, Instant.now()) < 8

/** The weekly review as the lead card: week, won or lost, headline, its seven days. */
@Composable
internal fun WeeklyCard(insight: CoachInsight, goal: MajorGoal?, onClick: () -> Unit) {
    val weekly = insight.weekly
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).semantics { contentDescription = "Weekly review: ${insight.title}. Open" },
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline("Weekly review · ${weekly?.let { weekLabel(it, goal) } ?: "This week"}", Modifier.weight(1f))
                weekly?.let { outcome(it) }?.let { (text, color) -> Text(text, style = Resurge.type.label, color = color) }
            }
            Text(insight.title, style = Resurge.type.headline)
            weekly?.days?.takeIf { it.isNotEmpty() }?.let { WeekSquares(it, size = null) }
            Text(
                listOfNotNull(weekly?.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.format(DAY), "${readMinutes(insight)} min read").joinToString(" · "),
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
            )
        }
    }
}

/** Seven squares, good days in up, missed in down, open days outlined. */
@Composable
internal fun WeekSquares(days: List<Boolean?>, size: Dp?, labels: List<String>? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        days.take(7).forEachIndexed { i, met ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .then(if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f))
                        .clip(Resurge.shape.sm)
                        .then(
                            when (met) {
                                true -> Modifier.background(Resurge.colors.up)
                                false -> Modifier.background(Resurge.colors.down)
                                null -> Modifier.border(1.dp, Resurge.colors.borderStrong, Resurge.shape.sm)
                            },
                        ),
                )
                labels?.getOrNull(i)?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                }
            }
        }
    }
}

/** "Won", or "Lost by 1 day", in its signal colour; null for older reviews without the count. */
@Composable
internal fun outcome(weekly: WeeklyDetails): Pair<String, Color>? {
    val good = weekly.goodDays ?: return null
    val target = weekly.target ?: return null
    return if (good >= target) "Week won" to Resurge.colors.up
    else "Lost by ${target - good} ${if (target - good == 1) "day" else "days"}" to Resurge.colors.down
}

/** "Week 4" counted from the goal's start, else the dates. */
internal fun weekLabel(weekly: WeeklyDetails, goal: MajorGoal?): String {
    val start = weekly.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start != null && goal != null) {
        val n = ChronoUnit.WEEKS.between(DayStreakMath.weekStart(goal.startDate), start).toInt() + 1
        if (n >= 1) return "Week $n"
    }
    return weekRange(weekly) ?: "This week"
}

private fun readMinutes(i: CoachInsight): Int {
    val w = i.weekly
    val words = listOfNotNull(i.title, w?.summary, w?.habits).sumOf { it.split(" ").size } +
        (w?.wins.orEmpty() + w?.focus.orEmpty() + w?.areas.orEmpty().map { it.note }).sumOf { it.split(" ").size }
    return (words / 200).coerceAtLeast(1)
}

/** One note: an icon for what it's about, the title (dotted while unseen), two lines of it, and when. */
@Composable
private fun InsightRow(insight: CoachInsight, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${insight.title}. ${if (!insight.seen) "New. " else ""}Open" },
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
                Icon(insightIcon(insight), contentDescription = null, tint = Resurge.colors.textMuted, size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(insight.title, style = Resurge.type.label, color = Resurge.colors.text, modifier = Modifier.weight(1f, fill = false))
                    if (!insight.seen) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(Resurge.colors.accent))
                    }
                }
                Text(insight.body, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metaLabel(insight), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
    }
}

@DrawableRes
internal fun insightIcon(i: CoachInsight): Int = when {
    i.kind == InsightKind.SLEEP || i.category == "SLEEP" -> ResurgeIcons.Moon
    i.kind == InsightKind.WEEKLY -> ResurgeIcons.Target
    i.category == "HEART" || i.category == "RECOVERY" -> ResurgeIcons.Heartbeat
    i.category == "NUTRITION" -> ResurgeIcons.Food
    else -> ResurgeIcons.Dumbbell
}

/** "After a workout · today", "Sleep · Thursday". */
@Composable
internal fun metaLabel(i: CoachInsight): String {
    val what = when (i.kind) {
        InsightKind.SLEEP -> "Sleep"
        InsightKind.WEEKLY -> "Weekly review"
        InsightKind.ACTIVITY -> "After a workout"
    }
    return "$what · ${dayLabel(i.createdAt)}"
}

@Composable
internal fun dayLabel(at: Instant): String {
    val date = at.atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when {
        date == today -> "today, ${at.atZone(ZoneId.systemDefault()).format(clockFormatter())}"
        date == today.minusDays(1) -> "yesterday"
        date.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        else -> date.format(DAY)
    }
}

@Composable
private fun ChatList(threads: List<CoachThread>, onOpen: (String) -> Unit) {
    if (threads.isEmpty()) {
        EmptyState("No chats yet", "Ask your coach anything about your plan, your streak or your scores.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp + navBottom())) {
        item {
            Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
                threads.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
                    UnstyledButton(
                        onClick = { onOpen(t.id) },
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                        indication = LocalIndication.current,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                t.updatedAt?.let {
                                    Spacer(Modifier.width(8.dp))
                                    Text(dayLabel(it), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                                }
                            }
                            t.insightTitle?.let { Text("About: $it", style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            t.preview?.let(::plainPreview)?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** The bottom system bar, so lists scroll clear of it. */
@Composable
private fun navBottom() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** A reply's start as one plain line: no markdown markers, line breaks or trailing ellipses. */
internal fun plainPreview(text: String): String = text
    .replace(Regex("[*_#`>]+"), "")
    .replace(Regex("(?m)^\\s*[-•]\\s+"), "")
    .replace(Regex("\\s+"), " ")
    .replace(Regex("(\\s*(\\.{3}|…))+$"), "")
    .trim()

/** "6–12 Oct" from the review's week bounds (ISO dates), or null when they're missing. */
internal fun weekRange(weekly: WeeklyDetails): String? {
    val start = weekly.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val end = weekly.weekEnd?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val month = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    return if (start.month == end.month) "${start.dayOfMonth}–${end.dayOfMonth} ${end.format(month)}"
    else "${start.dayOfMonth} ${start.format(month)} – ${end.dayOfMonth} ${end.format(month)}"
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

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
                }
            }
        }
    }
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
        Icon(ResurgeIcons.CoachFilled, contentDescription = null, tint = Resurge.colors.text, size = 28.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = Resurge.type.headline)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Resurge.type.body, color = Resurge.colors.textMuted, textAlign = TextAlign.Center)
    }
}
