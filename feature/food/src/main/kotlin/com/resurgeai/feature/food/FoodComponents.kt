package com.resurgeai.feature.food

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.resurgeai.core.designsystem.components.MacroVisuals
import com.resurgeai.core.designsystem.components.MetricChip
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.Nutrition
import kotlin.math.roundToInt

/** Protein · carbs · fat chips with their Tabler icons (meat, wheat, droplet). */
@Composable
internal fun MacroChips(n: Nutrition, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricChip("${n.protein.roundToInt()}g protein", icon = ResurgeIcons.Protein, tint = MacroVisuals.protein())
        MetricChip("${n.carbs.roundToInt()}g carbs", icon = ResurgeIcons.Carbs, tint = MacroVisuals.carbs())
        MetricChip("${n.fat.roundToInt()}g fat", icon = ResurgeIcons.Fat, tint = MacroVisuals.fat())
    }
}

/** A JPEG held in memory (meal photo or thumbnail). */
@Composable
internal fun JpegImage(bytes: ByteArray, contentDescription: String?, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() } ?: return
    Image(bitmap, contentDescription, modifier.clip(Resurge.shape.md), contentScale = ContentScale.Crop)
}

internal fun kcal(v: Double): String = "%,d kcal".format(v.roundToInt())
