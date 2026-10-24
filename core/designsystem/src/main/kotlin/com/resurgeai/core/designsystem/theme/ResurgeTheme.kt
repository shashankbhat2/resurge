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

private fun figtreeWeight(weight: FontWeight) = Font(
    resId = R.font.figtree_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** UI face: titles, body, labels and captions. */
val Figtree = FontFamily(
    figtreeWeight(FontWeight.Normal),
    figtreeWeight(FontWeight.Medium),
    figtreeWeight(FontWeight.SemiBold),
    figtreeWeight(FontWeight.Bold),
)

private fun bricolageWeight(weight: FontWeight) = Font(
    resId = R.font.bricolage_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Display face: greeting, headings with personality, and the numbers people track (tide, metrics, rise count). */
val Bricolage = FontFamily(
    bricolageWeight(FontWeight.Normal),
    bricolageWeight(FontWeight.Medium),
    bricolageWeight(FontWeight.SemiBold),
    bricolageWeight(FontWeight.Bold),
)

private val Tabular = "tnum"

/**
 * Resurge Mono: black, white and greys do the work, one accent marks "you are here", and three
 * signals (up, mid, down) say whether things are moving toward the goal. The older category colours
 * (sleep, energy, glow, frost…) stay until each screen moves over.
 */
private object Light {
    val brand = Color(0xFF5B4BDB)
    val text = Color(0xFF000000)
}

private object Dark {
    val brand = Color(0xFF8B7CFF)
    val bg = Color(0xFF000000)
}

val ResurgeTheme = buildThemeV2 {
    name = "Resurge"
    colorSchemeTransitionSpec = tween(200)

    colorScheme(ColorScheme.Light) {
        properties[colors] = mapOf(
            C.bg to Color(0xFFFFFFFF),
            C.surface to Color(0xFFF5F5F5),
            C.surfaceMuted to Color(0xFFEDEDED),
            C.border to Color(0xFFE2E2E2),
            C.text to Light.text,
            C.textMuted to Color(0xFF404040),
            C.textSubtle to Color(0xFF6B6B6B),
            C.accent to Light.brand,
            C.accentMuted to Color(0xFFEEEBFF),
            C.onAccent to Color.White,
            C.brandInk to Light.brand,
            C.foam to Color(0xFFA9A3FF),
            C.glow to Color(0xFFF5B544),
            C.onGlow to Light.text,
            C.glowInk to Color(0xFF8A5A00),
            C.glowTint to Color(0xFFFFF3DC),
            C.frost to Color(0xFFCFE6FA),
            C.frostInk to Color(0xFF1F5E94),
            C.ebb to Color(0xFFD9D9E3),
            C.commander to Color(0xFFB31717),
            C.sleep to Color(0xFF4250B8),
            C.activity to Color(0xFF1A7D44),
            C.energy to Color(0xFFB65015),
            C.water to Color(0xFF0B70A0),
            C.heart to Color(0xFFC92E55),
            C.danger to Color(0xFFD1105A),
            C.success to Color(0xFF007A62),
            C.up to Color(0xFF007A62),
            C.mid to Color(0xFFA86200),
            C.down to Color(0xFFD1105A),
            C.accentTint to Color(0xFFEEEBFF),
            C.borderStrong to Color(0xFFCFCFCF),
            C.navHighlight to Light.brand,
            C.onNavHighlight to Color.White,
            C.navBar to Color(0xFFF5F5F5),
            C.navActive to Light.text,
            C.navOnActive to Color(0xFFFFFFFF),
            C.navInactive to Color(0xFF6B6B6B),
        )
        defaultContentColor = Light.text
        defaultIndication = rememberColoredIndication(
            hoveredColor = Color.Black.copy(alpha = 0.04f),
            pressedColor = Color.Black.copy(alpha = 0.08f),
            focusedColor = Color.Black.copy(alpha = 0.06f),
        )
    }

    colorScheme(ColorScheme.Dark) {
        properties[colors] = mapOf(
            C.bg to Dark.bg,
            C.surface to Color(0xFF0D0D0D),
            C.surfaceMuted to Color(0xFF161616),
            C.border to Color(0xFF222222),
            C.text to Color(0xFFFFFFFF),
            C.textMuted to Color(0xFFA3A3A3),
            C.textSubtle to Color(0xFF7A7A7A),
            C.accent to Dark.brand,
            C.accentMuted to Color(0xFF1A1730),
            C.onAccent to Dark.bg,
            C.brandInk to Dark.brand,
            C.foam to Color(0xFFA9A3FF),
            C.glow to Color(0xFFF7C15F),
            C.onGlow to Dark.bg,
            C.glowInk to Color(0xFFF7C15F),
            C.glowTint to Color(0xFF3A2E14),
            C.frost to Color(0xFF1E3346),
            C.frostInk to Color(0xFF9CCBF5),
            C.ebb to Color(0xFF34343F),
            C.commander to Color(0xFFFF6464),
            C.sleep to Color(0xFF97A2F5),
            C.activity to Color(0xFF5BD394),
            C.energy to Color(0xFFFF9A57),
            C.water to Color(0xFF5CC8F2),
            C.heart to Color(0xFFFF7A97),
            C.danger to Color(0xFFFF4D8D),
            C.success to Color(0xFF22F5C3),
            C.up to Color(0xFF22F5C3),
            C.mid to Color(0xFFFFC61A),
            C.down to Color(0xFFFF4D8D),
            C.accentTint to Color(0xFF1A1730),
            C.borderStrong to Color(0xFF333333),
            C.navHighlight to Dark.brand,
            C.onNavHighlight to Dark.bg,
            C.navBar to Color(0xFF161616),
            C.navActive to Color(0xFFFFFFFF),
            C.navOnActive to Dark.bg,
            C.navInactive to Color(0xFF7A7A7A),
        )
        defaultContentColor = Color(0xFFFFFFFF)
        defaultIndication = rememberColoredIndication(
            hoveredColor = Color.White.copy(alpha = 0.04f),
            pressedColor = Color.White.copy(alpha = 0.08f),
            focusedColor = Color.White.copy(alpha = 0.06f),
        )
    }

    defaultTextStyle = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 22.sp)
    defaultTextSelectionColors = TextSelectionColors(
        handleColor = Light.brand,
        backgroundColor = Light.brand.copy(alpha = 0.3f),
    )

    properties[typography] = mapOf(
        TypeTokens.brand to TextStyle(
            fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp,
        ),
        TypeTokens.brandLarge to TextStyle(
            fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp,
        ),
        TypeTokens.tide to TextStyle(
            fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
            fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-1.7).sp, fontFeatureSettings = Tabular,
        ),
        TypeTokens.display to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp,
        ),
        TypeTokens.metric to TextStyle(
            fontFamily = Bricolage, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = Tabular,
        ),
        TypeTokens.title to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
        ),
        TypeTokens.headline to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp, lineHeight = 24.sp,
        ),
        TypeTokens.body to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.Normal,
            fontSize = 15.sp, lineHeight = 22.sp,
        ),
        TypeTokens.label to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp,
        ),
        TypeTokens.caption to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp,
        ),
        TypeTokens.overline to TextStyle(
            fontFamily = Figtree, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.9.sp,
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
        ShapeTokens.lg to RoundedCornerShape(28.dp),
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
    val up @Composable get() = Theme[colors][C.up]
    val mid @Composable get() = Theme[colors][C.mid]
    val down @Composable get() = Theme[colors][C.down]
    val accentTint @Composable get() = Theme[colors][C.accentTint]
    val borderStrong @Composable get() = Theme[colors][C.borderStrong]
    val sleep @Composable get() = Theme[colors][C.sleep]
    val activity @Composable get() = Theme[colors][C.activity]
    val energy @Composable get() = Theme[colors][C.energy]
    val water @Composable get() = Theme[colors][C.water]
    val heart @Composable get() = Theme[colors][C.heart]
    val brandInk @Composable get() = Theme[colors][C.brandInk]
    val foam @Composable get() = Theme[colors][C.foam]
    val glow @Composable get() = Theme[colors][C.glow]
    val onGlow @Composable get() = Theme[colors][C.onGlow]
    val glowInk @Composable get() = Theme[colors][C.glowInk]
    val glowTint @Composable get() = Theme[colors][C.glowTint]
    val frost @Composable get() = Theme[colors][C.frost]
    val frostInk @Composable get() = Theme[colors][C.frostInk]
    val ebb @Composable get() = Theme[colors][C.ebb]
    val commander @Composable get() = Theme[colors][C.commander]
    val navBar @Composable get() = Theme[colors][C.navBar]
    val navActive @Composable get() = Theme[colors][C.navActive]
    val navOnActive @Composable get() = Theme[colors][C.navOnActive]
    val navInactive @Composable get() = Theme[colors][C.navInactive]
    val navHighlight @Composable get() = Theme[colors][C.navHighlight]
    val onNavHighlight @Composable get() = Theme[colors][C.onNavHighlight]

    /**
     * Raised surface shared by the navbar, hero card, primary button and segmented control:
     * white in light mode, charcoal in dark mode. [onInverse] is the text/icon colour on it.
     */
    val inverse @Composable get() = navBar
    val onInverse @Composable get() = navActive
    val onInverseMuted @Composable get() = navInactive

    /** Highest-contrast fill for active/selected marks (navbar circle, checks, selected pills). */
    val strong @Composable get() = navActive
    val onStrong @Composable get() = navOnActive
}

object TypeAccess {
    val brand @Composable get() = Theme[typography][TypeTokens.brand]
    val brandLarge @Composable get() = Theme[typography][TypeTokens.brandLarge]
    val tide @Composable get() = Theme[typography][TypeTokens.tide]
    val overline @Composable get() = Theme[typography][TypeTokens.overline]
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
