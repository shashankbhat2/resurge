package com.resurgeai.app.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
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
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeighInSource
import com.resurgeai.core.model.WeightMath
import com.resurgeai.core.scale.ScaleStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
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
)

/**
 * Home screen weight: the latest weigh-in, the trend's change over the past week, and a button
 * that listens for the scale (or opens the log when no scale is paired). Refreshed whenever this
 * phone logs a weigh-in, and by the system every few hours.
 */
class WeightWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val glance = load(context)
        provideContent { Content(context, glance) }
    }

    private suspend fun load(context: Context): WeightGlance {
        val deps = EntryPointAccessors.fromApplication(context, Deps::class.java)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weighIns = withTimeoutOrNull(LOAD_MS) { runCatching { deps.weights().weighIns().first() }.getOrNull() }.orEmpty()
        val latest = weighIns.maxByOrNull { it.at }
        val trend = if (weighIns.isEmpty()) emptyList() else WeightMath.trend(weighIns, today, zone)
        return WeightGlance(
            kg = latest?.kg,
            at = latest?.at,
            fromScale = latest?.source == WeighInSource.SCALE,
            weekChangeKg = WeightMath.change(trend, today.minusDays(7), today),
            units = deps.units().current(),
            hasScale = deps.scales().current().scales.isNotEmpty(),
        )
    }

    @Composable
    private fun Content(context: Context, glance: WeightGlance) {
        val wide = LocalSize.current.width >= WIDE.width
        Column(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(Surface)
                .padding(16.dp)
                .clickable(actionStartActivity(openWeight(context))),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Weight", style = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                if (wide && glance.at != null) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(whenText(context, glance), style = TextStyle(color = Subtle, fontSize = 12.sp))
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                glance.kg?.let { ScaleNotifications.weightText(it, glance.units) } ?: "No weigh-ins yet",
                style = TextStyle(color = Ink, fontSize = if (glance.kg != null) 28.sp else 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            glance.weekChangeKg?.let {
                Text(changeText(it, glance.units), style = TextStyle(color = Muted, fontSize = 13.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            ActionButton(context, glance.hasScale)
        }
    }

    @Composable
    private fun ActionButton(context: Context, hasScale: Boolean) {
        val action = if (hasScale) {
            actionStartService(WeighInService.intent(context, WeighInService.Trigger.USER), isForegroundService = true)
        } else {
            actionStartActivity(openWeight(context, log = true))
        }
        Box(
            GlanceModifier
                .fillMaxWidth()
                .height(36.dp)
                .cornerRadius(18.dp)
                .background(Brand)
                .clickable(action),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (hasScale) "Weigh in" else "Log weight",
                style = TextStyle(color = OnBrand, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    private fun whenText(context: Context, glance: WeightGlance): String {
        val at = glance.at ?: return ""
        val date = at.atZone(ZoneId.systemDefault()).toLocalDate()
        val time = if (date == LocalDate.now()) DateFormat.getTimeFormat(context).format(Date.from(at)) else DateFormat.getMediumDateFormat(context).format(Date.from(at))
        return if (glance.fromScale) "Scale · $time" else time
    }

    private fun changeText(kg: Double, units: UnitSystem): String {
        val v = if (units == UnitSystem.IMPERIAL) kg / WeightMath.KG_PER_LB else kg
        val rounded = (v * 10).roundToInt() / 10.0
        if (rounded == 0.0) return "Steady this week"
        val label = if (units == UnitSystem.IMPERIAL) "lb" else "kg"
        val sign = if (rounded < 0) "−" else "+"
        return "$sign${"%.1f".format(abs(rounded))} $label this week"
    }

    private fun openWeight(context: Context, log: Boolean = false): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(ResurgeMessagingService.EXTRA_OPEN, if (log) OPEN_LOG else ScaleNotifications.OPEN_WEIGHT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface Deps {
        fun weights(): WeightRepository
        fun units(): ScaleUnits
        fun scales(): ScaleStore
    }

    /** Redraws every placed widget when this phone logs, edits or removes a weigh-in. */
    @Singleton
    class Refresher @Inject constructor(@ApplicationContext private val context: Context) : WeightChangeListener {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        override fun onWeightsChanged() {
            scope.launch { runCatching { WeightWidget().updateAll(context) } }
        }
    }

    companion object {
        const val OPEN_LOG = "weight_log"
        private const val LOAD_MS = 5_000L
        private val SMALL = DpSize(120.dp, 120.dp)
        private val WIDE = DpSize(220.dp, 120.dp)

        // The Tide palette (core/designsystem ResurgeTheme), light and dark.
        private val Surface = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF16161D))
        private val Ink = ColorProvider(day = Color(0xFF111127), night = Color(0xFFF3F3F8))
        private val Muted = ColorProvider(day = Color(0xFF55556B), night = Color(0xFFB3B3C6))
        private val Subtle = ColorProvider(day = Color(0xFF6E6E84), night = Color(0xFF8E8EA5))
        private val Brand = ColorProvider(day = Color(0xFF6F66FB), night = Color(0xFF8C85FF))
        private val OnBrand = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0C0C11))
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}
