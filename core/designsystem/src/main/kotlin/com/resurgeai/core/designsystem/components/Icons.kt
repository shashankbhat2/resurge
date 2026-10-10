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
import com.composables.icons.lucide.R as Lucide

/** Lucide icons used across the app. Add entries here rather than referencing drawables directly. */
object ResurgeIcons {
    @DrawableRes val Home = Lucide.drawable.lucide_ic_house
    @DrawableRes val Food = Lucide.drawable.lucide_ic_utensils
    @DrawableRes val Coach = Lucide.drawable.lucide_ic_sparkles
    @DrawableRes val Profile = Lucide.drawable.lucide_ic_user
    @DrawableRes val Check = Lucide.drawable.lucide_ic_check
    @DrawableRes val ChevronRight = Lucide.drawable.lucide_ic_chevron_right
    @DrawableRes val ArrowLeft = Lucide.drawable.lucide_ic_arrow_left
    @DrawableRes val ArrowRight = Lucide.drawable.lucide_ic_arrow_right
    @DrawableRes val LogOut = Lucide.drawable.lucide_ic_log_out
    @DrawableRes val Sun = Lucide.drawable.lucide_ic_sun
    @DrawableRes val Moon = Lucide.drawable.lucide_ic_moon
    @DrawableRes val Monitor = Lucide.drawable.lucide_ic_monitor
    @DrawableRes val Flame = Lucide.drawable.lucide_ic_flame
    @DrawableRes val Droplet = Lucide.drawable.lucide_ic_droplet
    @DrawableRes val Bed = Lucide.drawable.lucide_ic_bed
    @DrawableRes val Footprints = Lucide.drawable.lucide_ic_footprints
    @DrawableRes val Activity = Lucide.drawable.lucide_ic_activity
    @DrawableRes val Dumbbell = Lucide.drawable.lucide_ic_dumbbell
    @DrawableRes val Heart = Lucide.drawable.lucide_ic_heart
    @DrawableRes val Scale = Lucide.drawable.lucide_ic_scale
    @DrawableRes val Target = Lucide.drawable.lucide_ic_target
    @DrawableRes val Watch = Lucide.drawable.lucide_ic_watch
    @DrawableRes val Phone = Lucide.drawable.lucide_ic_smartphone
    @DrawableRes val Sparkles = Lucide.drawable.lucide_ic_sparkles
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
