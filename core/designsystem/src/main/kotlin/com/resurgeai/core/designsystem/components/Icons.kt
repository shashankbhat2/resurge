package com.resurgeai.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.LocalContentColor
import com.composeunstyled.UnstyledIcon
import com.resurgeai.core.designsystem.R
import com.composables.icons.tabler.filled.R as TablerFilled
import com.composables.icons.tabler.outline.R as Tabler

/**
 * Tabler icons used across the app (outline by default). Add entries here rather than referencing
 * drawables directly. Navigation uses outline when inactive and the `*Filled` variant when active.
 */
object ResurgeIcons {
    // Navigation
    @DrawableRes val Today = Tabler.drawable.tabler_ic_sunrise_outline
    @DrawableRes val TodayFilled = TablerFilled.drawable.tabler_ic_sunrise_filled
    @DrawableRes val Food = Tabler.drawable.tabler_ic_salad_outline
    @DrawableRes val FoodFilled = TablerFilled.drawable.tabler_ic_salad_filled
    @DrawableRes val Coach = Tabler.drawable.tabler_ic_sparkles_outline
    @DrawableRes val CoachFilled = R.drawable.ic_sparkles_filled
    @DrawableRes val Profile = Tabler.drawable.tabler_ic_user_circle_outline
    @DrawableRes val ProfileFilled = R.drawable.ic_user_circle_filled
    @DrawableRes val Fitness = Tabler.drawable.tabler_ic_barbell_outline
    @DrawableRes val FitnessFilled = TablerFilled.drawable.tabler_ic_barbell_filled
    @DrawableRes val Scan = Tabler.drawable.tabler_ic_scan_outline
    @DrawableRes val Habits = Tabler.drawable.tabler_ic_restore_outline

    // General
    @DrawableRes val Check = Tabler.drawable.tabler_ic_check_outline
    @DrawableRes val ChevronRight = Tabler.drawable.tabler_ic_chevron_right_outline
    @DrawableRes val ArrowLeft = Tabler.drawable.tabler_ic_arrow_left_outline
    @DrawableRes val ArrowRight = Tabler.drawable.tabler_ic_arrow_right_outline
    @DrawableRes val ArrowUp = Tabler.drawable.tabler_ic_arrow_up_outline
    @DrawableRes val LogOut = Tabler.drawable.tabler_ic_logout_outline
    @DrawableRes val Sun = Tabler.drawable.tabler_ic_sun_outline
    @DrawableRes val Moon = Tabler.drawable.tabler_ic_moon_outline
    @DrawableRes val Monitor = Tabler.drawable.tabler_ic_device_desktop_outline
    @DrawableRes val Sparkles = Tabler.drawable.tabler_ic_sparkles_outline
    @DrawableRes val Target = Tabler.drawable.tabler_ic_target_outline
    @DrawableRes val Devices = Tabler.drawable.tabler_ic_devices_outline
    @DrawableRes val Close = Tabler.drawable.tabler_ic_x_outline
    @DrawableRes val Plus = Tabler.drawable.tabler_ic_plus_outline
    @DrawableRes val Pencil = Tabler.drawable.tabler_ic_pencil_outline
    @DrawableRes val Archive = Tabler.drawable.tabler_ic_trash_outline
    @DrawableRes val Undo = Tabler.drawable.tabler_ic_arrow_back_up_outline
    @DrawableRes val Bell = Tabler.drawable.tabler_ic_bell_outline

    // Streaks: filled flame = logged done, outline = not done / unlogged, snowflake = freeze used.
    @DrawableRes val FlameFilled = TablerFilled.drawable.tabler_ic_flame_filled
    @DrawableRes val Snowflake = Tabler.drawable.tabler_ic_snowflake_outline

    // Rise count (days in a row): a medal, filled on the badge, outline on habit tiles.
    @DrawableRes val Medal = TablerFilled.drawable.tabler_ic_award_filled
    @DrawableRes val MedalOutline = Tabler.drawable.tabler_ic_award_outline

    // Weekly goal (full days this week).
    @DrawableRes val Trophy = Tabler.drawable.tabler_ic_trophy_outline

    // Metrics
    @DrawableRes val Flame = Tabler.drawable.tabler_ic_flame_outline
    @DrawableRes val Droplet = Tabler.drawable.tabler_ic_droplet_outline
    @DrawableRes val Bed = Tabler.drawable.tabler_ic_bed_outline
    @DrawableRes val Footprints = Tabler.drawable.tabler_ic_walk_outline
    @DrawableRes val Distance = Tabler.drawable.tabler_ic_route_outline
    @DrawableRes val Activity = Tabler.drawable.tabler_ic_activity_outline
    @DrawableRes val Dumbbell = Tabler.drawable.tabler_ic_barbell_outline
    @DrawableRes val Heart = Tabler.drawable.tabler_ic_heart_outline
    @DrawableRes val Heartbeat = Tabler.drawable.tabler_ic_heartbeat_outline
    @DrawableRes val Scale = Tabler.drawable.tabler_ic_scale_outline

    // Sources
    @DrawableRes val Watch = Tabler.drawable.tabler_ic_device_watch_outline
    @DrawableRes val Phone = Tabler.drawable.tabler_ic_device_mobile_outline

    // Logging
    @DrawableRes val Camera = Tabler.drawable.tabler_ic_camera_outline
    @DrawableRes val Photo = Tabler.drawable.tabler_ic_photo_outline
    @DrawableRes val Keyboard = Tabler.drawable.tabler_ic_keyboard_outline
    @DrawableRes val Search = Tabler.drawable.tabler_ic_search_outline
    @DrawableRes val Clock = Tabler.drawable.tabler_ic_clock_outline
    @DrawableRes val Stopwatch = Tabler.drawable.tabler_ic_stopwatch_outline
    @DrawableRes val Repeat = Tabler.drawable.tabler_ic_repeat_outline
    @DrawableRes val Mountain = Tabler.drawable.tabler_ic_mountain_outline
    @DrawableRes val Note = Tabler.drawable.tabler_ic_notes_outline
    @DrawableRes val Bowl = Tabler.drawable.tabler_ic_bowl_outline
    @DrawableRes val AlertCircle = Tabler.drawable.tabler_ic_alert_circle_outline

    // Macros
    @DrawableRes val Protein = Tabler.drawable.tabler_ic_meat_outline
    @DrawableRes val Carbs = Tabler.drawable.tabler_ic_wheat_outline
    @DrawableRes val Fat = Tabler.drawable.tabler_ic_droplet_outline
}

@Composable
fun Icon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    UnstyledIcon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
