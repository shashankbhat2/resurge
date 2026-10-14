package com.resurgeai.feature.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.MetricChip
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.CoachInsight
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.InsightKind
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Where timeline cards lead. */
class FeedActions(
    val onOpenMeal: (String) -> Unit = {},
    val onOpenWorkout: (String) -> Unit = {},
    val onOpenSleep: () -> Unit = {},
    val onOpenInsights: () -> Unit = {},
)

// --- Quick actions ---

/** Scan a meal, log a workout or add a glass of water without leaving Today. */
@Composable
internal fun QuickActions(onScanMeal: () -> Unit, onLogWorkout: () -> Unit, onAddWater: () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(ResurgeIcons.Camera, "Scan meal", Resurge.colors.energy, onScanMeal)
        QuickAction(ResurgeIcons.Dumbbell, "Log workout", Resurge.colors.activity, onLogWorkout)
        QuickAction(ResurgeIcons.Droplet, "+250 ml", Resurge.colors.water, onAddWater)
    }
}

@Composable
private fun QuickAction(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.surface)
            .border(1.dp, Resurge.colors.border.copy(alpha = 0.6f), Resurge.shape.pill),
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        indication = LocalIndication.current,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(label, style = Resurge.type.label)
        }
    }
}

// --- Timeline ---

/** Day sections with a time rail on the left and a card per entry. */
@Composable
internal fun Timeline(days: List<FeedDay>, today: LocalDate, units: UnitSystem, actions: FeedActions) {
    if (days.isEmpty()) {
        SectionHeader("Today")
        Text(
            "Meals, workouts and sleep show up here as you log them or your devices sync.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    days.forEach { day ->
        SectionHeader(
            when (day.date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            day.items.forEachIndexed { i, item ->
                TimelineRow(item.time, color = railColor(item), last = i == day.items.lastIndex) {
                    when (item) {
                        is FeedItem.MealEntry -> MealEntryCard(item.meal) { actions.onOpenMeal(item.meal.id) }
                        is FeedItem.WorkoutEntry -> WorkoutEntryCard(item.workout, units) { actions.onOpenWorkout(item.workout.id) }
                        is FeedItem.SleepEntry -> SleepEntryCard(item.session, actions.onOpenSleep)
                        is FeedItem.InsightEntry -> InsightEntryCard(item.insight, actions.onOpenInsights)
                    }
                }
            }
        }
    }
}

@Composable
private fun railColor(item: FeedItem): Color = when (item) {
    is FeedItem.MealEntry -> Resurge.colors.energy
    is FeedItem.WorkoutEntry -> ActivityVisuals.color(item.workout.activity.category)
    is FeedItem.SleepEntry -> Resurge.colors.sleep
    is FeedItem.InsightEntry -> Resurge.colors.accent
}

@Composable
private fun TimelineRow(time: Instant, color: Color, last: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                time.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter()),
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = Resurge.colors.textMuted,
                maxLines = 1,
                modifier = Modifier.padding(top = 18.dp),
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(8.dp).clip(Resurge.shape.pill).background(color))
            if (!last) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(Resurge.shape.pill)
                        .background(Resurge.colors.border),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).padding(bottom = 10.dp)) { content() }
    }
}

// --- Entry cards ---

@Composable
private fun EntryHeader(icon: @Composable () -> Unit, title: String, subtitle: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Resurge.type.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealEntryCard(meal: Meal, onClick: () -> Unit) {
    val t = meal.totals
    Card(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        EntryHeader(
            icon = {
                val thumb = meal.thumbnail
                val bitmap = remember(thumb) { thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                if (bitmap != null) {
                    Image(bitmap, null, Modifier.size(40.dp).clip(Resurge.shape.md), contentScale = ContentScale.Crop)
                } else {
                    IconBadge(ResurgeIcons.Bowl, tint = Resurge.colors.energy, size = 40.dp)
                }
            },
            title = meal.title,
            subtitle = "${meal.type.label} · ${meal.items.size} ${if (meal.items.size == 1) "item" else "items"}",
            trailing = "${t.kcal.roundToInt()} kcal",
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricChip("${t.protein.roundToInt()}g", icon = ResurgeIcons.Protein, tint = MacroVisuals.protein())
            MetricChip("${t.carbs.roundToInt()}g", icon = ResurgeIcons.Carbs, tint = MacroVisuals.carbs())
            MetricChip("${t.fat.roundToInt()}g", icon = ResurgeIcons.Fat, tint = MacroVisuals.fat())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutEntryCard(w: Workout, units: UnitSystem, onClick: () -> Unit) {
    val activity = w.activity
    val origin = if (w.source == WorkoutSource.MANUAL) "Logged" else w.sourceName ?: "Health Connect"
    Card(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        EntryHeader(
            icon = { IconBadge(ActivityVisuals.icon(activity.icon), tint = ActivityVisuals.color(activity.category), size = 40.dp) },
            title = w.displayName,
            subtitle = origin,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetricChip(Formatters.duration(w.duration), icon = ResurgeIcons.Stopwatch)
            (w.metrics.distanceM ?: FitnessMath.swimDistanceM(w.metrics))?.let { m ->
                val text = if (units == UnitSystem.IMPERIAL) String.format(Locale.getDefault(), "%.2f mi", m / FitnessMath.M_PER_MI)
                else "${Formatters.kilometres(m)} km"
                MetricChip(text, icon = ResurgeIcons.Distance)
            }
            if (w.metrics.exercises.isNotEmpty()) {
                MetricChip("${w.metrics.exercises.sumOf { it.sets.size }} sets", icon = ResurgeIcons.Dumbbell)
            }
            w.metrics.kcal?.let { MetricChip("$it kcal", icon = ResurgeIcons.Flame) }
            w.metrics.avgHr?.let { MetricChip("$it bpm", icon = ResurgeIcons.Heart) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepEntryCard(session: SleepSession, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val bed = session.start.atZone(zone).toLocalTime().format(clockFormatter())
    val wake = session.end.atZone(zone).toLocalTime().format(clockFormatter())
    Card(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        EntryHeader(
            icon = { IconBadge(ResurgeIcons.Bed, tint = Resurge.colors.sleep, size = 40.dp) },
            title = "Slept ${Formatters.duration(session.asleep)}",
            subtitle = "$bed – $wake",
        )
        val stages = listOf(SleepStage.DEEP to "Deep", SleepStage.REM to "REM", SleepStage.LIGHT to "Light")
            .mapNotNull { (stage, label) -> session.stages[stage]?.let { "$label ${Formatters.duration(it)}" } }
        if (stages.isNotEmpty() || session.quality != null) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stages.forEach { MetricChip(it) }
                session.quality?.let { MetricChip("${(it * 100).roundToInt()}% efficient") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InsightEntryCard(insight: CoachInsight, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ResurgeIcons.Sparkles, contentDescription = null, tint = Resurge.colors.accent, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                when (insight.kind) {
                    InsightKind.WEEKLY -> "Weekly review"
                    InsightKind.SLEEP -> "Sleep insight"
                    InsightKind.ACTIVITY -> "Activity insight"
                },
                style = Resurge.type.caption,
                color = Resurge.colors.accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(insight.title, style = Resurge.type.label)
        Spacer(Modifier.height(4.dp))
        Text(insight.body, style = Resurge.type.body, color = Resurge.colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (insight.highlights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                insight.highlights.forEach { MetricChip("${it.label} ${it.value}") }
            }
        }
    }
}
