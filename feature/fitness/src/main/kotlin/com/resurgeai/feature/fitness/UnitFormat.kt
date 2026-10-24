package com.resurgeai.feature.fitness

import com.resurgeai.core.model.FitnessMath
import com.resurgeai.core.model.UnitSystem
import java.time.Duration
import java.util.Locale
import kotlin.math.roundToInt

/** Display formatting and unit conversion for the Fitness tab. Values are stored in metric. */
internal class UnitFormat(val units: UnitSystem) {
    private val imperial get() = units == UnitSystem.IMPERIAL

    val distanceUnit: String get() = if (imperial) "mi" else "km"
    val weightUnit: String get() = if (imperial) "lb" else "kg"
    val elevationUnit: String get() = if (imperial) "ft" else "m"

    /** Metres to km or miles, for steppers. */
    fun toDistanceUnit(m: Double): Double = if (imperial) m / FitnessMath.M_PER_MI else m / 1000.0
    fun fromDistanceUnit(v: Double): Double = if (imperial) v * FitnessMath.M_PER_MI else v * 1000.0

    fun toWeightUnit(kg: Double): Double = if (imperial) kg / FitnessMath.KG_PER_LB else kg
    fun fromWeightUnit(v: Double): Double = if (imperial) v * FitnessMath.KG_PER_LB else v
    /** − / + on a set's weight: a tenth of a pound or kilo. Bigger jumps are typed. */
    val weightStep: Double get() = 0.1

    fun toElevationUnit(m: Double): Double = if (imperial) m / FitnessMath.M_PER_FT else m
    fun fromElevationUnit(v: Double): Double = if (imperial) v * FitnessMath.M_PER_FT else v

    fun distance(m: Double): String = when {
        imperial -> "${decimal(m / FitnessMath.M_PER_MI, 2)} mi"
        m < 1000 -> "${m.roundToInt()} m"
        else -> "${decimal(m / 1000.0, 2)} km"
    }

    fun distanceValue(v: Double): String = "${decimal(v, 1)} $distanceUnit"
    fun weight(kg: Double): String = "${decimal(toWeightUnit(kg), 1)} $weightUnit"
    fun weightValue(v: Double): String = "${decimal(v, 1)} $weightUnit"
    fun elevation(m: Double): String = "${toElevationUnit(m).roundToInt()} $elevationUnit"
    fun volume(kg: Double): String = "%,d %s".format(Locale.getDefault(), toWeightUnit(kg).roundToInt(), weightUnit)

    fun pace(secPerKm: Long): String {
        val secs = if (imperial) (secPerKm * FitnessMath.M_PER_MI / 1000).toLong() else secPerKm
        return "%d:%02d /%s".format(secs / 60, secs % 60, distanceUnit)
    }

    fun speed(kmh: Double): String = if (imperial) "${decimal(kmh / 1.609344, 1)} mph" else "${decimal(kmh, 1)} km/h"

    companion object {
        fun duration(d: Duration): String {
            val minutes = d.toMinutes()
            return if (minutes < 60) "$minutes min" else "%d h %02d".format(minutes / 60, minutes % 60)
        }

        fun decimal(v: Double, places: Int): String {
            val s = "%.${places}f".format(Locale.getDefault(), v)
            return if (s.contains('.') || s.contains(',')) s.trimEnd('0').trimEnd('.', ',') else s
        }
    }
}
