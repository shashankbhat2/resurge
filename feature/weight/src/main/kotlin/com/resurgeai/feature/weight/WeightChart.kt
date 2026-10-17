package com.resurgeai.feature.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.TrendPoint
import com.resurgeai.core.model.WeighIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Weigh-ins as faint dots and the trend as a line with a soft fill, over [span]. A dashed goal line
 * shows when [goalKg] is within reach of the drawn values. With [selection], two handles mark a
 * period; dragging moves the nearer one ([onSelect]).
 */
@Composable
internal fun WeightChart(
    trend: List<TrendPoint>,
    weighIns: List<WeighIn>,
    span: Pair<LocalDate, LocalDate>,
    units: WeightUnits,
    goalKg: Double?,
    modifier: Modifier = Modifier,
    selection: Pair<LocalDate, LocalDate>? = null,
    onSelect: ((LocalDate, LocalDate) -> Unit)? = null,
) {
    val (start, end) = span
    val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
    val points = remember(trend, span) { trend.filter { it.date in start..end } }
    val dots = remember(weighIns, span) { weighIns.filter { it.date() in start..end } }
    val values = points.map { units.toUnit(it.kg) } + dots.map { units.toUnit(it.kg) }
    if (values.isEmpty()) return
    var lo = values.min() - 0.4
    var hi = values.max() + 0.4
    val goal = goalKg?.let(units::toUnit)
    if (goal != null) {
        lo = minOf(lo, goal - 0.4)
        hi = maxOf(hi, goal + 0.4)
    }
    if (hi - lo < 2) {
        val mid = (hi + lo) / 2
        lo = mid - 1
        hi = mid + 1
    }
    val step = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0).first { (hi - lo) / it <= 5 }

    val cBorder = Resurge.colors.border
    val cBand = Resurge.colors.accentMuted
    val cGoal = Resurge.colors.glow
    val cDot = Resurge.colors.textSubtle
    val cLine = Resurge.colors.accent
    val cSurface = Resurge.colors.surface
    val cHandle = Resurge.colors.brandInk
    val label = Resurge.type.caption.copy(color = cDot, fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val latestSelect by rememberUpdatedState(onSelect)
    val latestSelection by rememberUpdatedState(selection)
    var dragging by remember { mutableIntStateOf(-1) }
    var lastDate by remember { mutableStateOf<LocalDate?>(null) }

    val left = 34f
    val bottom = 22f
    val top = 10f
    val interactive = if (selection != null && onSelect != null) {
        Modifier.pointerInput(span) {
            val leftPx = left.dp.toPx()
            fun dateAt(x: Float): LocalDate {
                val frac = ((x - leftPx) / (size.width - leftPx - 6.dp.toPx())).coerceIn(0f, 1f)
                return start.plusDays((frac * days).roundToLong())
            }
            fun xOf(d: LocalDate) = leftPx + ChronoUnit.DAYS.between(start, d).toFloat() / days * (size.width - leftPx - 6.dp.toPx())
            detectDragGestures(
                onDragStart = { pos ->
                    val sel = latestSelection ?: return@detectDragGestures
                    dragging = if (abs(pos.x - xOf(sel.first)) <= abs(pos.x - xOf(sel.second))) 0 else 1
                },
                onDragEnd = { dragging = -1 },
                onDragCancel = { dragging = -1 },
                onDrag = { change, _ ->
                    val sel = latestSelection ?: return@detectDragGestures
                    val d = dateAt(change.position.x)
                    if (d != lastDate) {
                        lastDate = d
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    if (dragging == 0 && d.isBefore(sel.second)) latestSelect?.invoke(d, sel.second)
                    if (dragging == 1 && d.isAfter(sel.first)) latestSelect?.invoke(sel.first, d)
                },
            )
        }.pointerInput(span) {
            // A tap moves the nearer handle there too.
            val leftPx = left.dp.toPx()
            detectTapGestures { pos ->
                val sel = latestSelection ?: return@detectTapGestures
                val frac = ((pos.x - leftPx) / (size.width - leftPx - 6.dp.toPx())).coerceIn(0f, 1f)
                val d = start.plusDays((frac * days).roundToLong())
                val nearFirst = abs(ChronoUnit.DAYS.between(sel.first, d)) <= abs(ChronoUnit.DAYS.between(sel.second, d))
                if (nearFirst && d.isBefore(sel.second)) latestSelect?.invoke(d, sel.second)
                else if (!nearFirst && d.isAfter(sel.first)) latestSelect?.invoke(sel.first, d)
            }
        }
    } else Modifier

    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .then(interactive)
            .semantics { contentDescription = "Weight trend from ${shortDate(start)} to ${shortDate(end)}" },
    ) {
        val l = left.dp.toPx()
        val r = size.width - 6.dp.toPx()
        val t = top.dp.toPx()
        val b = size.height - bottom.dp.toPx()
        fun x(d: LocalDate) = l + ChronoUnit.DAYS.between(start, d).toFloat() / days * (r - l)
        fun y(v: Double) = (t + (hi - v) / (hi - lo) * (b - t)).toFloat()

        // Gridlines at round values, labelled on the left.
        var g = ceil(lo / step) * step
        while (g <= floor(hi / step) * step + 1e-9) {
            drawLine(cBorder, Offset(l, y(g)), Offset(r, y(g)), strokeWidth = 1.dp.toPx())
            val text = if (step < 1) WeightUnits.decimal(g) else g.toInt().toString()
            val m = measurer.measure(text, label)
            drawText(m, topLeft = Offset(l - 6.dp.toPx() - m.size.width, y(g) - m.size.height / 2f))
            g += step
        }

        selection?.let { (a, z) ->
            drawRoundRect(cBand, Offset(x(a), t - 4.dp.toPx()), Size(x(z) - x(a), b - t + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
        }

        goal?.let {
            drawLine(cGoal, Offset(l, y(it)), Offset(r, y(it)), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }

        dots.forEach { w -> drawCircle(cDot.copy(alpha = 0.55f), radius = 2.3.dp.toPx(), center = Offset(x(w.date()), y(units.toUnit(w.kg)))) }

        if (points.isNotEmpty()) {
            val line = Path()
            points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.date), y(units.toUnit(p.kg))) else line.lineTo(x(p.date), y(units.toUnit(p.kg))) }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.last().date), b)
                lineTo(x(points.first().date), b)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(cLine.copy(alpha = 0.16f), cLine.copy(alpha = 0f)), startY = t, endY = b))
            drawPath(line, cLine, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (selection == null) {
                val last = points.last()
                drawCircle(cSurface, 6.dp.toPx(), Offset(x(last.date), y(units.toUnit(last.kg))))
                drawCircle(cLine, 4.dp.toPx(), Offset(x(last.date), y(units.toUnit(last.kg))))
            }
        }

        selection?.let { (a, z) ->
            listOf(a, z).forEachIndexed { i, d ->
                val v = points.lastOrNull { !it.date.isAfter(d) } ?: points.firstOrNull() ?: return@forEachIndexed
                drawLine(cHandle, Offset(x(d), t - 4.dp.toPx()), Offset(x(d), b), strokeWidth = 1.5.dp.toPx())
                val c = Offset(x(d), y(units.toUnit(v.kg)))
                drawCircle(cSurface, if (dragging == i) 9.dp.toPx() else 7.dp.toPx(), c)
                drawCircle(cHandle, if (dragging == i) 9.dp.toPx() else 7.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
        }

        // Dates: start, middle, end.
        val mid = start.plusDays(days / 2)
        listOf(start to 0f, mid to 0.5f, end to 1f).forEach { (d, align) ->
            val m = measurer.measure(shortDate(d), label)
            val cx = x(d) - m.size.width * align
            drawText(m, topLeft = Offset(cx.coerceIn(l - 4.dp.toPx(), r - m.size.width), b + 5.dp.toPx()))
        }
    }
}
