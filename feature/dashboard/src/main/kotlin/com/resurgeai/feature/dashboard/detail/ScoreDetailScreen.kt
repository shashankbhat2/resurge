package com.resurgeai.feature.dashboard.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Button
import com.resurgeai.core.designsystem.components.ButtonVariant
import com.resurgeai.core.designsystem.components.CoachBadge
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.SegmentOption
import com.resurgeai.core.designsystem.components.SegmentedControl
import com.resurgeai.core.designsystem.components.SectionLabel
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.healthconnect.HealthConnectManager
import com.resurgeai.core.healthconnect.HealthPermissions
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.PaceSignal
import com.resurgeai.core.model.Score
import com.resurgeai.core.model.ScoreKind
import com.resurgeai.core.model.ScoreMath
import com.resurgeai.core.model.ScorePart
import com.resurgeai.feature.dashboard.ScoreHistory
import com.resurgeai.feature.dashboard.ScorePeriod
import com.resurgeai.feature.dashboard.ScoreRing
import com.resurgeai.feature.dashboard.ScoresEngine
import com.resurgeai.feature.dashboard.previous
import com.resurgeai.feature.dashboard.range
import com.resurgeai.feature.dashboard.scoreColor
import com.resurgeai.feature.dashboard.scores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class ScoreDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val engine: ScoresEngine,
    private val healthConnect: HealthConnectManager,
) : ViewModel() {
    val kind: ScoreKind = ScoreKind.fromName(savedStateHandle.get<String>(ARG_KIND)) ?: ScoreKind.RECOVERY
    private val _period = MutableStateFlow(ScorePeriod.fromName(savedStateHandle.get<String>(ARG_PERIOD)))
    val period: StateFlow<ScorePeriod> = _period.asStateFlow()
    val history: StateFlow<ScoreHistory> = engine.state

    val permissionContract = healthConnect.permissionContract()

    fun setPeriod(p: ScorePeriod) { _period.value = p }

    fun refresh(force: Boolean = false) = engine.refresh(force)

    companion object {
        /** Must match the property names in the navigation route. */
        const val ARG_KIND = "kind"
        const val ARG_PERIOD = "period"
    }
}

@Composable
fun ScoreDetailRoute(
    onBack: () -> Unit,
    onOpenMetric: (HealthMetric) -> Unit,
    onOpenScore: (ScoreKind) -> Unit,
    viewModel: ScoreDetailViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val hrvLauncher = rememberLauncherForActivityResult(viewModel.permissionContract) { viewModel.refresh(force = true) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    ScoreDetailScreen(
        kind = viewModel.kind,
        period = period,
        history = history,
        onBack = onBack,
        onPeriodChange = viewModel::setPeriod,
        onOpenMetric = onOpenMetric,
        onOpenScore = onOpenScore,
        onAllowHrv = { hrvLauncher.launch(HealthPermissions.OPTIONAL) },
    )
}

/**
 * One score in depth. Today: the ring, why, and each base metric's push on it. Week: a bar a day
 * (Effort against its target band). Month: a calendar of days. Week and Month list each base metric's
 * average against the period before.
 */
@Composable
fun ScoreDetailScreen(
    kind: ScoreKind,
    period: ScorePeriod,
    history: ScoreHistory,
    onBack: () -> Unit,
    onPeriodChange: (ScorePeriod) -> Unit,
    onOpenMetric: (HealthMetric) -> Unit = {},
    onOpenScore: (ScoreKind) -> Unit = {},
    onAllowHrv: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = kind.label, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SegmentedControl(
                options = ScorePeriod.entries.map { SegmentOption(it, it.label) },
                selected = period,
                onSelect = onPeriodChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            if (!history.loaded) {
                Text("Working out your scores…", style = Resurge.type.body, color = Resurge.colors.textMuted, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                val open: (ScorePart) -> Unit = { p -> p.metric?.let(onOpenMetric) ?: p.opens?.let(onOpenScore) }
                when (period) {
                    ScorePeriod.TODAY -> history.latest?.get(kind)?.let { DayView(it, kind == ScoreKind.RECOVERY && !history.hrvAllowed, open, onAllowHrv) }
                    ScorePeriod.WEEK, ScorePeriod.MONTH -> PeriodView(kind, period, history, open)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DayView(score: Score, offerHrv: Boolean, onOpen: (ScorePart) -> Unit, onAllowHrv: () -> Unit) {
    val color = scoreColor(score.signal)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScoreRing(
            score.value, color, size = 168.dp, stroke = 11.dp,
            numberStyle = Resurge.type.tide.copy(fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-1.6).sp),
            caption = if (score.value != null) "of 100" else null,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (score.soFar && score.kind == ScoreKind.EFFORT) "So far · ${score.headline}" else score.headline,
            style = Resurge.type.headline,
            color = if (score.signal == null) Resurge.colors.textMuted else color,
        )
        Spacer(Modifier.height(6.dp))
        Text(score.reason, style = Resurge.type.body, color = Resurge.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
        score.band?.let { band ->
            Spacer(Modifier.height(6.dp))
            Text("Today’s target ${band.first}–${band.last}", style = Resurge.type.label, color = Resurge.colors.textSubtle)
        }
        if (score.learning) {
            Spacer(Modifier.height(6.dp))
            Text("Still learning your normal; this firms up after a week.", style = Resurge.type.caption, color = Resurge.colors.textSubtle, textAlign = TextAlign.Center)
        }
    }
    val parts = score.parts.filter { it.level != null }
    if (parts.isNotEmpty()) {
        SectionLabel("What made it", trailing = "Points vs an ordinary day")
        Surface {
            parts.forEachIndexed { i, p ->
                if (i > 0) RowDivider()
                PartRow(p, soFar = score.soFar, onOpen = onOpen)
            }
            if (offerHrv) {
                RowDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Heart rate variability", style = Resurge.type.label, color = Resurge.colors.text)
                        Text("Not allowed yet. Adds to Recovery if your watch records it.", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                    }
                    Button("Allow", onClick = onAllowHrv, variant = ButtonVariant.Secondary)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Each bar is how far that part moved the score from an ordinary day. Tap one for its own history.",
            style = Resurge.type.caption,
            color = Resurge.colors.textSubtle,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PartRow(p: ScorePart, soFar: Boolean, onOpen: (ScorePart) -> Unit) {
    val linked = p.metric != null || p.opens != null
    val impact = p.impact
    // Coloured by which way it pushed; before the day's done a shortfall isn't a verdict, so it stays grey.
    val color = when {
        impact > 0 -> Resurge.colors.up
        impact == 0 || soFar -> Resurge.colors.textSubtle
        impact <= -5 -> Resurge.colors.down
        else -> Resurge.colors.mid
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.label, style = Resurge.type.label, color = Resurge.colors.text, modifier = Modifier.weight(1f))
                Text(partValue(p), style = Resurge.type.label.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.text)
                if (linked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.note, style = Resurge.type.caption, color = color, modifier = Modifier.weight(1f))
                ImpactBar(impact, color)
                Text(
                    when { impact > 0 -> "+$impact"; impact < 0 -> "−${-impact}"; else -> "0" },
                    style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                    color = color,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
            }
        }
    }
    if (linked) {
        UnstyledButton(onClick = { onOpen(p) }, indication = LocalIndication.current, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${p.label}, ${partValue(p)}. ${p.note}. Open" }) { body() }
    } else body()
}

/** A bar centred on zero: right for points gained, left for points lost. */
@Composable
private fun ImpactBar(points: Int, color: Color) {
    val track = Resurge.colors.border
    val tick = Resurge.colors.borderStrong
    Canvas(Modifier.width(88.dp).height(12.dp)) {
        val mid = size.width / 2
        val h = 6.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(track, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
        val len = (abs(points) * 4.dp.toPx()).coerceAtMost(mid)
        if (len > 0) drawRoundRect(color, Offset(if (points > 0) mid else mid - len, top), Size(len, h), CornerRadius(h / 2))
        drawRect(tick, Offset(mid - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

@Composable
private fun PeriodView(kind: ScoreKind, period: ScorePeriod, history: ScoreHistory, onOpen: (ScorePart) -> Unit) {
    val today = history.today
    val range = period.range(today)
    val days = history.scores(kind, range)
    val before = history.scores(kind, period.previous(today))
    val versus = if (period == ScorePeriod.WEEK) "last week" else range.start.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val avg = ScoreMath.average(days)
    val prevAvg = ScoreMath.average(before)
    val scored = ScoreMath.scoredDays(days)
    val good = ScoreMath.goodDays(days)

    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(periodTitle(period, range), style = Resurge.type.overline, color = Resurge.colors.textSubtle)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(avg?.toString() ?: "–", style = Resurge.type.tide.copy(fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-1.6).sp, fontFeatureSettings = "tnum"))
                Spacer(Modifier.width(8.dp))
                Text("average", style = Resurge.type.body, color = Resurge.colors.textMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (scored > 0) {
                Text(
                    "$good of $scored days ${if (kind == ScoreKind.EFFORT) "in range" else "good"}",
                    style = Resurge.type.label,
                    color = scoreColor(if (good * 3 >= scored * 2) PaceSignal.UP else if (good * 3 >= scored) PaceSignal.MID else PaceSignal.DOWN),
                )
            }
        }
        if (avg != null && prevAvg != null) {
            val d = avg - prevAvg
            Text(
                "${if (d >= 0) "+" else "−"}${abs(d)} vs $versus",
                style = Resurge.type.label,
                color = when { d > 2 -> Resurge.colors.up; d < -2 -> Resurge.colors.down; else -> Resurge.colors.textSubtle },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Surface(Modifier.padding(14.dp)) {
        if (period == ScorePeriod.WEEK) WeekBars(kind, range.start, today, history.scores(kind, range.start..range.start.plusDays(6)))
        else MonthGrid(range.start, today, days)
    }
    patternNote(kind, period, days)?.let { note ->
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(Resurge.shape.md).border(1.dp, Resurge.colors.border, Resurge.shape.md).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoachBadge(size = 28.dp)
            Text(note, style = Resurge.type.body, color = Resurge.colors.textMuted)
        }
    }

    val finished = days.filter { !it.soFar }
    val keys = finished.flatMap { d -> d.parts.filter { it.level != null }.map { it.key } }.distinct()
    if (keys.isNotEmpty()) {
        SectionLabel("What made it this ${if (period == ScorePeriod.WEEK) "week" else "month"}", trailing = "Daily average")
        Surface {
            keys.forEachIndexed { i, key ->
                if (i > 0) RowDivider()
                PeriodPartRow(kind, key, finished, before, versus, onOpen)
            }
        }
    }
}

@Composable
private fun PeriodPartRow(kind: ScoreKind, key: String, days: List<Score>, before: List<Score>, versus: String, onOpen: (ScorePart) -> Unit) {
    val parts = days.mapNotNull { d -> d.parts.firstOrNull { it.key == key && it.level != null } }
    val sample = parts.last()
    val avgRaw = parts.mapNotNull { it.raw }.average()
    val avgLevel = parts.mapNotNull { it.level }.average()
    val onTarget = parts.count { (it.level ?: 0.0) >= 0.8 }
    val prev = before.mapNotNull { d -> d.parts.firstOrNull { it.key == key && it.level != null }?.raw }.takeIf { it.isNotEmpty() }?.average()
    // Effort aims for a band, not the most, so its parts are drawn neutral; the others by how close they came.
    val color = if (kind == ScoreKind.EFFORT) Resurge.colors.textMuted else scoreColor(sample.copy(level = avgLevel).signal)
    val linked = sample.metric != null || sample.opens != null
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sample.label, style = Resurge.type.label, color = Resurge.colors.text, modifier = Modifier.weight(1f))
                Text(if (key == "session") "${parts.count { (it.raw ?: 0.0) >= 1 }} of ${parts.size}" else partValue(sample.copy(raw = avgRaw)), style = Resurge.type.label.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.text)
                sample.target?.let { t ->
                    if (sample.key != "session") Text(" / ${sample.unit.format(t)}", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
                }
                if (linked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(ResurgeIcons.ChevronRight, contentDescription = null, tint = Resurge.colors.textSubtle, size = 16.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.border)) {
                    Box(Modifier.fillMaxWidth(avgLevel.toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().clip(Resurge.shape.pill).background(color))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (key == "load") "${parts.count { (it.raw ?: 0.0) > 0 }} workout days" else "$onTarget of ${parts.size} days on target",
                    style = Resurge.type.caption,
                    color = color,
                )
            }
            prev?.let { p ->
                val d = avgRaw - p
                if (sample.key != "session" && abs(d) >= 0.5) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${if (d > 0) "+" else "−"}${sample.unit.format(abs(d))} vs $versus",
                        style = Resurge.type.caption,
                        color = Resurge.colors.textSubtle,
                    )
                }
            }
        }
    }
    if (linked) {
        UnstyledButton(onClick = { onOpen(sample) }, indication = LocalIndication.current, modifier = Modifier.fillMaxWidth()) { body() }
    } else body()
}

/** Monday to Sunday, one bar each. Effort shows its target band behind the bar; today is outlined in the accent. */
@Composable
private fun WeekBars(kind: ScoreKind, start: LocalDate, today: LocalDate, days: List<Score>) {
    val byDate = days.associateBy { it.date }
    val accent = Resurge.colors.accent
    val bandFill = Resurge.colors.surfaceMuted
    val bandLine = Resurge.colors.borderStrong
    val colors = (0 until 7).map { i -> byDate[start.plusDays(i.toLong())]?.let { scoreColor(it.signal) } ?: Color.Transparent }
    Column {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val gap = 10.dp.toPx()
            val col = (size.width - gap * 6) / 7
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            (0 until 7).forEach { i ->
                val date = start.plusDays(i.toLong())
                val s = byDate[date]
                val x = i * (col + gap)
                fun y(v: Int) = size.height * (1 - v / 100f)
                if (kind == ScoreKind.EFFORT) s?.band?.let { b ->
                    val top = y(b.last)
                    val h = y(b.first) - top
                    drawRoundRect(bandFill, Offset(x, top), Size(col, h), CornerRadius(6.dp.toPx()))
                    drawRoundRect(bandLine, Offset(x, top), Size(col, h), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = dash))
                }
                val v = s?.value ?: return@forEach
                val inset = 6.dp.toPx()
                val top = y(v)
                if (date == today) {
                    val w = 2.dp.toPx()
                    drawRoundRect(accent, Offset(x + inset + w / 2, top + w / 2), Size(col - inset * 2 - w, size.height - top - w), CornerRadius(5.dp.toPx()), style = Stroke(w))
                } else {
                    drawRoundRect(colors[i], Offset(x + inset, top), Size(col - inset * 2, size.height - top), CornerRadius(5.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            (0 until 7).forEach { i ->
                val date = start.plusDays(i.toLong())
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Resurge.type.caption,
                    color = if (date == today) Resurge.colors.accent else Resurge.colors.textSubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (kind == ScoreKind.EFFORT) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 12.dp, height = 8.dp).border(1.dp, Resurge.colors.borderStrong, Resurge.shape.sm))
                Spacer(Modifier.width(6.dp))
                Text("Target for the day", style = Resurge.type.caption, color = Resurge.colors.textSubtle)
            }
        }
    }
}

/** The month as a calendar, each finished day filled with its colour; today outlined in the accent. */
@Composable
private fun MonthGrid(first: LocalDate, today: LocalDate, days: List<Score>) {
    val byDate = days.associateBy { it.date }
    val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val length = first.lengthOfMonth()
    val cells = List(lead) { null } + (1..length).map { first.withDayOfMonth(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach {
                Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = Resurge.type.caption, color = Resurge.colors.textSubtle, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { i ->
                    val date = week.getOrNull(i)
                    val s = date?.let { byDate[it] }
                    val fill = s?.takeIf { it.value != null && !it.soFar && date != today }?.let { scoreColor(it.signal) }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(Resurge.shape.sm)
                            .then(
                                when {
                                    date == null -> Modifier
                                    date == today -> Modifier.border(2.dp, Resurge.colors.accent, Resurge.shape.sm)
                                    fill != null -> Modifier.background(fill)
                                    else -> Modifier.border(1.dp, Resurge.colors.border, Resurge.shape.sm)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Text(
                                "${date.dayOfMonth}",
                                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                                color = when {
                                    fill != null -> Resurge.colors.bg
                                    date.isAfter(today) -> Resurge.colors.textSubtle.copy(alpha = 0.5f)
                                    else -> Resurge.colors.text
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(Resurge.colors.up, "${ScoreMath.READY}+")
            Legend(Resurge.colors.mid, "${ScoreMath.CAREFUL}–${ScoreMath.READY - 1}")
            Legend(Resurge.colors.down, "Under ${ScoreMath.CAREFUL}")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(Resurge.shape.sm).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Resurge.type.caption, color = Resurge.colors.textSubtle)
    }
}

/**
 * One pattern worth saying. Month: weekends against weekdays when they differ by 10 or more. Week:
 * the lowest finished day and why.
 */
private fun patternNote(kind: ScoreKind, period: ScorePeriod, days: List<Score>): String? {
    val done = days.filter { it.value != null && !it.soFar }
    if (done.size < 3) return null
    if (period == ScorePeriod.MONTH) {
        val (weekend, weekday) = done.partition { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        val we = ScoreMath.average(weekend) ?: return null
        val wd = ScoreMath.average(weekday) ?: return null
        return when {
            wd - we >= 10 -> "Weekends are the gap. ${kind.label} averages $wd on weekdays and $we on Saturdays and Sundays."
            we - wd >= 10 -> "Weekdays are the gap. ${kind.label} averages $we at weekends and $wd on weekdays."
            else -> null
        }
    }
    val low = done.minByOrNull { it.value!! } ?: return null
    if (low.signal == PaceSignal.UP) return null
    return "Low point: ${low.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${low.value}. ${low.reason}"
}

private fun periodTitle(period: ScorePeriod, range: ClosedRange<LocalDate>): String = when (period) {
    ScorePeriod.MONTH -> range.start.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    else -> "${range.start.format(DAY_MONTH)} – ${range.start.plusDays(6).format(DAY_MONTH)}"
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun partValue(p: ScorePart): String = when (p.key) {
    "session" -> when {
        (p.raw ?: 0.0) >= 1 -> "Done"
        (p.raw ?: 0.0) > 0 -> "Partly"
        else -> "Not yet"
    }
    "consistency" -> p.value?.let { "±$it" } ?: "–"
    else -> p.value ?: "–"
}

@Composable
private fun Surface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Resurge.shape.md)
            .background(Resurge.colors.surface)
            .then(modifier),
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Resurge.colors.border))
}
