package com.resurgeai.feature.dashboard.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.IconBadge
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.ShareBar
import com.resurgeai.core.designsystem.components.SkeletonBox
import com.resurgeai.core.designsystem.components.SkeletonCircle
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.SourceKind
import com.resurgeai.core.model.SourceShare
import com.resurgeai.feature.dashboard.Formatters
import com.resurgeai.feature.dashboard.detail.metricColor
import com.resurgeai.feature.dashboard.sourceIcon
import kotlin.math.roundToInt

@Composable
fun MetricSourcesRoute(
    onBack: () -> Unit,
    viewModel: MetricSourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MetricSourcesScreen(
        state = state,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        onManage = { runCatching { context.startActivity(viewModel.healthConnectSettingsIntent()) } },
    )
}

@Composable
fun MetricSourcesScreen(
    state: MetricSourcesUiState,
    onBack: () -> Unit,
    onRangeChange: (SourceRange) -> Unit,
    onManage: () -> Unit,
) {
    val color = metricColor(state.metric)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "${state.metric.label} sources", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SegmentedControl(
                options = SourceRange.entries.map { SegmentOption(it, it.label) },
                selected = state.range,
                onSelect = onRangeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> SkeletonGroup {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(2) {
                                Card(Modifier.weight(1f)) {
                                    SkeletonCircle(32.dp)
                                    Spacer(Modifier.height(12.dp))
                                    SkeletonLine(56.dp, 22.dp)
                                    Spacer(Modifier.height(8.dp))
                                    SkeletonLine(64.dp)
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            repeat(2) { i ->
                                if (i > 0) Spacer(Modifier.height(18.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SkeletonCircle(32.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SkeletonLine(120.dp)
                                        SkeletonLine(70.dp, 10.dp)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                SkeletonBox(Modifier.fillMaxWidth().height(6.dp), Resurge.shape.pill)
                            }
                        }
                    }
                }
                state.error != null -> Card(Modifier.fillMaxWidth()) {
                    Text(state.error, style = Resurge.type.body, color = Resurge.colors.textMuted)
                }
                state.breakdown.shares.isEmpty() -> Card(Modifier.fillMaxWidth()) {
                    Text("No ${state.metric.label.lowercase()} data for this period.", style = Resurge.type.body, color = Resurge.colors.textMuted)
                }
                else -> {
                    KindSummary(state.breakdown.shares, color)
                    SectionHeader("By device & app")
                    Card(Modifier.fillMaxWidth()) {
                        state.breakdown.shares.forEachIndexed { i, share ->
                            if (i > 0) Spacer(Modifier.height(16.dp))
                            SourceRow(share, color)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Shares show who recorded the data. When your phone and watch both record the same thing, " +
                    "Health Connect counts it once using your app priority order.",
                style = Resurge.type.caption,
                color = Resurge.colors.textSubtle,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                "Manage sources in Health Connect",
                onClick = onManage,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Phone vs wearable vs manual at a glance. */
@Composable
private fun KindSummary(shares: List<SourceShare>, color: Color) {
    val byKind = shares.groupBy { it.source.kind }
        .mapValues { (_, s) -> s.sumOf { it.fraction.toDouble() }.toFloat() }
        .entries
        .sortedByDescending { it.value }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        byKind.take(3).forEach { (kind, fraction) ->
            Card(Modifier.weight(1f)) {
                IconBadge(sourceIcon(kind), tint = color, size = 32.dp)
                Spacer(Modifier.height(10.dp))
                Text("${(fraction * 100).roundToInt()}%", style = Resurge.type.title)
                Text(Formatters.kindLabel(kind), style = Resurge.type.caption, color = Resurge.colors.textMuted)
            }
        }
    }
}

@Composable
private fun SourceRow(share: SourceShare, color: Color) {
    val source = share.source
    val label = Formatters.sourceLabel(source)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(sourceIcon(source.kind), tint = Resurge.colors.textMuted, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = Resurge.type.label)
                Text(
                    listOfNotNull(
                        Formatters.kindLabel(source.kind),
                        source.appName?.takeIf { it != label },
                        source.packageName.takeIf { source.appName == null && source.kind == SourceKind.UNKNOWN },
                    ).joinToString(" · "),
                    style = Resurge.type.caption,
                    color = Resurge.colors.textMuted,
                )
            }
            Text("${(share.fraction * 100).roundToInt()}%", style = Resurge.type.label, color = Resurge.colors.textMuted)
        }
        Spacer(Modifier.height(8.dp))
        ShareBar(share.fraction, color)
    }
}
