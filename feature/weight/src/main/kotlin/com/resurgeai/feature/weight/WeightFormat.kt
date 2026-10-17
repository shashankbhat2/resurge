package com.resurgeai.feature.weight

import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weight in the person's units. Everything is stored in kg; only display converts. */
internal class WeightUnits(val system: UnitSystem) {
    private val imperial get() = system == UnitSystem.IMPERIAL
    val label: String get() = if (imperial) "lb" else "kg"

    fun toUnit(kg: Double): Double = if (imperial) kg / WeightMath.KG_PER_LB else kg
    fun toKg(value: Double): Double = if (imperial) value * WeightMath.KG_PER_LB else value

    /** "78.4" */
    fun number(kg: Double): String = decimal(toUnit(kg))

    /** "78.4 kg" */
    fun format(kg: Double): String = "${number(kg)} $label"

    /** A weekly pace: "0.25 kg", "0.5 kg", "0.6 lb". */
    fun pace(kgPerWeek: Double): String {
        val v = toUnit(kgPerWeek)
        val text = if (imperial) decimal(v) else String.format(Locale.getDefault(), "%.2f", v).trimEnd('0').trimEnd('.', ',')
        return "$text $label"
    }

    /** "−1.2 kg", "+0.4 kg", "0.0 kg" for a change in kg. */
    fun signed(deltaKg: Double): String {
        val v = toUnit(deltaKg)
        val rounded = (v * 10).roundToInt() / 10.0
        val sign = when {
            rounded < 0 -> "−"
            rounded > 0 -> "+"
            else -> ""
        }
        return "$sign${decimal(abs(rounded))} $label"
    }

    companion object {
        fun decimal(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())

/** "18 Sep", with the year when it isn't [today]'s year. */
internal fun shortDate(date: LocalDate, today: LocalDate = LocalDate.now()): String =
    date.format(if (date.year == today.year) DAY_MONTH else DAY_MONTH_YEAR)

/** "Today", "Yesterday", "Wednesday" within a week, else "18 Sep". */
internal fun relativeDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.isAfter(today.minusDays(7)) -> date.format(WEEKDAY)
    else -> shortDate(date, today)
}

/** "12 weeks", "5 days", "1 week". */
internal fun spanLabel(days: Long): String = when {
    days >= 14 -> "${(days / 7.0).roundToInt()} weeks"
    days == 7L -> "1 week"
    days == 1L -> "1 day"
    else -> "$days days"
}
