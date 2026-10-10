package com.resurgeai.core.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Avatar
import com.resurgeai.core.designsystem.components.BarChart
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.Card
import com.resurgeai.core.designsystem.components.ChoiceCard
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.DotOnAxis
import com.resurgeai.core.designsystem.components.MagnitudeBar
import com.resurgeai.core.designsystem.components.RadialBars
import com.resurgeai.core.designsystem.components.RadialCard
import com.resurgeai.core.designsystem.components.RangeDial
import com.resurgeai.core.designsystem.components.Ring
import com.resurgeai.core.designsystem.components.RingLabel
import com.resurgeai.core.designsystem.components.RingSegment
import com.resurgeai.core.designsystem.components.RangeBar
import com.resurgeai.core.designsystem.components.SegmentsBar
import com.resurgeai.core.designsystem.components.WindowBar
import com.resurgeai.core.designsystem.components.FloatingNavBar
import com.resurgeai.core.designsystem.components.IconButton
import com.resurgeai.core.designsystem.components.NavItem
import com.resurgeai.core.designsystem.components.MetricCard
import com.resurgeai.core.designsystem.components.NumberStepper
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SectionHeader
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.ShareBar
import com.resurgeai.core.designsystem.components.SourceChip
import com.resurgeai.core.designsystem.components.Spinner
import com.resurgeai.core.designsystem.components.Stat
import com.resurgeai.core.designsystem.components.StepIndicator
import com.resurgeai.core.designsystem.components.WeekDots
import com.resurgeai.core.designsystem.theme.Resurge

enum class CatalogSection { Colors, Typography, Buttons, Controls, Metrics, Misc }

/** Every design-system component on one scrolling page. Debug builds link to it from Profile. */
@Composable
fun DesignCatalog(
    modifier: Modifier = Modifier,
    sections: List<CatalogSection> = CatalogSection.entries,
    scrollable: Boolean = true,
) {
    Column(
        modifier
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(16.dp),
    ) {
        sections.forEach { section ->
            SectionHeader(section.name)
            when (section) {
                CatalogSection.Colors -> ColorsSection()
                CatalogSection.Typography -> TypographySection()
                CatalogSection.Buttons -> ButtonsSection()
                CatalogSection.Controls -> ControlsSection()
                CatalogSection.Metrics -> MetricsSection()
                CatalogSection.Misc -> MiscSection()
            }
        }
    }
}

@Composable
private fun ColorsSection() {
    val swatches = listOf(
        "bg" to Resurge.colors.bg, "surface" to Resurge.colors.surface,
        "muted" to Resurge.colors.surfaceMuted, "border" to Resurge.colors.border,
        "text" to Resurge.colors.text, "textMuted" to Resurge.colors.textMuted,
        "accent" to Resurge.colors.accent, "danger" to Resurge.colors.danger,
        "sleep" to Resurge.colors.sleep, "activity" to Resurge.colors.activity,
        "energy" to Resurge.colors.energy, "water" to Resurge.colors.water,
        "heart" to Resurge.colors.heart,
    )
    swatches.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(Resurge.shape.sm)
                .background(color)
                .border(1.dp, Resurge.colors.border, Resurge.shape.sm),
        )
        Text(name, style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun TypographySection() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Display 32", style = Resurge.type.display)
        Text("8,412 metric", style = Resurge.type.metric)
        Text("Title 22", style = Resurge.type.title)
        Text("Headline 17", style = Resurge.type.headline)
        Text("Body 15 — the quick brown fox jumps over the lazy dog.", style = Resurge.type.body)
        Text("Label 14", style = Resurge.type.label)
        Text("Caption 12", style = Resurge.type.caption, color = Resurge.colors.textMuted)
    }
}

@Composable
private fun ButtonsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
        Button("Secondary", onClick = {}, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Ghost", onClick = {}, variant = ButtonVariant.Ghost)
            Button("Danger", onClick = {}, variant = ButtonVariant.Danger, leadingIcon = ResurgeIcons.LogOut)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button("Loading", onClick = {}, loading = true)
            Button("Disabled", onClick = {}, enabled = false)
            IconButton(ResurgeIcons.ArrowRight, contentDescription = "Next", onClick = {})
        }
    }
}

@Composable
private fun ControlsSection() {
    var theme by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(true) }
    var value by remember { mutableIntStateOf(2000) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedControl(
            options = listOf(
                SegmentOption(0, "System", ResurgeIcons.Monitor),
                SegmentOption(1, "Light", ResurgeIcons.Sun),
                SegmentOption(2, "Dark", ResurgeIcons.Moon),
            ),
            selected = theme,
            onSelect = { theme = it },
            modifier = Modifier.fillMaxWidth(),
        )
        ChoiceCard(
            title = "Sleep better",
            description = "Consistent, restful nights",
            selected = selected,
            onClick = { selected = !selected },
            icon = ResurgeIcons.Bed,
            iconTint = Resurge.colors.sleep,
        )
        Card { NumberStepper(value, { value = it }, step = 50, range = 1200..4500, unit = "kcal per day") }
        StepIndicator(current = 1, total = 4)
    }
}

@Composable
private fun MetricsSection() {
    val week = listOf(6200.0, 8100.0, null, 9400.0, 7300.0, 10200.0, 8412.0)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                title = "Steps", icon = ResurgeIcons.Footprints, tint = Resurge.colors.activity,
                value = "8,412", sparkline = week,
                footer = { SourceChip("Galaxy Watch", ResurgeIcons.Watch) },
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Sleep", icon = ResurgeIcons.Bed, tint = Resurge.colors.sleep,
                value = null, subtitle = "Connect a wearable",
                modifier = Modifier.weight(1f),
            )
        }
        Card {
            BarChart(
                values = week,
                labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                color = Resurge.colors.activity,
                target = 8000.0,
            )
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exercise days", style = Resurge.type.label, modifier = Modifier.weight(1f))
                WeekDots(listOf(true, false, true, true, false, false, false), Resurge.colors.activity, todayIndex = 4)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Stat("Average", "8,270", Modifier.weight(1f))
                Stat("Best", "10,200", Modifier.weight(1f))
                Stat("Total", "49,612", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            ShareBar(0.72f, Resurge.colors.activity)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RadialCard("Steps", ResurgeIcons.Footprints, Resurge.colors.activity, Modifier.weight(1f), subtitle = "74% of goal") { m ->
                Ring(listOf(RingSegment(0.74f, Resurge.colors.activity)), m) { RingLabel("7,412", "of 10k") }
            }
            RadialCard("Distance", ResurgeIcons.Distance, Resurge.colors.water, Modifier.weight(1f), subtitle = "7-day avg 5.6 km") { m ->
                RadialBars(listOf(4.1, 6.3, 2.2, 7.8, 5.0, 8.4, 5.4), Resurge.colors.water, m) { RingLabel("5.43", "km") }
            }
        }
        Card(inverse = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inverse hero card", style = Resurge.type.headline, color = Resurge.colors.onInverse, modifier = Modifier.weight(1f))
                RangeDial(0.07f, 0.77f, 0.11f, Resurge.colors.heart, Modifier.width(96.dp), trackColor = Resurge.colors.onInverse.copy(alpha = 0.08f), markerColor = Resurge.colors.onInverse, markerRing = Resurge.colors.inverse)
            }
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MagnitudeBar(0.8f, Resurge.colors.activity, marker = 0.6f)
                WindowBar(
                    start = 0.28f, end = 0.7f,
                    segments = listOf(
                        0.25f to Resurge.colors.sleep,
                        0.5f to Resurge.colors.sleep.copy(alpha = 0.42f),
                        0.2f to Resurge.colors.sleep.copy(alpha = 0.7f),
                        0.05f to Resurge.colors.textSubtle,
                    ),
                    gridLines = listOf(1f / 3, 2f / 3),
                )
                RangeBar(0.1f, 0.85f, 0.3f, Resurge.colors.heart)
                SegmentsBar(listOf(0.5f, 0.25f), Resurge.colors.activity)
                DotOnAxis(0.4f, Resurge.colors.textMuted)
            }
        }
    }
}

@Composable
private fun MiscSection() {
    FloatingNavBar(
        items = listOf(
            NavItem(0, "Today", ResurgeIcons.Today, ResurgeIcons.TodayFilled),
            NavItem(1, "Fitness", ResurgeIcons.Fitness, ResurgeIcons.FitnessFilled),
            NavItem(2, "Food", ResurgeIcons.Food, ResurgeIcons.FoodFilled),
        ),
        selected = 0,
        onSelect = {},
        action = NavItem(Unit, "Coach", ResurgeIcons.CoachFilled),
    )
    Spacer(Modifier.height(12.dp))
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar("Shashank Bhat", photoUrl = null, size = 44.dp)
            Avatar(null, photoUrl = null, size = 32.dp)
            Spinner(Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            SourceChip("Phone", ResurgeIcons.Phone)
        }
        Spacer(Modifier.height(12.dp))
        Divider()
    }
}
