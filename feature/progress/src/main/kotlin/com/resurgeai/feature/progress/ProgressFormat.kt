package com.resurgeai.feature.progress

import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** "78.4 kg" / "172.8 lb". Weights are stored in kg. */
internal fun formatWeight(kg: Double, units: UnitSystem): String {
    val imperial = units == UnitSystem.IMPERIAL
    val v = if (imperial) kg / WeightMath.KG_PER_LB else kg
    return String.format(Locale.getDefault(), "%.1f %s", v, if (imperial) "lb" else "kg")
}

/** "−3.1 kg", "+0.4 kg", for a change in kg. */
internal fun formatChange(deltaKg: Double, units: UnitSystem): String {
    val imperial = units == UnitSystem.IMPERIAL
    val v = ((if (imperial) deltaKg / WeightMath.KG_PER_LB else deltaKg) * 10).roundToInt() / 10.0
    val sign = when {
        v < 0 -> "−"
        v > 0 -> "+"
        else -> ""
    }
    return sign + String.format(Locale.getDefault(), "%.1f %s", abs(v), if (imperial) "lb" else "kg")
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** "18 Sep", with the year when it isn't [today]'s year. */
internal fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    date.format(if (date.year == today.year) DAY_MONTH else DAY_MONTH_YEAR)
