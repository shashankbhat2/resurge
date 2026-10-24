package com.resurgeai.feature.dashboard

import com.resurgeai.core.designsystem.components.OverviewFooterHeight
import com.resurgeai.core.designsystem.components.OverviewBodyHeight
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.components.StatDivider
import com.resurgeai.core.designsystem.components.StatCell
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.CoachBadge
import com.resurgeai.core.designsystem.components.CoachMascot
import com.resurgeai.core.designsystem.components.HabitVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.RiseBadge
import com.resurgeai.core.designsystem.components.TIDE_RISE_MS
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.CoachStyle
import com.resurgeai.core.model.InsightKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// --- Coach ---

/** The latest coach insight in the persona's voice. */
@Composable
internal fun CoachNote(insight: CoachInsight?, commander: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Row(Modifier.padding(16.dp)) {
            CoachBadge(size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label ?: listOfNotNull(if (commander) "Commander" else "Friend", insight?.kind?.let(::insightLabel)).joinToString(" · "),
                    style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.caption.fontSize * 1.08f),
                    color = Resurge.colors.textSubtle,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    insight?.body ?: "Your first note arrives after your next workout or night of sleep.",
                    style = Resurge.type.body,
                    color = if (insight != null) Resurge.colors.text else Resurge.colors.textMuted,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun insightLabel(kind: InsightKind): String = when (kind) {
    InsightKind.WEEKLY -> "Weekly review"
    InsightKind.SLEEP -> "Sleep"
    InsightKind.ACTIVITY -> "Activity"
}

// --- Day complete ---

/** The day's one celebration, once everything today is done. */
@Composable
internal fun DayCompleteDialog(state: DashboardUiState, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    BottomSheetDialog(onDismiss) { DayCompleteSheet(state, onDismiss) }
}

@Composable
internal fun DayCompleteSheet(state: DashboardUiState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val streak = state.streak
    val day = streak.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val commander = state.profile?.coachStyle == CoachStyle.COMMANDER
    Column(
        modifier
            .fillMaxWidth()
            .clip(Resurge.shape.lg)
            .background(Resurge.colors.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Resurge.colors.surface)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Overline("$day · ${streak.done} of ${streak.total}", color = Resurge.colors.up)
            Text("Day complete", style = Resurge.type.display)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (streak.streak.current > 0) RiseBadge(streak.streak.current)
                Text(
                    "${streak.week.metDays} of ${streak.week.target} days this week",
                    style = Resurge.type.label,
                    color = Resurge.colors.textMuted,
                    modifier = Modifier
                        .clip(Resurge.shape.pill)
                        .background(Resurge.colors.surfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CoachNote(
                insight = state.latestInsight,
                commander = commander,
                onClick = onDone,
                label = if (commander) "Debrief" else "Today's note",
            )
            Button("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        }
    }
}

// --- Shared ---

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
}
