package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.model.DailyValue
import com.resurgeai.core.model.HealthMetric
import com.resurgeai.core.model.MetricSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {
    // Thu 1 Oct .. Sat 10 Oct 2026: a partial week (Thu-Sat) then a full Sunday-Saturday week.
    private val days = (0L until 10L).map { LocalDate.of(2026, 10, 1).plusDays(it) }

    private fun series(metric: HealthMetric, values: List<Double?>) =
        MetricSeries(metric, days.zip(values) { d, v -> DailyValue(d, v) })

    @Test
    fun `weekly groups by Sunday-start weeks`() {
        val weeks = Periods.weekly(series(HealthMetric.STEPS, List(10) { 1000.0 }))
        assertEquals(2, weeks.size)
        assertEquals(LocalDate.of(2026, 10, 1), weeks[0].start) // Thu
        assertEquals(LocalDate.of(2026, 10, 3), weeks[0].end) // Sat
        assertEquals(3, weeks[0].daysInPeriod)
        assertEquals(LocalDate.of(2026, 10, 4), weeks[1].start) // Sun
        assertEquals(LocalDate.of(2026, 10, 10), weeks[1].end) // Sat (today)
        assertEquals(7, weeks[1].daysInPeriod)
    }

    @Test
    fun `weekly value is the average of days with data, total only for additive metrics`() {
        val values = listOf(null, 2000.0, 4000.0, 1000.0, 1000.0, 1000.0, 1000.0, 1000.0, 1000.0, 1000.0)
        val steps = Periods.weekly(series(HealthMetric.STEPS, values))
        assertEquals(3000.0, steps[0].value!!, 0.0) // Thu null, Fri 2000, Sat 4000
        assertEquals(6000.0, steps[0].total!!, 0.0)
        assertEquals(2, steps[0].daysWithData)

        val hr = Periods.weekly(series(HealthMetric.HEART_RATE, values))
        assertNull(hr[0].total)
    }

    @Test
    fun `daily keeps one period per day`() {
        val daily = Periods.daily(series(HealthMetric.SLEEP, List(10) { 7.5 }))
        assertEquals(10, daily.size)
        assertNull("sleep is not additive", daily[0].total)
        assertEquals(daily[3].start, daily[3].end)
    }
}
