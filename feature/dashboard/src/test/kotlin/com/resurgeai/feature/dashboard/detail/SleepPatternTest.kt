package com.resurgeai.feature.dashboard.detail

import com.resurgeai.core.model.SleepSegment
import com.resurgeai.core.model.SleepSession
import com.resurgeai.core.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SleepPatternTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    /** A night waking on [wake] at 07:00 after 23:00: two hours deep, then light until morning. */
    private fun night(wake: LocalDate, staged: Boolean = true): SleepSession {
        val start = wake.minusDays(1).atTime(23, 0).atZone(zone).toInstant()
        val end = wake.atTime(7, 0).atZone(zone).toInstant()
        val deepEnd = start.plus(Duration.ofHours(2))
        return SleepSession(
            start = start,
            end = end,
            stages = if (staged) mapOf(SleepStage.DEEP to Duration.ofHours(2), SleepStage.LIGHT to Duration.ofHours(6)) else emptyMap(),
            source = null,
            timeline = if (staged) listOf(SleepSegment(start, deepEnd, SleepStage.DEEP), SleepSegment(deepEnd, end, SleepStage.LIGHT)) else emptyList(),
        )
    }

    private val monday = LocalDate.of(2026, 11, 9)
    private val saturday = LocalDate.of(2026, 11, 14)

    @Test
    fun `folds nights onto one clock with stage shares per slot`() {
        val p = SleepPattern.build(listOf(night(monday), night(monday.plusDays(1))), zone)
        assertEquals(2, p.nights)
        assertEquals(LocalTime.of(23, 0), p.windowStart)
        assertEquals(LocalTime.of(7, 0), p.windowEnd)
        assertEquals(48, p.bins)
        // Rows are awake, REM, light, deep.
        assertEquals(1f, p.frequency[3][0], 0f)
        assertEquals(0f, p.frequency[2][0], 0f)
        assertEquals(1f, p.frequency[2][30], 0f)
        assertEquals(0f, p.frequency[0].sum(), 0f)
        assertEquals(listOf(0L, 0L, 360L, 120L), p.stageMinutes)
        assertEquals(LocalTime.of(23, 0), p.usualBed)
        assertEquals(LocalTime.of(7, 0), p.usualWake)
        assertEquals(Duration.ofHours(8), p.asleep)
    }

    @Test
    fun `reads the usual stage at a time, and nothing when people are up`() {
        val p = SleepPattern.build(listOf(night(monday)), zone)
        assertEquals(SleepStage.DEEP to 1f, p.stageAt(p.fractionOf(LocalTime.of(0, 30))))
        assertEquals(SleepStage.LIGHT to 1f, p.stageAt(p.fractionOf(LocalTime.of(5, 0))))
        assertEquals(LocalTime.of(3, 0), p.timeAt(0.5f))
    }

    @Test
    fun `nights without stages count as light sleep`() {
        val p = SleepPattern.build(listOf(night(monday, staged = false)), zone)
        assertEquals(1f, p.frequency[2][0], 0f)
        assertEquals(480L, p.stageMinutes[2])
    }

    @Test
    fun `splits weeknights from weekends by the day the night ended`() {
        val nights = listOf(night(monday), night(saturday), night(saturday.plusDays(1)))
        assertEquals(1, SleepPattern.build(nights, zone, NightSet.WEEKNIGHTS).nights)
        assertEquals(2, SleepPattern.build(nights, zone, NightSet.WEEKENDS).nights)
    }

    @Test
    fun `no nights gives the default window and no readings`() {
        val p = SleepPattern.build(emptyList(), zone)
        assertEquals(0, p.nights)
        assertEquals(SleepPattern.DEFAULT_START, p.windowStart)
        assertEquals(SleepPattern.DEFAULT_END, p.windowEnd)
        assertNull(p.usualBed)
        assertNull(p.stageAt(0.5f))
    }
}
