package com.resurgeai.feature.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.BarChart
import com.resurgeai.core.designsystem.components.Overline
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.Glycemic
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Predicted glycaemic load: the 7 days up to the picked day as dot columns (low/medium line at 80),
 * then that day's meals with their predicted GI and GL. Clearly marked as a prediction, never a measurement.
 */
@Composable
internal fun GlycemicCard(state: FoodUiState) {
    val dates = (6 downTo 0).map { state.selected.minusDays(it.toLong()) }
    val byDay = state.weekDays.associate { d -> d.date to d.meals }
    val dayGl = dates.map { date -> byDay[date]?.sumOf { Glycemic.estimate(it).gl }?.toDouble() }
    val today = byDay[state.selected].orEmpty()
    val todayGl = dayGl.last()?.toInt()
    val name = dayLabel(state.selected, state.today)
    val zone = ZoneId.systemDefault()
    val clock = clockFormatter()

    Column(
        Modifier.fillMaxWidth().clip(Resurge.shape.lg).background(Resurge.colors.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Overline("Glycaemic load", Modifier.weight(1f))
            PredictedTag()
        }
        Text(
            todayGl?.let { "$name ~$it · ${band(Glycemic.dayBand(it))}" } ?: "No meals ${name.lowercase()}",
            style = Resurge.type.headline,
        )
        BarChart(
            values = dayGl,
            labels = dates.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
            color = Resurge.colors.energy,
            target = 80.0,
            chartHeight = 84.dp,
            modifier = Modifier.semantics { contentDescription = "Predicted glycaemic load for the 7 days to ${name.lowercase()}" },
        )
        today.forEach { meal ->
            val e = Glycemic.estimate(meal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${meal.loggedAt.atZone(zone).toLocalTime().format(clock)}  ${meal.type.label}".uppercase(),
                    style = Resurge.type.caption,
                    color = Resurge.colors.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    if (e.gi == null) "GL ~0 · FEW CARBS" else "GI ~${e.gi}  GL ~${e.gl} · ${band(e.band).uppercase()}",
                    style = Resurge.type.caption,
                    color = Resurge.colors.text,
                )
            }
        }
        Text(Glycemic.NOTE, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}

/** The "predicted" chip next to every glycaemic number. */
@Composable
private fun PredictedTag() {
    Row(
        Modifier.clip(Resurge.shape.sm).border(1.dp, Resurge.colors.border, Resurge.shape.sm).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("PREDICTED · MAY NOT BE ACCURATE", style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

private fun band(b: Glycemic.Band) = when (b) {
    Glycemic.Band.LOW -> "low"
    Glycemic.Band.MEDIUM -> "medium"
    Glycemic.Band.HIGH -> "high"
}

