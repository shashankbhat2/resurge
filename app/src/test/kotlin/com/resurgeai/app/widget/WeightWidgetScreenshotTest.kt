package com.resurgeai.app.widget

import android.app.Application
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.model.UnitSystem
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The home screen widget, rendered through the same RemoteViews a launcher gets.
 * Record with :app:recordRoborazziDebug.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
class WeightWidgetScreenshotTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    // A month drifting down from 79.6 to 78.4 kg, with day-to-day wobble.
    private val trend = (0..30).map { 79.6 - 1.2 * it / 30 + 0.12 * sin(it / 2.5) }

    private val glance = WeightGlance(
        kg = 78.3,
        at = today.atTime(7, 42).atZone(zone).toInstant(),
        fromScale = true,
        weekChangeKg = -0.6,
        units = UnitSystem.METRIC,
        hasScale = true,
        trend = trend,
    )

    private fun capture(name: String, size: DpSize, g: WeightGlance = glance, scale: WidgetScale = WidgetScale.Idle, framed: Boolean = true) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val actions = WeightWidgetActions(actionStartActivity(Intent()), actionStartActivity(Intent()))
                val views = runBlocking { GlanceRemoteViews().compose(activity, size) { WeightWidgetContent(g, scale, actions) } }.remoteViews
                val density = activity.resources.displayMetrics.density
                fun px(dp: Float) = (dp * density).roundToInt()
                // A soft wallpaper behind the widget, like a home screen.
                val wallpaper = FrameLayout(activity).apply {
                    if (framed) {
                        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFB9C6F5.toInt(), 0xFFE8D5F2.toInt()))
                        setPadding(px(20f), px(20f), px(20f), px(20f))
                    }
                }
                val widget = FrameLayout(activity)
                widget.addView(views.apply(activity, widget))
                wallpaper.addView(widget, FrameLayout.LayoutParams(px(size.width.value), px(size.height.value)))
                activity.setContentView(wallpaper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                wallpaper.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                wallpaper.layout(0, 0, wallpaper.measuredWidth, wallpaper.measuredHeight)
                wallpaper.captureRoboImage("src/test/screenshots/widget_$name.png")
            }
        }
    }

    /** Also the widget picker's preview: copy to res/drawable-nodpi/weight_widget_preview.png. */
    @Test fun preview() = capture("preview", WeightWidget.WIDE, framed = false)

    @Test fun small() = capture("small", WeightWidget.SMALL)

    @Test fun wide() = capture("wide", WeightWidget.WIDE)

    @Test fun wideListening() = capture("wide_listening", WeightWidget.WIDE, scale = WidgetScale.Listening)

    @Test fun wideWeighing() = capture("wide_weighing", WeightWidget.WIDE, scale = WidgetScale.Weighing(78.2))

    @Test fun smallNoScale() = capture("small_no_scale", WeightWidget.SMALL, glance.copy(hasScale = false, fromScale = false, weekChangeKg = 0.02))

    @Test fun smallEmpty() = capture("small_empty", WeightWidget.SMALL, WeightGlance(null, null, false, null, UnitSystem.METRIC, hasScale = true))

    @Config(qualifiers = "+night")
    @Test fun wideDark() = capture("wide_dark", WeightWidget.WIDE, glance.copy(units = UnitSystem.IMPERIAL))
}
