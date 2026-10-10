package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.theme.Resurge

/** Circular avatar: remote photo when available, initials underneath as placeholder/fallback. */
@Composable
fun Avatar(
    name: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val initials = name.orEmpty()
        .split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Box(
        modifier = modifier
            .size(size)
            .clip(Resurge.shape.pill)
            .background(Resurge.colors.accentMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = Resurge.type.label,
            fontSize = (size.value * 0.38f).sp,
            color = Resurge.colors.accent,
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
