package com.resurgeai.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Whether times render on a 24-hour clock. Provided at the app root from the user's setting. */
val LocalUse24HourClock = compositionLocalOf { true }

/** Formatter for clock times ("23:44" or "11:44 PM") following [LocalUse24HourClock]. */
@Composable
@ReadOnlyComposable
fun clockFormatter(): DateTimeFormatter = clockFormatter(LocalUse24HourClock.current)

fun clockFormatter(use24Hour: Boolean): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
