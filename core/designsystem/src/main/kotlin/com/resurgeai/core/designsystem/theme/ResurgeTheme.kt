package com.resurgeai.core.designsystem.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.theme.ColorScheme
import com.composeunstyled.theme.Theme
import com.composeunstyled.theme.buildThemeV2
import com.composeunstyled.theme.rememberColoredIndication
import com.resurgeai.core.designsystem.R
import com.resurgeai.core.designsystem.theme.ColorTokens as C

private fun interWeight(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Inter = FontFamily(
    interWeight(FontWeight.Normal),
    interWeight(FontWeight.Medium),
    interWeight(FontWeight.SemiBold),
    interWeight(FontWeight.Bold),
)

private val Tabular = "tnum"

private object Palette {
    val accent = Color(0xFF2F6BFF)
    val sleep = Color(0xFF6366F1)
    val activity = Color(0xFF16A34A)
    val energy = Color(0xFFF97316)
    val water = Color(0xFF0EA5E9)
    val heart = Color(0xFFE11D48)
}

val ResurgeTheme = buildThemeV2 {
    name = "Resurge"
    colorSchemeTransitionSpec = tween(200)

    properties[colors] = mapOf(
        C.accent to Palette.accent,
        C.onAccent to Color.White,
        C.sleep to Palette.sleep,
        C.activity to Palette.activity,
        C.energy to Palette.energy,
        C.water to Palette.water,
        C.heart to Palette.heart,
        C.danger to Color(0xFFDC2626),
        C.success to Color(0xFF16A34A),
    )

    colorScheme(ColorScheme.Light) {
        properties[colors] = mapOf(
            C.bg to Color(0xFFF7F7F5),
            C.surface to Color(0xFFFFFFFF),
            C.surfaceMuted to Color(0xFFF0EFEC),
            C.border to Color(0xFFE7E5E4),
            C.text to Color(0xFF0C0A09),
            C.textMuted to Color(0xFF57534E),
            C.textSubtle to Color(0xFF8A8580),
            C.accentMuted to Palette.accent.copy(alpha = 0.10f),
        )
        defaultContentColor = Color(0xFF0C0A09)
        defaultIndication = rememberColoredIndication(
            hoveredColor = Color.Black.copy(alpha = 0.04f),
            pressedColor = Color.Black.copy(alpha = 0.08f),
            focusedColor = Color.Black.copy(alpha = 0.06f),
        )
    }

    colorScheme(ColorScheme.Dark) {
        properties[colors] = mapOf(
            C.bg to Color(0xFF0B0B0C),
            C.surface to Color(0xFF151517),
            C.surfaceMuted to Color(0xFF1E1E21),
            C.border to Color(0xFF26262A),
            C.text to Color(0xFFF5F5F4),
            C.textMuted to Color(0xFFB4AFAA),
            C.textSubtle to Color(0xFF85807B),
            C.accentMuted to Palette.accent.copy(alpha = 0.18f),
        )
        defaultContentColor = Color(0xFFF5F5F4)
        defaultIndication = rememberColoredIndication(
            hoveredColor = Color.White.copy(alpha = 0.04f),
            pressedColor = Color.White.copy(alpha = 0.08f),
            focusedColor = Color.White.copy(alpha = 0.06f),
        )
    }

    defaultTextStyle = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp)
    defaultTextSelectionColors = TextSelectionColors(
        handleColor = Palette.accent,
        backgroundColor = Palette.accent.copy(alpha = 0.3f),
    )

    properties[typography] = mapOf(
        TypeTokens.display to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp,
        ),
        TypeTokens.metric to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 34.sp, fontFeatureSettings = Tabular,
        ),
        TypeTokens.title to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
        ),
        TypeTokens.headline to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp, lineHeight = 24.sp,
        ),
        TypeTokens.body to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.Normal,
            fontSize = 15.sp, lineHeight = 22.sp,
        ),
        TypeTokens.label to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp,
        ),
        TypeTokens.caption to TextStyle(
            fontFamily = Inter, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp,
        ),
    )

    properties[spacing] = mapOf(
        SpaceTokens.xs to 4.dp,
        SpaceTokens.sm to 8.dp,
        SpaceTokens.md to 12.dp,
        SpaceTokens.lg to 16.dp,
        SpaceTokens.xl to 24.dp,
        SpaceTokens.xxl to 32.dp,
    )

    properties[shapes] = mapOf(
        ShapeTokens.sm to RoundedCornerShape(10.dp),
        ShapeTokens.md to RoundedCornerShape(16.dp),
        ShapeTokens.lg to RoundedCornerShape(24.dp),
        ShapeTokens.pill to CircleShape,
    )
}

/** Short accessors so call sites read `Resurge.colors.text` instead of `Theme[colors][ColorTokens.text]`. */
object Resurge {
    val colors = ColorAccess
    val type = TypeAccess
    val space = SpaceAccess
    val shape = ShapeAccess
}

object ColorAccess {
    val bg @Composable get() = Theme[colors][C.bg]
    val surface @Composable get() = Theme[colors][C.surface]
    val surfaceMuted @Composable get() = Theme[colors][C.surfaceMuted]
    val border @Composable get() = Theme[colors][C.border]
    val text @Composable get() = Theme[colors][C.text]
    val textMuted @Composable get() = Theme[colors][C.textMuted]
    val textSubtle @Composable get() = Theme[colors][C.textSubtle]
    val accent @Composable get() = Theme[colors][C.accent]
    val accentMuted @Composable get() = Theme[colors][C.accentMuted]
    val onAccent @Composable get() = Theme[colors][C.onAccent]
    val danger @Composable get() = Theme[colors][C.danger]
    val success @Composable get() = Theme[colors][C.success]
    val sleep @Composable get() = Theme[colors][C.sleep]
    val activity @Composable get() = Theme[colors][C.activity]
    val energy @Composable get() = Theme[colors][C.energy]
    val water @Composable get() = Theme[colors][C.water]
    val heart @Composable get() = Theme[colors][C.heart]
}

object TypeAccess {
    val display @Composable get() = Theme[typography][TypeTokens.display]
    val metric @Composable get() = Theme[typography][TypeTokens.metric]
    val title @Composable get() = Theme[typography][TypeTokens.title]
    val headline @Composable get() = Theme[typography][TypeTokens.headline]
    val body @Composable get() = Theme[typography][TypeTokens.body]
    val label @Composable get() = Theme[typography][TypeTokens.label]
    val caption @Composable get() = Theme[typography][TypeTokens.caption]
}

object SpaceAccess {
    val xs @Composable get() = Theme[spacing][SpaceTokens.xs]
    val sm @Composable get() = Theme[spacing][SpaceTokens.sm]
    val md @Composable get() = Theme[spacing][SpaceTokens.md]
    val lg @Composable get() = Theme[spacing][SpaceTokens.lg]
    val xl @Composable get() = Theme[spacing][SpaceTokens.xl]
    val xxl @Composable get() = Theme[spacing][SpaceTokens.xxl]
}

object ShapeAccess {
    val sm @Composable get() = Theme[shapes][ShapeTokens.sm]
    val md @Composable get() = Theme[shapes][ShapeTokens.md]
    val lg @Composable get() = Theme[shapes][ShapeTokens.lg]
    val pill @Composable get() = Theme[shapes][ShapeTokens.pill]
}
