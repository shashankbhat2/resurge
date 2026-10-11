package com.resurgeai.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.resurgeai.core.designsystem.R

/**
 * The coach's face for the selected persona (Friend / Commander), drawn in its own colours.
 * [heading] uses the gradient variant shown next to the "Coach" title and before a chat starts;
 * otherwise the flat variant used in the navbar.
 */
@Composable
fun CoachMascot(commander: Boolean, size: Dp, modifier: Modifier = Modifier, heading: Boolean = false) {
    val res = when {
        commander && heading -> R.drawable.ic_coach_commander_h
        commander -> R.drawable.ic_coach_commander
        heading -> R.drawable.ic_coach_friend_h
        else -> R.drawable.ic_coach_friend
    }
    Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
}
