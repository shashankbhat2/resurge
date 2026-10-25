package com.resurgeai.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.SkeletonCircle
import com.resurgeai.core.designsystem.components.SkeletonGroup
import com.resurgeai.core.designsystem.components.SkeletonLine
import com.resurgeai.core.designsystem.components.signalColor
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.PaceSignal
import com.resurgeai.core.model.Score
import com.resurgeai.core.model.ScoreKind
import com.resurgeai.core.model.ScoreMath
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ScorePeriod(val label: String) {
    TODAY("Today"), WEEK("Week"), MONTH("Month");

    companion object {
        fun fromName(name: String?): ScorePeriod = entries.firstOrNull { it.name == name } ?: TODAY
    }
}

/** The days a period covers, ending today: this week from Monday, this month from the 1st. */
internal fun ScorePeriod.range(today: LocalDate): ClosedRange<LocalDate> = when (this) {
    ScorePeriod.TODAY -> today..today
    ScorePeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))..today
    ScorePeriod.MONTH -> today.withDayOfMonth(1)..today
}

/** The same stretch one period earlier (last week, last month), for comparison. */
internal fun ScorePeriod.previous(today: LocalDate): ClosedRange<LocalDate> = when (this) {
    ScorePeriod.TODAY -> today.minusDays(1)..today.minusDays(1)
    ScorePeriod.WEEK -> range(today).start.minusWeeks(1)..range(today).start.minusDays(1)
    ScorePeriod.MONTH -> range(today).start.minusMonths(1)..range(today).start.minusDays(1)
}

internal fun ScoreHistory.scores(kind: ScoreKind, range: ClosedRange<LocalDate>): List<Score> =
    of(kind).filter { it.date in range }

/** What a ring shows for a period: the day's score, or the average with how many days were good. */
internal data class RingValue(val value: Int?, val signal: PaceSignal?, val note: String, val soFar: Boolean)

internal fun ringValue(history: ScoreHistory, kind: ScoreKind, period: ScorePeriod): RingValue {
    if (period == ScorePeriod.TODAY) {
        val s = history.latest?.get(kind) ?: return RingValue(null, null, "Loading", false)
        val note = when {
            s.value == null -> s.missing.orEmpty()
            s.soFar && kind == ScoreKind.EFFORT -> "So far · ${s.headline}"
            else -> s.headline
        }
        return RingValue(s.value, s.signal, note, s.soFar)
    }
    val days = history.scores(kind, period.range(history.today))
    val avg = ScoreMath.average(days) ?: return RingValue(null, null, "No days yet", false)
    val scored = ScoreMath.scoredDays(days)
    val good = ScoreMath.goodDays(days)
    val signal = if (kind == ScoreKind.EFFORT) {
        when {
            scored == 0 -> null
            good * 3 >= scored * 2 -> PaceSignal.UP
            good * 3 >= scored -> PaceSignal.MID
            else -> PaceSignal.DOWN
        }
    } else ScoreMath.band(avg)
    val note = if (scored == 0) "Today so far" else "$good of $scored days ${if (kind == ScoreKind.EFFORT) "in range" else "good"}"
    return RingValue(avg, signal, note, false)
}

/** Ring colour: the signal, or grey while it isn't a verdict yet. */
@Composable
internal fun scoreColor(signal: PaceSignal?): Color = signal?.let { signalColor(it) } ?: Resurge.colors.textSubtle

/** A 0–100 ring with the number in the middle. */
@Composable
internal fun ScoreRing(
    value: Int?,
    color: Color,
    size: Dp,
    stroke: Dp,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val track = Resurge.colors.border
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (value != null && value > 0) {
                drawArc(color, -90f, 360f * value / 100f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.toString() ?: "–", style = numberStyle.copy(fontFeatureSettings = "tnum"), color = if (value == null) Resurge.colors.textSubtle else Resurge.colors.text)
            caption?.let { Text(it, style = Resurge.type.caption, color = Resurge.colors.textSubtle) }
        }
    }
}

/** Today's three scores under the streak, with a Today / Week / Month switch. Each ring opens its detail. */
@Composable
internal fun ScoreRings(
    history: ScoreHistory,
    period: ScorePeriod,
    onPeriodChange: (ScorePeriod) -> Unit,
    onOpen: (ScoreKind) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SegmentedControl(
            options = ScorePeriod.entries.map { SegmentOption(it, it.label) },
            selected = period,
            onSelect = onPeriodChange,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        if (!history.loaded) {
            SkeletonGroup {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScoreKind.entries.forEach { kind -> RingCardSkeleton(kind, Modifier.weight(1f)) }
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreKind.entries.forEach { kind ->
                RingCard(kind, ringValue(history, kind, period), Modifier.weight(1f)) { onOpen(kind) }
            }
        }
    }
}

/** [RingCard]'s shape while the scores are still being worked out. */
@Composable
private fun RingCardSkeleton(kind: ScoreKind, modifier: Modifier) {
    Column(
        modifier
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .semantics { contentDescription = "${kind.label}: loading" }
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkeletonCircle(76.dp)
        Spacer(Modifier.height(8.dp))
        Text(kind.label, style = Resurge.type.label, color = Resurge.colors.text)
        // Two caption lines, the same height as the real card's note.
        Spacer(Modifier.height(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonLine(56.dp, 10.dp)
            SkeletonLine(40.dp, 10.dp)
        }
    }
}

@Composable
private fun RingCard(kind: ScoreKind, ring: RingValue, modifier: Modifier, onClick: () -> Unit) {
    val color = scoreColor(ring.signal)
    UnstyledButton(
        onClick = onClick,
        modifier = modifier
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .semantics { contentDescription = "${kind.label}: ${ring.value ?: "no score"}. ${ring.note}. Open" },
        indication = LocalIndication.current,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScoreRing(ring.value, color, size = 76.dp, stroke = 7.dp, numberStyle = Resurge.type.headline.copy(fontSize = 24.sp, lineHeight = 26.sp))
            Spacer(Modifier.height(8.dp))
            Text(kind.label, style = Resurge.type.label, color = Resurge.colors.text)
            Spacer(Modifier.height(2.dp))
            Text(
                ring.note,
                style = Resurge.type.caption,
                color = if (ring.signal == null) Resurge.colors.textSubtle else color,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
                modifier = Modifier.heightIn(min = 30.dp),
            )
        }
    }
}
