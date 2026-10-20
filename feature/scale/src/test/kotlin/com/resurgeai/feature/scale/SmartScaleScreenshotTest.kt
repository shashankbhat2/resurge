package com.resurgeai.feature.scale

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.designsystem.theme.clockFormatter
import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.scale.PairedScale
import com.resurgeai.core.scale.ScaleReadiness
import com.resurgeai.core.scale.protocol.ScaleProtocols
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Record with :feature:scale:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1100dp-xhdpi")
class SmartScaleScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 17)
    private val zone = ZoneId.systemDefault()
    private val miScale = PairedScale("C8:47:8C:12:34:56", "xiaomi_mi", "Mi Body Composition Scale 2", today.minusDays(9).atStartOfDay(zone).toInstant())

    private val base = SmartScaleUiState(loading = false, families = ScaleProtocols.families, healthConnectAvailable = true)

    private fun capture(name: String, s: SmartScaleUiState, nearby: List<FoundScale> = emptyList(), scheme: ColorScheme = ColorScheme.Light) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { SmartScaleScreen(s, nearby, SmartScaleActions(), today = today) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/scale_$name.png")
    }

    @Test fun intro() = capture("intro", base)

    @Test fun finding() = capture(
        "finding",
        base.copy(finding = true),
        nearby = listOf(
            FoundScale("C8:47:8C:12:34:56", "xiaomi_mi", "Mi Body Composition Scale 2", 78.4, -48),
            FoundScale("A4:C1:38:9A:BC:DE", "qn_broadcast", "Renpho ES-CS20M", 64.1, -81),
        ),
    )

    @Test fun findingBluetoothOff() = capture("finding_bluetooth_off", base.copy(finding = true, phone = PhoneState(readiness = ScaleReadiness.BLUETOOTH_OFF)))

    @Test fun paired() = capture(
        "paired",
        base.copy(
            scales = listOf(miScale),
            lastReadingAt = today.atTime(7, 42).atZone(zone).toInstant(),
            writeToHealthConnect = true,
            phone = PhoneState(needsNotifications = true),
        ),
    )

    @Test fun pairedDark() = capture(
        "paired_dark",
        base.copy(scales = listOf(miScale), lastReadingAt = today.atTime(7, 42).atZone(zone).toInstant(), units = UnitSystem.IMPERIAL),
        scheme = ColorScheme.Dark,
    )

    @Test
    fun `last weigh-in reads as a time today and a date before yesterday`() {
        val clock = clockFormatter(true)
        assertEquals("Last weigh-in 07:42", lastWeighIn(today.atTime(7, 42).atZone(zone).toInstant(), today, clock, zone))
        assertEquals("Last weigh-in yesterday, 07:42", lastWeighIn(today.minusDays(1).atTime(7, 42).atZone(zone).toInstant(), today, clock, zone))
        assertEquals("172.8 lb", WeightText(UnitSystem.IMPERIAL).format(78.4))
    }
}
