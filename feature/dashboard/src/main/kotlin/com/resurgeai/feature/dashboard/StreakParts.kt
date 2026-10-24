package com.resurgeai.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.resurgeai.core.designsystem.components.Icon
import com.resurgeai.core.designsystem.components.ResurgeIcons
import com.resurgeai.core.designsystem.components.dayStatusFill
import com.resurgeai.core.designsystem.theme.Resurge
import com.resurgeai.core.model.DayStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

/**
 * One day of a week strip, as Today's week card draws it: met, freeze or missed as a filled square
 * (with a check or snowflake), today outlined in the accent with how much of it is done, later
 * days outlined in grey.
 */
@Composable
internal fun StripDay(date: LocalDate, status: DayStatus?, isToday: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val label = when {
        isToday && status == DayStatus.MET -> "today, done"
        isToday -> "today, ${(progress * 100).toInt()} percent"
        status == DayStatus.MET -> "done"
        status == DayStatus.FORGIVEN -> "covered by the freeze"
        status == DayStatus.MISSED -> "missed"
        status == DayStatus.OPEN -> "still open"
        else -> "not yet"
    }
    val shape = RoundedCornerShape(8.dp)
    val fill = status?.let { dayStatusFill(it) }?.let { if (status == DayStatus.MET) it else it.copy(alpha = 0.28f) }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .then(
                when {
                    fill != null -> Modifier.background(fill)
                    isToday -> Modifier.border(2.dp, Resurge.colors.accent, shape)
                    else -> Modifier.border(1.dp, Resurge.colors.borderStrong, shape)
                },
            )
            .semantics { contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))}: $label" },
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            DayStatus.MET -> Icon(ResurgeIcons.Check, contentDescription = null, tint = Resurge.colors.bg, size = 15.dp)
            DayStatus.FORGIVEN -> Icon(ResurgeIcons.Snowflake, contentDescription = null, tint = Resurge.colors.mid, size = 14.dp)
            else -> Text(
                if (isToday && progress > 0f) "${(progress * 100).toInt()}%" else "${date.dayOfMonth}",
                style = Resurge.type.caption,
                color = if (isToday) Resurge.colors.text else Resurge.colors.textSubtle,
            )
        }
    }
}
