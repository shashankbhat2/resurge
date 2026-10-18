package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.resurgeai.core.designsystem.theme.Resurge
import com.composables.icons.tabler.outline.R as Tabler

/** Resolves the icon/colour keys stored on habits (Firestore) to drawables and theme colours. */
object HabitVisuals {
    val icons: List<Pair<String, Int>> = listOf(
        "flame" to Tabler.drawable.tabler_ic_flame_outline,
        "bed" to Tabler.drawable.tabler_ic_bed_outline,
        "moon" to Tabler.drawable.tabler_ic_moon_outline,
        "phone_off" to Tabler.drawable.tabler_ic_device_mobile_off_outline,
        "walk" to Tabler.drawable.tabler_ic_walk_outline,
        "run" to Tabler.drawable.tabler_ic_run_outline,
        "bike" to Tabler.drawable.tabler_ic_bike_outline,
        "swim" to Tabler.drawable.tabler_ic_swimming_outline,
        "barbell" to Tabler.drawable.tabler_ic_barbell_outline,
        "yoga" to Tabler.drawable.tabler_ic_yoga_outline,
        "droplet" to Tabler.drawable.tabler_ic_droplet_outline,
        "apple" to Tabler.drawable.tabler_ic_apple_outline,
        "salad" to Tabler.drawable.tabler_ic_salad_outline,
        "coffee_off" to Tabler.drawable.tabler_ic_coffee_off_outline,
        "pill" to Tabler.drawable.tabler_ic_pill_outline,
        "book" to Tabler.drawable.tabler_ic_book_outline,
        "notebook" to Tabler.drawable.tabler_ic_notebook_outline,
        "brain" to Tabler.drawable.tabler_ic_brain_outline,
        "sun" to Tabler.drawable.tabler_ic_sun_outline,
        "music" to Tabler.drawable.tabler_ic_music_outline,
        // Neutral ones that say nothing about the habit, for private habits.
        "leaf" to Tabler.drawable.tabler_ic_leaf_outline,
        "star" to Tabler.drawable.tabler_ic_star_outline,
        "circle" to Tabler.drawable.tabler_ic_circle_dot_outline,
    )
    private val iconMap = icons.toMap()

    val colorKeys = listOf("accent", "sleep", "activity", "energy", "water", "heart")

    @DrawableRes
    fun icon(key: String): Int = iconMap[key] ?: Tabler.drawable.tabler_ic_flame_outline

    @Composable
    fun color(key: String): Color = when (key) {
        "sleep" -> Resurge.colors.sleep
        "activity" -> Resurge.colors.activity
        "energy" -> Resurge.colors.energy
        "water" -> Resurge.colors.water
        "heart" -> Resurge.colors.heart
        else -> Resurge.colors.accent
    }

    /**
     * The colour a done habit fills with, under `onAccent` text and icons. Brand uses `brandInk`,
     * since the lighter brand fill is too pale for white text.
     */
    @Composable
    fun fill(key: String): Color = if (key in setOf("sleep", "activity", "energy", "water", "heart")) color(key) else Resurge.colors.brandInk

    /** The name shown under each colour swatch. */
    fun colorLabel(key: String): String = when (key) {
        "sleep" -> "Sleep"
        "activity" -> "Move"
        "energy" -> "Fuel"
        "water" -> "Water"
        "heart" -> "Heart"
        else -> "Brand"
    }
}
