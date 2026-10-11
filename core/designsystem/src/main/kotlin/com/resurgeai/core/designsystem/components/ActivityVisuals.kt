package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.ActivityCategory
import com.composables.icons.tabler.outline.R as Tabler

/** Resolves catalog activity icon keys and categories to drawables and theme colours. */
object ActivityVisuals {
    private val icons = mapOf(
        "run" to Tabler.drawable.tabler_ic_run_outline,
        "treadmill" to Tabler.drawable.tabler_ic_treadmill_outline,
        "walk" to Tabler.drawable.tabler_ic_walk_outline,
        "trekking" to Tabler.drawable.tabler_ic_trekking_outline,
        "bike" to Tabler.drawable.tabler_ic_bike_outline,
        "kayak" to Tabler.drawable.tabler_ic_kayak_outline,
        "activity" to Tabler.drawable.tabler_ic_activity_outline,
        "stairs" to Tabler.drawable.tabler_ic_stairs_outline,
        "skateboarding" to Tabler.drawable.tabler_ic_skateboarding_outline,
        "mountain" to Tabler.drawable.tabler_ic_mountain_outline,
        "swimming" to Tabler.drawable.tabler_ic_swimming_outline,
        "barbell" to Tabler.drawable.tabler_ic_barbell_outline,
        "flame" to Tabler.drawable.tabler_ic_flame_outline,
        "yoga" to Tabler.drawable.tabler_ic_yoga_outline,
        "stretching" to Tabler.drawable.tabler_ic_stretching_outline,
        "music" to Tabler.drawable.tabler_ic_music_outline,
        "karate" to Tabler.drawable.tabler_ic_karate_outline,
        "ball_football" to Tabler.drawable.tabler_ic_ball_football_outline,
        "cricket" to Tabler.drawable.tabler_ic_cricket_outline,
        "ball_basketball" to Tabler.drawable.tabler_ic_ball_basketball_outline,
        "ball_tennis" to Tabler.drawable.tabler_ic_ball_tennis_outline,
        "ping_pong" to Tabler.drawable.tabler_ic_ping_pong_outline,
        "ball_volleyball" to Tabler.drawable.tabler_ic_ball_volleyball_outline,
        "golf" to Tabler.drawable.tabler_ic_golf_outline,
        "jump_rope" to Tabler.drawable.tabler_ic_jump_rope_outline,
    )

    @DrawableRes
    fun icon(key: String): Int = icons[key] ?: Tabler.drawable.tabler_ic_activity_outline

    @Composable
    fun color(category: ActivityCategory): Color = when (category) {
        ActivityCategory.DISTANCE -> Resurge.colors.activity
        ActivityCategory.STRENGTH -> Resurge.colors.energy
        ActivityCategory.SWIM -> Resurge.colors.water
        ActivityCategory.TIMED -> Resurge.colors.accent
        ActivityCategory.COUNT -> Resurge.colors.heart
    }
}

/** Macro colours and icons, shared by the Food tab cards and summary. */
object MacroVisuals {
    @Composable fun protein(): Color = Resurge.colors.heart
    @Composable fun carbs(): Color = Resurge.colors.energy
    @Composable fun fat(): Color = Resurge.colors.water
}
