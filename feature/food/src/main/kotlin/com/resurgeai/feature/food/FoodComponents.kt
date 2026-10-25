package com.resurgeai.feature.food

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.resurgeai.core.designsystem.components.Divider
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Micro
import com.resurgeai.core.model.Nutrition
import kotlin.math.roundToInt

/** Calories from each macro (4 / 4 / 9 kcal per gram): the honest way to compare them. */
internal data class MacroShares(val protein: Float, val carbs: Float, val fat: Float) {
    companion object {
        fun of(n: Nutrition): MacroShares {
            val p = n.protein * 4
            val c = n.carbs * 4
            val f = n.fat * 9
            val total = (p + c + f).takeIf { it > 0 } ?: return MacroShares(0f, 0f, 0f)
            return MacroShares((p / total).toFloat(), (c / total).toFloat(), (f / total).toFloat())
        }
    }
}

/**
 * A slim bar split by each macro's share of the calories (protein, carbs, fat in their colours),
 * with "20p · 64c · 9f" grams beside it.
 */
@Composable
internal fun MacroSplit(n: Nutrition, modifier: Modifier = Modifier) {
    val shares = MacroShares.of(n)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(6.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(shares.protein to MacroVisuals.protein(), shares.carbs to MacroVisuals.carbs(), shares.fat to MacroVisuals.fat())
                .filter { it.first > 0f }
                .forEach { (share, color) -> Box(Modifier.weight(share).fillMaxHeight().background(color)) }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${n.protein.roundToInt()}p · ${n.carbs.roundToInt()}c · ${n.fat.roundToInt()}f",
            style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
            color = Resurge.colors.textSubtle,
        )
    }
}

/** The meal's photo, or a bowl on a `surface-muted` tile when there isn't one. */
@Composable
internal fun MealThumb(thumbnail: ByteArray?, size: Dp = 52.dp) {
    if (thumbnail != null) {
        JpegImage(thumbnail, null, Modifier.size(size), shape = Resurge.shape.sm)
    } else {
        Box(Modifier.size(size).clip(Resurge.shape.sm).background(Resurge.colors.surfaceMuted), contentAlignment = Alignment.Center) {
            Icon(ResurgeIcons.Bowl, contentDescription = null, tint = Resurge.colors.energy, size = size * 0.46f)
        }
    }
}

/** Rounded `surface` card holding rows split by inset hairlines. */
@Composable
internal fun FoodGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(Resurge.shape.md).background(Resurge.colors.surface), content = content)
}

@Composable
internal fun GroupDivider(inset: Dp = 16.dp) = Divider(Modifier.padding(start = inset))

/** Pill choice: `brand-tint` with `brand-ink` text when selected, like the editor's day chips. */
@Composable
internal fun ChoiceChip(label: String, selected: Boolean = false, muted: Boolean = false, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        role = Role.Button,
        modifier = Modifier
            .clip(Resurge.shape.pill)
            .background(
                when {
                    selected -> Resurge.colors.accentMuted
                    muted -> Color.Transparent
                    else -> Resurge.colors.surface
                },
            )
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        indication = LocalIndication.current,
    ) {
        Text(
            label,
            style = Resurge.type.label.copy(fontWeight = if (selected) Resurge.type.headline.fontWeight else Resurge.type.label.fontWeight),
            color = when {
                selected -> Resurge.colors.brandInk
                muted -> Resurge.colors.textMuted
                else -> Resurge.colors.text
            },
        )
    }
}

/** A JPEG held in memory (meal photo or thumbnail). */
@Composable
internal fun JpegImage(
    bytes: ByteArray,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = Resurge.shape.md,
) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    Image(bitmap, contentDescription, modifier.clip(shape), contentScale = ContentScale.Crop)
}

internal fun kcal(v: Double): String = "%,d kcal".format(v.roundToInt())

/** "1,249" without the unit, for numbers set large with the unit beside them. */
internal fun kcalNumber(v: Double): String = "%,d".format(v.roundToInt())

/** One macro in the review's legend: its colour, name, grams and share of the calories. */
@Composable
internal fun MacroLegendRow(label: String, grams: Double, share: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(Resurge.shape.pill).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = Resurge.type.label, color = Resurge.colors.textMuted, modifier = Modifier.width(64.dp))
        Text("${grams.roundToInt()} g", style = Resurge.type.headline.copy(fontFeatureSettings = "tnum"))
        Spacer(Modifier.width(6.dp))
        Text("${(share * 100).roundToInt()}%", style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"), color = Resurge.colors.textSubtle)
    }
}

/**
 * Micronutrients in [n] against an adult daily guide, as rows with a slim bar. Only nutrients the
 * sources report are shown; limits (sugar, saturated fat, sodium, cholesterol) are labelled "limit".
 * Neutral colours throughout: this is information, not a score. Shows [collapsed] rows until
 * expanded.
 */
@Composable
internal fun MicronutrientList(n: Nutrition, collapsed: Int = 5, modifier: Modifier = Modifier) {
    val rows = Micro.entries.mapNotNull { m -> n[m]?.let { m to it } }
    if (rows.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded || rows.size <= collapsed + 1) rows else rows.take(collapsed)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shown.forEach { (m, value) -> MicroRow(m, value) }
        if (shown.size < rows.size || expanded) {
            UnstyledButton(
                onClick = { expanded = !expanded },
                role = Role.Button,
                indication = LocalIndication.current,
                modifier = Modifier.clip(Resurge.shape.pill),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                Text(
                    if (expanded) "Show fewer" else "Show all ${rows.size}",
                    style = Resurge.type.label.copy(fontWeight = Resurge.type.headline.fontWeight),
                    color = Resurge.colors.brandInk,
                )
            }
        }
    }
}

@Composable
private fun MicroRow(m: Micro, value: Double) {
    val share = (value / m.daily).toFloat()
    val amount = when {
        value >= 100 -> "%,d".format(value.roundToInt())
        value >= 10 -> "%.0f".format(value)
        else -> "%.1f".format(value)
    }
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, style = Resurge.type.label, modifier = Modifier.weight(1f))
            Text("$amount ${m.unit}", style = Resurge.type.label.copy(fontFeatureSettings = "tnum"))
            Spacer(Modifier.width(8.dp))
            Text(
                "${(share * 100).roundToInt()}%${if (m.limit) " of limit" else ""}",
                style = Resurge.type.caption.copy(fontFeatureSettings = "tnum"),
                color = Resurge.colors.textSubtle,
                modifier = Modifier.width(78.dp),
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(Resurge.shape.pill).background(Resurge.colors.surfaceMuted)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0.02f, 1f)).clip(Resurge.shape.pill)
                    .background(if (m.limit) Resurge.colors.textMuted else Resurge.colors.accent),
            )
        }
    }
}

/**
 * Where a food's numbers came from, in one line under its name: "IFCT 2017 (ICMR-NIN) · Red gram,
 * dal", or a nudge when it is an estimate or an unconfirmed web result.
 */
@Composable
internal fun SourceLine(item: FoodItem, modifier: Modifier = Modifier) {
    val source = item.source ?: return
    val unverified = item.unverified
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (unverified) ResurgeIcons.AlertCircle else ResurgeIcons.Database,
            contentDescription = null,
            tint = if (unverified) Resurge.colors.glowInk else Resurge.colors.textSubtle,
            size = 12.dp,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            when {
                source.isEstimate -> "Estimate: no nutrition table had this food"
                unverified -> "${source.name}: not confirmed, check it"
                source.detail != null && source.detail != item.name -> "${source.name} · ${source.detail}"
                else -> source.name
            },
            style = Resurge.type.caption,
            color = if (unverified) Resurge.colors.glowInk else Resurge.colors.textSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
