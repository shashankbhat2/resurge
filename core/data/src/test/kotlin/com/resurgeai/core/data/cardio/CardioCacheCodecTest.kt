package com.resurgeai.core.data.cardio

import com.resurgeai.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CardioCacheCodecTest {
    @Test fun `aggregate cache round trips source titles fractional durations and unknown coverage`() {
        val summary = DailyCardioPoints(LocalDate.of(2026, 10, 20), listOf(
            CardioContribution(CardioMethod.CADENCE, "Watch", null, null, 35_000, 35_000),
            CardioContribution(CardioMethod.ACTIVITY_ESTIMATE, "Manual", "workout", "Cycle 🚲", 60_000, 120_000),
        ), 12_000, CardioStatus.LIMITED_DATA, calculatedAt = Instant.parse("2026-10-20T12:00:00Z"))
        val entry = CachedCardioDay(summary, "fingerprint")
        assertEquals(entry, CardioCacheCodec.decode(CardioCacheCodec.encode(entry)))
    }
    @Test fun `corrupt truncated and future version caches are ignored`() {
        assertNull(CardioCacheCodec.decode("not base64!"))
        assertNull(CardioCacheCodec.decode(""))
        assertNull(CardioCacheCodec.decode(java.util.Base64.getEncoder().encodeToString(byteArrayOf(0, 0, 0, 99))))
    }
}
