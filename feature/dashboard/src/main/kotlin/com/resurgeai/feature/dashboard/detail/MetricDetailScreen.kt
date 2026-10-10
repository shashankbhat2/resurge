package com.resurgeai.feature.dashboard.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.Stat
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.feature.dashboard.Formatters
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MetricDetailRoute(
    onBack: () -> Unit,
    onOpenSources: (HealthMetric) -> Unit,
    viewModel: MetricDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MetricDetailScreen(
        state = state,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        onSelect = viewModel::select,
        onOpenSources = { onOpenSources(state.metric) },
    )
}

@Composable
fun MetricDetailScreen(
    state: MetricDetailUiState,
    onBack: () -> Unit,
    onRangeChange: (DetailRange) -> Unit,
    onSelect: (Int) -> Unit,
    onOpenSources: () -> Unit,
) {
    val color = metricColor(state.metric)
    val zone = ZoneId.systemDefault()
    val selected = state.periods.getOrNull(state.selected)

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = state.metric.label,
            onBack = onBack,
            trailing = {
                IconButton(ResurgeIcons.Devices, contentDescription = "Data sources", onClick = onOpenSources)
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SegmentedControl(
                options = DetailRange.entries.map { SegmentOption(it, it.label) },
                selected = state.range,
                onSelect = onRangeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.error != null -> Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(state.error, style = Resurge.type.body, color = Resurge.colors.textMuted)
                }
                state.series == null -> Row(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { Spinner() }
                else -> {
                    SummaryCard(state)
                    Spacer(Modifier.height(12.dp))
                    PeriodList(state, color, zone, onSelect)
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                val nights = selected?.let { p -> state.sleepSessions.filter { it.end.atZone(zone).toLocalDate() in p } }.orEmpty()
                if (nights.isNotEmpty()) {
                    SectionHeader("Nights · ${periodTitle(selected!!, state.range)}")
                    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        nights.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            SessionRow(
                                title = s.end.atZone(zone).format(DAY),
                                subtitle = "${s.start.atZone(zone).format(TIME)} – ${s.end.atZone(zone).format(TIME)}",
                                value = Formatters.duration(s.duration),
                                source = s.source,
                            )
                        }
                    }
                }

                val workouts = selected?.let { p -> state.exerciseSessions.filter { it.start.atZone(zone).toLocalDate() in p } }.orEmpty()
                if (workouts.isNotEmpty()) {
                    SectionHeader("Workouts · ${periodTitle(selected!!, state.range)}")
                    Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                        workouts.forEachIndexed { i, s ->
                            if (i > 0) Divider()
                            SessionRow(
                                title = s.title ?: s.type,
                                subtitle = s.start.atZone(zone).format(DAY_TIME),
                                value = Formatters.duration(s.duration),
                                source = s.source,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SummaryCard(state: MetricDetailUiState) {
    val metric = state.metric
    val series = state.series ?: return
    val values = series.nonNull
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text("Daily average", style = Resurge.type.caption, color = Resurge.colors.textMuted)
        Text(series.average?.let { Formatters.seriesValue(metric, it) } ?: "—", style = Resurge.type.display)
        if (values.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row {
                when (metric) {
                    HealthMetric.HEART_RATE, HealthMetric.WEIGHT -> {
                        Stat("Lowest", Formatters.seriesValue(metric, values.min()), Modifier.weight(1f))
                        Stat("Highest", Formatters.seriesValue(metric, values.max()), Modifier.weight(1f))
                    }
                    HealthMetric.SLEEP -> {
                        Stat("Shortest", Formatters.seriesValue(metric, values.min()), Modifier.weight(1f))
                        Stat("Longest", Formatters.seriesValue(metric, values.max()), Modifier.weight(1f))
                    }
                    else -> {
                        Stat("Best day", Formatters.seriesValue(metric, values.max()), Modifier.weight(1f))
                        Stat("Total", Formatters.seriesValue(metric, values.sum()), Modifier.weight(1f))
                    }
                }
                Stat("Days with data", "${values.size}/${series.points.size}", Modifier.weight(1f))
            }
        }
    }
}

private fun periodTitle(period: PeriodValue, range: DetailRange): String {
    val today = LocalDate.now()
    return when (range) {
        DetailRange.WEEK -> if (period.start == today) "Today" else period.start.format(DAY_SHORT)
        DetailRange.MONTH -> when {
            today in period -> "This week"
            period.start.month == period.end.month -> "${period.start.dayOfMonth}–${period.end.format(SHORT_DATE)}"
            else -> "${period.start.format(SHORT_DATE)} – ${period.end.format(SHORT_DATE)}"
        }
    }
}

private operator fun PeriodValue.contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

@Composable
private fun SessionRow(title: String, subtitle: String, value: String, source: DataSource?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Resurge.type.label)
            Text(
                listOfNotNull(subtitle, source?.let(Formatters::sourceLabel)).joinToString(" · "),
                style = Resurge.type.caption,
                color = Resurge.colors.textMuted,
            )
        }
        Text(value, style = Resurge.type.headline)
    }
}

@Composable
internal fun metricColor(metric: HealthMetric): Color = when (metric) {
    HealthMetric.STEPS, HealthMetric.EXERCISE -> Resurge.colors.activity
    HealthMetric.DISTANCE -> Resurge.colors.water
    HealthMetric.CALORIES_BURNED -> Resurge.colors.energy
    HealthMetric.SLEEP -> Resurge.colors.sleep
    HealthMetric.HEART_RATE -> Resurge.colors.heart
    HealthMetric.WEIGHT -> Resurge.colors.textMuted
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")
private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE d")
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
