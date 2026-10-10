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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.BarChart
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.ShareBar
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.Stat
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import com.resurgeai.feature.dashboard.Formatters
import com.resurgeai.feature.dashboard.sourceIcon
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MetricDetailRoute(
    onBack: () -> Unit,
    viewModel: MetricDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MetricDetailScreen(
        state = state,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        onManageSources = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } },
    )
}

@Composable
fun MetricDetailScreen(
    state: MetricDetailUiState,
    onBack: () -> Unit,
    onRangeChange: (DetailRange) -> Unit,
    onManageSources: () -> Unit,
) {
    val color = metricColor(state.metric)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = state.metric.label, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SegmentedControl(
                options = DetailRange.entries.map { SegmentOption(it, it.label) },
                selected = state.range,
                onSelect = onRangeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            val series = state.series
            when {
                state.error != null -> Card(Modifier.fillMaxWidth()) {
                    Text(state.error, style = Resurge.type.body, color = Resurge.colors.textMuted)
                }
                series == null -> Row(Modifier.fillMaxWidth().padding(48.dp), horizontalArrangement = Arrangement.Center) { Spinner() }
                else -> ChartCard(state.metric, state.range, series, color)
            }

            if (state.sourcesToday.shares.isNotEmpty()) {
                SectionHeader("Sources today")
                Card(Modifier.fillMaxWidth()) {
                    state.sourcesToday.shares.forEachIndexed { i, share ->
                        if (i > 0) Spacer(Modifier.height(14.dp))
                        SourceRow(share.source, share.fraction, color)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Health Connect combines overlapping phone and watch data using your app priority.",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textSubtle,
                    )
                }
            }

            if (state.sleepSessions.isNotEmpty()) {
                SectionHeader("Nights")
                Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    state.sleepSessions.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        SessionRow(
                            title = s.end.atZone(ZoneId.systemDefault()).format(DAY),
                            subtitle = "${s.start.atZone(ZoneId.systemDefault()).format(TIME)} – ${s.end.atZone(ZoneId.systemDefault()).format(TIME)}",
                            value = Formatters.duration(s.duration),
                            source = s.source,
                        )
                    }
                }
            }

            if (state.exerciseSessions.isNotEmpty()) {
                SectionHeader("Workouts")
                Card(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    state.exerciseSessions.forEachIndexed { i, s ->
                        if (i > 0) Divider()
                        SessionRow(
                            title = s.title ?: s.type,
                            subtitle = s.start.atZone(ZoneId.systemDefault()).format(DAY_TIME),
                            value = Formatters.duration(s.duration),
                            source = s.source,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                "Manage sources in Health Connect",
                onClick = onManageSources,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChartCard(metric: HealthMetric, range: DetailRange, series: MetricSeries, color: Color) {
    val values = series.nonNull
    Card(Modifier.fillMaxWidth()) {
        val headline = series.average?.let { Formatters.seriesValue(metric, it) } ?: "—"
        Text(if (metric == HealthMetric.WEIGHT) "Average" else "Daily average", style = Resurge.type.caption, color = Resurge.colors.textMuted)
        Text(headline, style = Resurge.type.display)
        Spacer(Modifier.height(16.dp))
        BarChart(
            values = series.points.map { it.value },
            labels = series.points.mapIndexed { i, p ->
                when (range) {
                    DetailRange.WEEK -> p.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    DetailRange.MONTH -> if (i % 7 == series.points.lastIndex % 7) p.date.dayOfMonth.toString() else ""
                }
            },
            color = color,
        )
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

@Composable
private fun SourceRow(source: DataSource, fraction: Float, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sourceIcon(source.kind), contentDescription = null, tint = Resurge.colors.textMuted, size = 16.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(Formatters.sourceLabel(source), style = Resurge.type.label)
                val secondary = listOfNotNull(
                    Formatters.kindLabel(source.kind),
                    source.appName?.takeIf { it != Formatters.sourceLabel(source) },
                ).joinToString(" · ")
                Text(secondary, style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
            Text("${(fraction * 100).toInt()}%", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        ShareBar(fraction, color)
    }
}

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
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
