package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.healthconnect.HealthMath
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import java.time.LocalDate

/** One bar / card on the detail screen: a single day (week view) or a calendar week (month view). */
data class PeriodValue(
    val start: LocalDate,
    val end: LocalDate,
    /** Day value, or the average per day with data for a week. */
    val value: Double?,
    /** Sum over the period for additive metrics (steps, distance, kcal, exercise); else null. */
    val total: Double?,
    val daysWithData: Int,
    val daysInPeriod: Int,
)

/** Metrics where adding days together is meaningful. */
val HealthMetric.isAdditive: Boolean
    get() = this == HealthMetric.STEPS || this == HealthMetric.DISTANCE ||
        this == HealthMetric.CALORIES_BURNED || this == HealthMetric.EXERCISE

object Periods {
    fun daily(series: MetricSeries): List<PeriodValue> = series.points.map { p ->
        PeriodValue(
            start = p.date,
            end = p.date,
            value = p.value,
            total = p.value.takeIf { series.metric.isAdditive },
            daysWithData = if (p.value != null) 1 else 0,
            daysInPeriod = 1,
        )
    }

    /** Groups days into Monday-start calendar weeks. The first/last week may be partial. */
    fun weekly(series: MetricSeries): List<PeriodValue> = series.points
        .groupBy { HealthMath.weekStart(it.date) }
        .toSortedMap()
        .map { (_, days) ->
            val values = days.mapNotNull { it.value }
            PeriodValue(
                start = days.first().date,
                end = days.last().date,
                value = values.takeIf { it.isNotEmpty() }?.average(),
                total = values.takeIf { it.isNotEmpty() && series.metric.isAdditive }?.sum(),
                daysWithData = values.size,
                daysInPeriod = days.size,
            )
        }
}
