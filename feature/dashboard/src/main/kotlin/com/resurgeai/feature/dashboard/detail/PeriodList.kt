package com.resurgeai.feature.dashboard.detail

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.DotOnAxis
import com.resurgeai.core.designsystem.components.MagnitudeBar
import com.resurgeai.core.designsystem.components.RangeBar
import com.resurgeai.core.designsystem.components.SegmentsBar
import com.resurgeai.core.designsystem.components.WindowBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.SleepStage
import com.resurgeai.feature.dashboard.Formatters
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Vertical list of periods, newest at the top: one row per day (Week) or per calendar week (Month).
 * Each metric gets its own visual on an axis shared by all rows, so periods can be compared by eye.
 */
@Composable
internal fun PeriodList(
    state: MetricDetailUiState,
    color: Color,
    zone: ZoneId,
    onSelect: (Int) -> Unit,
) {
    val (axis, rows) = remember(state.periods, state.sleepSessions, state.exerciseSessions, state.heartRanges) {
        DayRows.build(state.metric, state.periods, state.sleepSessions, state.exerciseSessions, state.heartRanges, zone)
    }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (axis.labels.isNotEmpty()) AxisHeader(axis.labels)
        rows.forEachIndexed { i, row ->
            if (i > 0) Divider()
            DayRowItem(
                row = row,
                weekly = state.range == DetailRange.MONTH,
                metric = state.metric,
                color = color,
                gridLines = axis.gridLines,
                selected = row.periodIndex == state.selected,
                onClick = { onSelect(row.periodIndex) },
            )
        }
    }
}

private val ROW_PADDING = 16.dp
private val LABEL_COLUMN = 56.dp
private val VALUE_COLUMN = 76.dp

/** Labels pinned to their axis positions, aligned with the visual column of each row. */
@Composable
private fun AxisHeader(labels: List<Pair<String, Float>>) {
    Layout(
        content = {
            labels.forEach { (label, _) ->
                Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle, maxLines = 1)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_PADDING + LABEL_COLUMN, end = ROW_PADDING + VALUE_COLUMN, top = 12.dp),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                // Centre each label on its position, but keep the first/last inside the column.
                val x = (labels[i].second * width - p.width / 2f).toInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                p.place(x, 0)
            }
        }
    }
}

@Composable
private fun DayRowItem(
    row: DayRow,
    weekly: Boolean,
    metric: HealthMetric,
    color: Color,
    gridLines: List<Float>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val isCurrent = today in row.start..row.end
    UnstyledButton(
        onClick = onClick,
        indication = LocalIndication.current,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Resurge.colors.surfaceMuted else Color.Transparent)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_PADDING, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(LABEL_COLUMN)) {
                val caption = when {
                    weekly && isCurrent -> "This wk"
                    weekly -> row.start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    isCurrent -> "Today"
                    else -> row.start.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
                Text(
                    caption,
                    style = Resurge.type.caption,
                    color = if (isCurrent) Resurge.colors.text else Resurge.colors.textMuted,
                    maxLines = 1,
                )
                Text(
                    if (weekly) "${row.start.dayOfMonth}–${row.end.dayOfMonth}" else row.start.dayOfMonth.toString(),
                    style = Resurge.type.headline,
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                if (metric != HealthMetric.HEART_RATE) Viz(row.viz, metric, color, gridLines)
                row.detail?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = Resurge.type.caption, color = Resurge.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.width(VALUE_COLUMN), horizontalAlignment = Alignment.End) {
                Text(
                    row.value?.let { Formatters.seriesValue(metric, it) } ?: "—",
                    style = Resurge.type.label,
                    color = if (row.value != null) Resurge.colors.text else Resurge.colors.textSubtle,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                if (weekly && row.value != null && metric != HealthMetric.WEIGHT) {
                    Text("per day", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                }
            }
        }
    }
}

@Composable
private fun Viz(viz: DayViz, metric: HealthMetric, color: Color, gridLines: List<Float>) {
    when (viz) {
        is DayViz.Magnitude -> MagnitudeBar(viz.fraction, color, marker = viz.marker)
        // One colour for the whole night keeps the list calm; stages live in the day's hypnogram.
        is DayViz.Window -> WindowBar(start = viz.start, end = viz.end, segments = listOf(1f to color))
        is DayViz.Range -> RangeBar(viz.low, viz.high, viz.mid, color)
        is DayViz.Dot -> DotOnAxis(viz.position, color)
        is DayViz.Segments -> SegmentsBar(viz.fractions, color)
        DayViz.Empty -> MagnitudeBar(0f, color)
    }
}
