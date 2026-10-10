package com.resurgeai.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.catalog.DesignCatalog
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot of every design-system component in light and dark.
 * Record: ./gradlew :core:designsystem:recordRoborazziDebug
 * Verify: ./gradlew :core:designsystem:verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2100dp-xhdpi")
class CatalogScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun catalogLight() = capture(ColorScheme.Light, "light")

    @Test fun catalogDark() = capture(ColorScheme.Dark, "dark")

    private fun capture(scheme: ColorScheme, name: String) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen { DesignCatalog(scrollable = false) }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/catalog_$name.png")
    }
}
