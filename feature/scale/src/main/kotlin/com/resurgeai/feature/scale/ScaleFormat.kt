package com.resurgeai.feature.scale

import com.resurgeai.core.model.UnitSystem
import com.resurgeai.core.model.WeightMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A reading in the person's units: "78.4 kg", "172.8 lb". Stored values are always kg. */
internal class WeightText(private val units: UnitSystem) {
    fun format(kg: Double): String {
        val imperial = units == UnitSystem.IMPERIAL
        val value = if (imperial) kg / WeightMath.KG_PER_LB else kg
        return String.format(Locale.getDefault(), "%.1f %s", value, if (imperial) "lb" else "kg")
    }
}

private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "Last weigh-in 7:42", "Last weigh-in yesterday, 7:42", "Last weigh-in 12 Oct". */
internal fun lastWeighIn(at: Instant, today: LocalDate, clock: DateTimeFormatter, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = at.atZone(zone)
    val day = time.toLocalDate()
    return "Last weigh-in " + when (day) {
        today -> time.format(clock)
        today.minusDays(1) -> "yesterday, ${time.format(clock)}"
        else -> day.format(DAY_MONTH)
    }
}
