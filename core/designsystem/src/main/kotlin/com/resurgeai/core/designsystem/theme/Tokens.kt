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

    // Resurge Tide: the daily game layer.
    /** Links, selected labels and tide percentages on light grounds. */
    val brandInk = ThemeToken<Color>("brand_ink")
    /** Leading edge of a rising tide. Decorative only. */
    val foam = ThemeToken<Color>("foam")
    /** Earned moments only: streak, high tide, a weekly goal met. */
    val glow = ThemeToken<Color>("glow")
    val onGlow = ThemeToken<Color>("on_glow")
    val glowInk = ThemeToken<Color>("glow_ink")
    val glowTint = ThemeToken<Color>("glow_tint")
    /** Anchor (streak freeze) days. */
    val frost = ThemeToken<Color>("frost")
    val frostInk = ThemeToken<Color>("frost_ink")
    /** Missed days: low tide, never red. */
    val ebb = ThemeToken<Color>("ebb")
    /** The Commander coach's red. A persona colour, never a warning. */
    val commander = ThemeToken<Color>("commander")

    // Floating navigation bar / raised surfaces: white (light) or charcoal (dark), with a high-contrast active circle.
    val navBar = ThemeToken<Color>("nav_bar")
    val navActive = ThemeToken<Color>("nav_active")
    val navOnActive = ThemeToken<Color>("nav_on_active")
    val navInactive = ThemeToken<Color>("nav_inactive")
    val navHighlight = ThemeToken<Color>("nav_highlight")
    val onNavHighlight = ThemeToken<Color>("on_nav_highlight")
}

object TypeTokens {
    /** Personality text (greeting, sign-in, onboarding, high tide) and big numbers. Bricolage Grotesque. */
    val brand = ThemeToken<TextStyle>("brand")
    val brandLarge = ThemeToken<TextStyle>("brand_large")
    /** The tide percentage inside the Today ring. One per screen. */
    val tide = ThemeToken<TextStyle>("tide")
    /** Uppercase section labels. */
    val overline = ThemeToken<TextStyle>("overline")
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
