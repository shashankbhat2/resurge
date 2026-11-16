package com.resurgeai.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composeunstyled.theme.ColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.resurgeai.core.designsystem.components.Screen
import com.resurgeai.core.designsystem.components.TopBar
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.designsystem.theme.ResurgeTheme
import com.resurgeai.core.model.Targets
import com.resurgeai.core.model.ThemePreference
import com.resurgeai.core.model.UserProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The settings menu, with meal logging on this device on (default) and off. Record with :feature:profile:recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1100dp-xhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val profile = UserProfile(
        uid = "u1", displayName = "Alex Rivera", email = "alex@example.com", photoUrl = null,
        goals = emptySet(), targets = Targets(2200, 3000), onboardingComplete = true,
    )

    private fun capture(name: String, logMeals: Boolean, scheme: ColorScheme = ColorScheme.Light) {
        compose.setContent {
            ResurgeTheme(colorScheme = scheme) {
                Screen {
                    Column(Modifier.fillMaxSize().background(Resurge.colors.bg)) {
                        TopBar("Settings", onBack = {})
                        MainMenu(
                            state = ProfileUiState(profile = profile, theme = ThemePreference.SYSTEM, logMealsOnDevice = logMeals),
                            onOpen = {}, onHealthConnect = {}, onOpenCatalog = null, onOpenProgressPhotos = {},
                            onOpenSmartScale = {}, versionName = "0.9.0", onSignOut = {}, onLogMealsChange = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/settings_$name.png")
    }

    @Test fun mealsViaMcp() = capture("meals_mcp", logMeals = false)

    @Test fun mealsOnDeviceDark() = capture("meals_on_device_dark", logMeals = true, scheme = ColorScheme.Dark)
}
