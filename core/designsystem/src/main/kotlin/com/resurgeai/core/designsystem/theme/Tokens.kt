package com.resurgeai.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken

val colors = ThemeProperty<Color>("colors")
val typography = ThemeProperty<TextStyle>("typography")
val spacing = ThemeProperty<Dp>("spacing")
val shapes = ThemeProperty<Shape>("shapes")

object ColorTokens {
    val bg = ThemeToken<Color>("bg")
    val surface = ThemeToken<Color>("surface")
    val surfaceMuted = ThemeToken<Color>("surface_muted")
    val border = ThemeToken<Color>("border")
    val text = ThemeToken<Color>("text")
    val textMuted = ThemeToken<Color>("text_muted")
    val textSubtle = ThemeToken<Color>("text_subtle")
    val accent = ThemeToken<Color>("accent")
    val accentMuted = ThemeToken<Color>("accent_muted")
    val onAccent = ThemeToken<Color>("on_accent")
    val danger = ThemeToken<Color>("danger")
    val success = ThemeToken<Color>("success")
    val sleep = ThemeToken<Color>("sleep")
    val activity = ThemeToken<Color>("activity")
    val energy = ThemeToken<Color>("energy")
    val water = ThemeToken<Color>("water")
    val heart = ThemeToken<Color>("heart")

    // Floating navigation bar / raised surfaces: white (light) or charcoal (dark), with a high-contrast active circle.
    val navBar = ThemeToken<Color>("nav_bar")
    val navActive = ThemeToken<Color>("nav_active")
    val navOnActive = ThemeToken<Color>("nav_on_active")
    val navInactive = ThemeToken<Color>("nav_inactive")
}

object TypeTokens {
    val display = ThemeToken<TextStyle>("display")
    val metric = ThemeToken<TextStyle>("metric")
    val title = ThemeToken<TextStyle>("title")
    val headline = ThemeToken<TextStyle>("headline")
    val body = ThemeToken<TextStyle>("body")
    val label = ThemeToken<TextStyle>("label")
    val caption = ThemeToken<TextStyle>("caption")
}

object SpaceTokens {
    val xs = ThemeToken<Dp>("xs")
    val sm = ThemeToken<Dp>("sm")
    val md = ThemeToken<Dp>("md")
    val lg = ThemeToken<Dp>("lg")
    val xl = ThemeToken<Dp>("xl")
    val xxl = ThemeToken<Dp>("xxl")
}

object ShapeTokens {
    val sm = ThemeToken<Shape>("sm")
    val md = ThemeToken<Shape>("md")
    val lg = ThemeToken<Shape>("lg")
    val pill = ThemeToken<Shape>("pill")
}
