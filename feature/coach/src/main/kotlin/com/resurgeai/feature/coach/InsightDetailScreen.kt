package com.resurgeai.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.composeunstyled.Text
import com.resurgeai.core.data.coach.CoachRepository
import com.resurgeai.core.data.session.SessionRepository
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.CoachBadge
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.MajorGoal
import com.resurgeai.core.model.SessionState
import com.resurgeai.core.model.TrainingGoal
import com.resurgeai.core.model.WeeklyArea
import com.resurgeai.core.model.WeeklyDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class InsightDetailUiState(val loaded: Boolean = false, val insight: CoachInsight? = null, val goal: MajorGoal? = null)

@HiltViewModel
class InsightDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: CoachRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {
    val id: String = checkNotNull(savedState.get<String>(ARG_ID))

    val state: StateFlow<InsightDetailUiState> = combine(
        // Opening it counts as seeing it: it leaves Today's pinned card and loses its dot.
        repository.insight(id).onEach { i -> if (i != null && !i.seen) repository.markSeen(i.id) },
        sessionRepository.session.map { (it as? SessionState.Ready)?.profile?.majorGoal }.onStart { emit(null) },
    ) { i, g -> InsightDetailUiState(loaded = true, insight = i, goal = g) }
        .catch { emit(InsightDetailUiState(loaded = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightDetailUiState())

    companion object {
        /** Must match the property name in the navigation route. */
        const val ARG_ID = "id"
    }
}

@Composable
fun InsightDetailRoute(onBack: () -> Unit, onAsk: (String) -> Unit, viewModel: InsightDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightDetailScreen(state, onBack, onAsk = { onAsk(viewModel.id) })
}

/** One insight in full, with "Ask about this" to start a chat about it. A weekly review gets the week's layout. */
@Composable
fun InsightDetailScreen(state: InsightDetailUiState, onBack: () -> Unit, onAsk: () -> Unit) {
    val insight = state.insight
    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, top = 8.dp, bottom = 4.dp)) {
            IconButton(ResurgeIcons.ArrowLeft, contentDescription = "Back", onClick = onBack)
        }
        when {
            !state.loaded -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Spinner(color = Resurge.colors.text) }
            insight == null -> EmptyState("Insight not found", "It may have been removed.")
            else -> {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val weekly = insight.weekly
                    if (insight.kind == InsightKind.WEEKLY && weekly != null) WeeklyBody(insight, weekly, state.goal) else NoteBody(insight)
                    Spacer(Modifier.height(8.dp))
                }
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Button(if (insight.kind == InsightKind.WEEKLY) "Ask about this week" else "Ask about this", onClick = onAsk, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** A workout or sleep note: what and when, the note, its numbers, and the coach's next step. */
@Composable
private fun NoteBody(insight: CoachInsight) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(insightIcon(insight), contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(metaLabel(insight), style = Resurge.type.caption, color = Resurge.colors.textSubtle)
        }
        Text(insight.title, style = Resurge.type.title)
    }
    Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted)
    if (insight.highlights.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(vertical = 14.dp)) {
            insight.highlights.take(3).forEachIndexed { i, h ->
                if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(Resurge.colors.border))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(h.value, style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"), maxLines = 1)
                    Text(h.label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
                }
            }
        }
    }
    insight.suggestion?.let { CoachNote(it) }
}

@Composable
private fun CoachNote(text: String) {
    Row(
        Modifier.fillMaxWidth().clip(Resurge.shape.md).border(1.dp, Resurge.colors.border, Resurge.shape.md).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoachBadge(size = 28.dp)
        Text(text, style = Resurge.type.body, color = Resurge.colors.text)
    }
}

/** The week: label and headline, its good days, the story, how each area moved, wins, and next week's focus. */
@Composable
private fun WeeklyBody(insight: CoachInsight, weekly: WeeklyDetails, goal: MajorGoal?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(listOfNotNull(weekLabel(weekly, goal), weekRange(weekly)).distinct().joinToString(" · "))
        Text(insight.title, style = Resurge.type.title)
    }
    if (weekly.days.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${weekly.goodDays ?: weekly.days.count { it == true }}", style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"))
                weekly.target?.let { Text(" / $it good days", style = Resurge.type.label, color = Resurge.colors.textSubtle) }
                Spacer(Modifier.weight(1f))
                outcome(weekly)?.let { (text, color) -> Text(text, style = Resurge.type.label, color = color) }
            }
            val start = weekly.weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            WeekSquares(weekly.days, size = null, labels = start?.let { s -> (0 until 7).map { s.plusDays(it.toLong()).dayOfWeek.letter() } })
        }
    }
    if (weekly.summary.isNotBlank()) Text(weekly.summary, style = Resurge.type.body, color = Resurge.colors.textMuted)
    if (weekly.areas.isNotEmpty()) {
        Section("How each area moved") {
            Column(Modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface)) {
                weekly.areas.forEachIndexed { i, a ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
                    AreaRow(a, goal)
                }
            }
        }
    }
    if (weekly.wins.isNotEmpty()) {
        Section("Wins") {
            weekly.wins.forEach { w ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.up))
                    Spacer(Modifier.width(10.dp))
                    Text(w, style = Resurge.type.body)
                }
            }
        }
    }
    if (weekly.habits.isNotBlank()) Section("Habits") { Text(weekly.habits, style = Resurge.type.body, color = Resurge.colors.textMuted) }
    if (weekly.focus.isNotEmpty()) {
        Section("Focus for next week") {
            weekly.focus.forEachIndexed { i, f ->
                Row(
                    Modifier.fillMaxWidth().clip(Resurge.shape.md).border(1.dp, Resurge.colors.border, Resurge.shape.md).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.size(24.dp).clip(Resurge.shape.pill).background(Resurge.colors.strong), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = Resurge.type.caption, color = Resurge.colors.onStrong)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(f, style = Resurge.type.body)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline(title)
        content()
    }
}

/** An area with an arrow coloured by whether that direction is good for it (and for the goal). */
@Composable
private fun AreaRow(area: WeeklyArea, goal: MajorGoal?) {
    val color = areaColor(area, goal)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Text(
                when (area.trend) { "up" -> "↑"; "down" -> "↓"; else -> "→" },
                style = Resurge.type.label,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(area.area.replaceFirstChar { it.uppercase() }, style = Resurge.type.label, color = Resurge.colors.text)
            Text(area.note, style = Resurge.type.caption, color = Resurge.colors.textMuted)
        }
        if (area.value.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(area.value, style = Resurge.type.label.copy(fontFeatureSettings = "tnum"), color = color)
        }
    }
}

@Composable
private fun areaColor(area: WeeklyArea, goal: MajorGoal?): Color {
    if (area.trend == "steady") return Resurge.colors.textMuted
    val up = area.trend == "up"
    val good = when (area.area) {
        // Weight is good in the goal's direction; resting heart rate is good going down.
        "weight" -> when {
            goal?.type == TrainingGoal.BUILD_MUSCLE -> up
            goal?.type == TrainingGoal.LOSE_FAT -> !up
            else -> null
        }
        "heart" -> !up
        else -> up
    } ?: return Resurge.colors.textMuted
    return if (good) Resurge.colors.up else Resurge.colors.down
}

private fun DayOfWeek.letter(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())
