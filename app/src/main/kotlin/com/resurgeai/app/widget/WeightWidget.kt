package com.resurgeai.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.resurgeai.app.MainActivity
import com.resurgeai.app.notifications.ResurgeMessagingService
import com.resurgeai.app.scale.ScaleNotifications
import com.resurgeai.app.scale.ScaleUnits
import com.resurgeai.app.scale.WeighInService
import com.resurgeai.core.data.weight.WeightChangeListener
import com.resurgeai.core.data.weight.WeightRepository
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeighIn
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.scale.ScaleSettings
import com.resurgeai.core.scale.ScaleStore
import com.resurgeai.core.scale.WeighInEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the widget shows; null weight before the first weigh-in. */
internal data class WeightGlance(
    val kg: Double?,
    val at: Instant?,
    val fromScale: Boolean,
    val weekChangeKg: Double?,
    val units: UnitSystem,
    val hasScale: Boolean,
    /** Daily trend over the last month, oldest first, for the sparkline. */
    val trend: List<Double> = emptyList(),
) {
    companion object {
        fun of(weighIns: List<WeighIn>, settings: ScaleSettings?, units: UnitSystem, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): WeightGlance {
            val latest = weighIns.maxByOrNull { it.at }
            val trend = if (weighIns.isEmpty()) emptyList() else WeightMath.trend(weighIns, today, zone)
            return WeightGlance(
                kg = latest?.kg,
                at = latest?.at,
                fromScale = latest?.source == WeighInSource.SCALE,
                weekChangeKg = WeightMath.change(trend, today.minusDays(7), today),
                units = units,
                hasScale = settings?.scales?.isNotEmpty() == true,
                trend = trend.filter { !it.date.isBefore(today.minusDays(30)) }.map { it.kg },
            )
        }
    }
}

/** What the paired scale is doing, so the widget can follow a weigh-in as it happens. */
internal sealed interface WidgetScale {
    data object Idle : WidgetScale
    /** Listening, nobody on the scale yet. */
    data object Listening : WidgetScale
    /** Someone's on it; [kg] is still settling. */
    data class Weighing(val kg: Double) : WidgetScale
}

/**
 * Home screen weight: the latest weigh-in, the trend's change over the past week (with a month's
 * sparkline when there's room), and a button that listens for the scale, or opens the log when
 * no scale is paired. While the scale is being listened to, the widget follows along live.
 */
class WeightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, Deps::class.java)
        val units = deps.units().current()
        val weighInsFlow = deps.weights().weighIns().catch { emit(emptyList()) }
        val firstWeighIns = withTimeoutOrNull(LOAD_MS) { weighInsFlow.first() }.orEmpty()
        val firstSettings = deps.scales().current()
        provideContent {
            // Firestore and the scale store are live, so a weigh-in shows up while the widget is open.
            val weighIns by remember { weighInsFlow }.collectAsState(firstWeighIns)
            val settings by remember { deps.scales().settings }.collectAsState(firstSettings)
            val listening by WeighInService.listening.collectAsState()
            val live by deps.engine().live.collectAsState()
            val glance = remember(weighIns, settings) { WeightGlance.of(weighIns, settings, units) }
            val scale = when {
                live?.settled == false -> WidgetScale.Weighing(live!!.kg)
                listening -> WidgetScale.Listening
                else -> WidgetScale.Idle
            }
            WeightWidgetContent(glance, scale, WeightWidgetActions.of(LocalContext.current, glance.hasScale))
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Deps {
        fun weights(): WeightRepository
        fun units(): ScaleUnits
        fun scales(): ScaleStore
        fun engine(): WeighInEngine
    }

    /** Redraws every placed widget when this phone logs, edits or removes a weigh-in. */
    @Singleton
    class Refresher @Inject constructor(@ApplicationContext private val context: Context) : WeightChangeListener {
        override fun onWeightsChanged() = refresh(context)
    }

    companion object {
        const val OPEN_LOG = "weight_log"
        private const val LOAD_MS = 5_000L
        internal val SMALL = DpSize(140.dp, 140.dp)
        internal val WIDE = DpSize(260.dp, 140.dp)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws placed widgets; starts a live session if none is running. */
        fun refresh(context: Context) {
            scope.launch { runCatching { WeightWidget().updateAll(context) } }
        }

        /** Whether this launcher can place the widget from inside the app. */
        fun canPin(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

        /** Asks the launcher to place the widget; it shows its own confirmation. */
        fun pin(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return
            runCatching { manager.requestPinAppWidget(ComponentName(context, WeightWidgetReceiver::class.java), null, null) }
        }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

/** Where taps go: the widget opens the Weight screen; the button weighs in or opens the log. */
internal class WeightWidgetActions(val open: Action, val primary: Action) {
    companion object {
        fun of(context: Context, hasScale: Boolean) = WeightWidgetActions(
            open = actionStartActivity(openWeight(context, ScaleNotifications.OPEN_WEIGHT)),
            primary = if (hasScale) {
                actionStartService(WeighInService.intent(context, WeighInService.Trigger.USER), isForegroundService = true)
            } else {
                actionStartActivity(openWeight(context, WeightWidget.OPEN_LOG))
            },
        )

        private fun openWeight(context: Context, open: String): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(ResurgeMessagingService.EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
internal fun WeightWidgetContent(glance: WeightGlance, scale: WidgetScale, actions: WeightWidgetActions) {
    val context = LocalContext.current
    val wide = LocalSize.current.width >= WeightWidget.WIDE.width
    // Laid out to fit a 2×2 cell at its smallest (140 dp); extra height goes above the button.
    Column(
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(WidgetColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(actions.open),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(ResurgeIcons.Scale), contentDescription = null, modifier = GlanceModifier.size(15.dp), colorFilter = ColorFilter.tint(WidgetColors.BrandInk))
            Spacer(GlanceModifier.width(6.dp))
            Text("Weight", style = TextStyle(color = WidgetColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            if (wide && glance.at != null && scale == WidgetScale.Idle) {
                Spacer(GlanceModifier.defaultWeight())
                Text(whenText(context, glance), style = TextStyle(color = WidgetColors.Subtle, fontSize = 12.sp), maxLines = 1)
            }
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Reading(glance, scale)
                Change(glance, scale)
            }
            if (wide && glance.trend.size >= 2 && scale == WidgetScale.Idle) {
                Spacer(GlanceModifier.width(12.dp))
                Sparkline(glance.trend, width = 100, height = 44)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        PrimaryButton(glance, scale, actions)
    }
}

@Composable
private fun Reading(glance: WeightGlance, scale: WidgetScale) {
    val kg = (scale as? WidgetScale.Weighing)?.kg ?: glance.kg
    if (kg == null) {
        Text("No weigh-ins", style = TextStyle(color = WidgetColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.padding(top = 8.dp))
        if (scale == WidgetScale.Idle) Caption(null, if (glance.hasScale) "Step on to start" else "Log one to start")
        return
    }
    val (number, unit) = weightParts(kg, glance.units)
    val live = scale is WidgetScale.Weighing
    Row(verticalAlignment = Alignment.Bottom) {
        Text(number, style = TextStyle(color = if (live) WidgetColors.BrandInk else WidgetColors.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(
            if (live) " $unit…" else " $unit",
            style = TextStyle(color = WidgetColors.Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(bottom = 5.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Change(glance: WeightGlance, scale: WidgetScale) {
    when (scale) {
        is WidgetScale.Weighing -> Caption(null, "Hold still on the scale")
        WidgetScale.Listening -> Caption(null, "Listening for your scale")
        WidgetScale.Idle -> {
            val change = glance.weekChangeKg ?: return
            val (arrow, amount) = changeParts(change, glance.units)
            if (arrow == null) Caption(null, amount) else Caption("$arrow $amount", "this week")
        }
    }
}

/** One line under the number: an optional emphasised lead, then muted text. */
@Composable
private fun Caption(lead: String?, rest: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        lead?.let {
            Text(it, style = TextStyle(color = WidgetColors.BrandInk, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(rest, style = TextStyle(color = WidgetColors.Muted, fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun PrimaryButton(glance: WeightGlance, scale: WidgetScale, actions: WeightWidgetActions) {
    val waiting = scale != WidgetScale.Idle
    val label = when {
        scale is WidgetScale.Weighing -> "Weighing…"
        scale == WidgetScale.Listening -> "Step on your scale"
        glance.hasScale -> "Weigh in"
        else -> "Log weight"
    }
    Row(
        GlanceModifier
            .fillMaxWidth()
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(if (waiting) WidgetColors.AccentMuted else WidgetColors.Brand)
            .clickable(if (waiting) actions.open else actions.primary),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ink = if (waiting) WidgetColors.BrandInk else WidgetColors.OnBrand
        Image(
            ImageProvider(if (glance.hasScale || waiting) ResurgeIcons.Scale else ResurgeIcons.Plus),
            contentDescription = null,
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(ink),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(label, style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

/** A month of trend as a soft line; drawn white and tinted, so it follows light and dark. */
@Composable
private fun Sparkline(trend: List<Double>, width: Int, height: Int) {
    val density = LocalContext.current.resources.displayMetrics.density
    val bitmap = remember(trend, density) { sparkline(trend, (width * density).roundToInt(), (height * density).roundToInt(), density) }
    Image(
        ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.width(width.dp).height(height.dp),
        colorFilter = ColorFilter.tint(WidgetColors.Brand),
    )
}

internal fun sparkline(trend: List<Double>, w: Int, h: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 2.5f * density
    val dot = 3.5f * density
    val pad = dot + stroke / 2
    val lo = trend.min()
    val hi = trend.max()
    // A flat month still gets a little room, so it reads as steady rather than as noise.
    val span = maxOf(hi - lo, 0.6)
    val mid = (hi + lo) / 2
    fun x(i: Int) = pad + (w - 2 * pad) * i / (trend.size - 1)
    fun y(kg: Double) = (pad + (h - 2 * pad) * (0.5 - (kg - mid) / span)).toFloat()
    val line = Path().apply {
        moveTo(x(0), y(trend[0]))
        for (i in 1 until trend.size) lineTo(x(i), y(trend[i]))
    }
    val fill = Path(line).apply {
        lineTo(x(trend.lastIndex), h.toFloat())
        lineTo(x(0), h.toFloat())
        close()
    }
    canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), android.graphics.Color.argb(70, 255, 255, 255), android.graphics.Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    })
    canvas.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    })
    canvas.drawCircle(x(trend.lastIndex), y(trend.last()), dot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    return bitmap
}

private fun whenText(context: Context, glance: WeightGlance): String {
    val at = glance.at ?: return ""
    val date = at.atZone(ZoneId.systemDefault()).toLocalDate()
    val time = if (date == LocalDate.now()) DateFormat.getTimeFormat(context).format(Date.from(at)) else DateFormat.getMediumDateFormat(context).format(Date.from(at))
    return if (glance.fromScale) "Scale · $time" else time
}

/** "78.3" and "kg", so the number can be big and the unit small. */
internal fun weightParts(kg: Double, units: UnitSystem): Pair<String, String> =
    if (units == UnitSystem.IMPERIAL) "%.1f".format(Locale.getDefault(), kg / WeightMath.KG_PER_LB) to "lb"
    else "%.1f".format(Locale.getDefault(), kg) to "kg"

/** "↓" and "0.6 kg"; no arrow for a steady week. */
internal fun changeParts(kg: Double, units: UnitSystem): Pair<String?, String> {
    val v = if (units == UnitSystem.IMPERIAL) kg / WeightMath.KG_PER_LB else kg
    val rounded = (v * 10).roundToInt() / 10.0
    if (rounded == 0.0) return null to "Steady this week"
    val label = if (units == UnitSystem.IMPERIAL) "lb" else "kg"
    return (if (rounded < 0) "↓" else "↑") to "%.1f %s".format(Locale.getDefault(), abs(rounded), label)
}

/** The Tide palette (core/designsystem ResurgeTheme), light and dark. */
internal object WidgetColors {
    val Surface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF16161D))
    val Ink = ColorProvider(day = Color(0xFF111127), night = Color(0xFFF3F3F8))
    val Muted = ColorProvider(day = Color(0xFF55556B), night = Color(0xFFB3B3C6))
    val Subtle = ColorProvider(day = Color(0xFF6E6E84), night = Color(0xFF8E8EA5))
    val Brand = ColorProvider(day = Color(0xFF6F66FB), night = Color(0xFF8C85FF))
    val BrandInk = ColorProvider(day = Color(0xFF4A41D9), night = Color(0xFFB4AFFF))
    val AccentMuted = ColorProvider(day = Color(0xFFECEBFF), night = Color(0xFF25234A))
    val OnBrand = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0C0C11))
}
