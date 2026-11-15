package com.resurgeai.core.data.cardio

import com.resurgeai.core.model.CardioContribution
import com.resurgeai.core.model.CardioMethod
import com.resurgeai.core.model.DailyCardioPoints
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CardioSummaryUploaderTest {
    @Test
    fun `uploads the day total, whether any of it was estimated, and the date for range queries`() {
        val day = DailyCardioPoints(
            LocalDate.of(2026, 11, 14),
            listOf(
                CardioContribution(CardioMethod.CADENCE, "Watch", null, null, 20 * 60_000L, 20 * 60_000L),
                CardioContribution(CardioMethod.ACTIVITY_ESTIMATE, "Yoga", null, null, 30 * 60_000L, 10 * 60_000L),
            ),
            calculatedAt = Instant.EPOCH,
        )
        val f = CardioSummaryUploader.fields(day)
        assertEquals("2026-11-14", f["date"])
        assertEquals(day.points, f["cardioPoints"])
        assertEquals(true, f["cardioEstimated"])
    }
}
