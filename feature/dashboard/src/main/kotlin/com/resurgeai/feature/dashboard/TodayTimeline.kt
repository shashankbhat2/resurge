package com.resurgeai.feature.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.ActivityVisuals
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.Workout
import com.resurgeai.core.model.WorkoutSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Where timeline cards lead. */
class FeedActions(
    val onOpenMeal: (String) -> Unit = {},
    val onOpenWorkout: (String) -> Unit = {},
    val onOpenSleep: () -> Unit = {},
    val onOpenInsights: () -> Unit = {},
)

// --- Feed ---

/** Day groups of flat rows, newest first: meals, workouts, sleep and coach notes. */
@Composable
internal fun Feed(days: List<FeedDay>, today: LocalDate, units: UnitSystem, commander: Boolean, actions: FeedActions) {
    if (days.isEmpty()) {
        TodaySection("Today")
        Text(
            "Meals, workouts and sleep show up here as you log them or your devices sync.",
            style = Resurge.type.body,
            color = Resurge.colors.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    days.forEach { day ->
        TodaySection(dayLabel(day.date, today), trailing = "${day.items.size} ${if (day.items.size == 1) "entry" else "entries"}")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            day.items.forEach { item ->
                when (item) {
                    is FeedItem.MealEntry -> MealRow(item.meal) { actions.onOpenMeal(item.meal.id) }
                    is FeedItem.WorkoutEntry -> WorkoutRow(item.workout, units) { actions.onOpenWorkout(item.workout.id) }
                    is FeedItem.SleepEntry -> SleepRow(item.session, actions.onOpenSleep)
                    is FeedItem.InsightEntry -> CoachNote(item.insight, commander, actions.onOpenInsights)
                }
            }
        }
    }
}

@Composable
private fun clock(time: Instant): String = time.atZone(ZoneId.systemDefault()).toLocalTime().format(clockFormatter())

/** One feed entry: a 40dp icon tile, title and caption, and the value on the right. */
@Composable
private fun FeedRow(
    icon: @Composable () -> Unit,
    title: String,
    caption: String,
    value: String?,
    detail: String?,
    onClick: () -> Unit,
) {
    UnstyledButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface),
        indication = LocalIndication.current,
        contentAlignment = Alignment.TopStart,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(caption, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (value != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight, fontSize = Resurge.type.body.fontSize, fontFeatureSettings = "tnum"), maxLines = 1)
                    detail?.let { Text(it, style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: Int, tint: Color) {
    Box(
        Modifier.size(40.dp).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, size = 22.dp) }
}

@Composable
private fun MealRow(meal: Meal, onClick: () -> Unit) {
    val t = meal.totals
    FeedRow(
        icon = {
            val thumb = meal.thumbnail
            val bitmap = remember(thumb) { thumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            if (bitmap != null) {
                Image(bitmap, null, Modifier.size(40.dp).clip(Resurge.shape.sm), contentScale = ContentScale.Crop)
            } else {
                IconTile(ResurgeIcons.Bowl, Resurge.colors.energy)
            }
        },
        title = meal.title,
        caption = "${meal.type.label} · ${clock(meal.loggedAt)}",
        value = Formatters.kcal(t.kcal),
        detail = "kcal",
        onClick = onClick,
    )
}

@Composable
private fun WorkoutRow(w: Workout, units: UnitSystem, onClick: () -> Unit) {
    val activity = w.activity
    val origin = if (w.source == WorkoutSource.MANUAL) "Logged" else w.sourceName ?: "Health Connect"
    val distance = (w.metrics.distanceM ?: FitnessMath.swimDistanceM(w.metrics))?.let { m ->
        if (units == UnitSystem.IMPERIAL) String.format(Locale.getDefault(), "%.2f mi", m / FitnessMath.M_PER_MI)
        else "${Formatters.kilometres(m)} km"
    }
    FeedRow(
        icon = { IconTile(ActivityVisuals.icon(activity.icon), ActivityVisuals.color(activity.category)) },
        title = w.displayName,
        caption = "$origin · ${clock(w.start)}",
        value = Formatters.duration(w.duration),
        detail = distance
            ?: w.metrics.exercises.takeIf { it.isNotEmpty() }?.let { ex -> "${ex.sumOf { it.sets.size }} sets" }
            ?: w.metrics.kcal?.let { "$it kcal" },
        onClick = onClick,
    )
}

@Composable
private fun SleepRow(session: SleepSession, onClick: () -> Unit) {
    FeedRow(
        icon = { IconTile(ResurgeIcons.Bed, Resurge.colors.sleep) },
        title = "Sleep",
        caption = "${clock(session.start)} – ${clock(session.end)}",
        value = Formatters.duration(session.asleep),
        detail = session.stages[SleepStage.DEEP]?.let { "${Formatters.duration(it)} deep" }
            ?: session.quality?.let { "${(it * 100).roundToInt()}% efficient" },
        onClick = onClick,
    )
}
