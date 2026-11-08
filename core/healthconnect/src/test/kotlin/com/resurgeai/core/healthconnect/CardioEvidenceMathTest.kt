package com.resurgeai.core.healthconnect

import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.SourceKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class CardioEvidenceMathTest {
    private val start = Instant.parse("2026-10-20T10:00:00Z")
    private val source = DataSource(SourceKind.WEARABLE, "watch", "Watch", null)

    @Test fun `cadence samples stop at next sample record end or sixty seconds`() {
        val values = CardioEvidenceMath.cadence(start, start.plusSeconds(200), listOf(
            start to 110.0, start.plusSeconds(30) to 90.0, start.plusSeconds(180) to 140.0,
        ), source, "id")
        assertEquals(listOf(30L, 60L, 20L), values.map { it.end.epochSecond - it.start.epochSecond })
        assertEquals(listOf(1, 0, 2), values.map { it.intensity })
    }
    @Test fun `invalid samples are discarded and duplicate times resolve deterministically`() {
        val samples = listOf(start to 140.0, start to 110.0, start.minusSeconds(1) to 110.0, start.plusSeconds(30) to Double.NaN)
        assertEquals(CardioEvidenceMath.cadence(start, start.plusSeconds(60), samples, source, "id"),
            CardioEvidenceMath.cadence(start, start.plusSeconds(60), samples.reversed(), source, "id"))
        assertEquals(1, CardioEvidenceMath.cadence(start, start.plusSeconds(60), samples, source, "id").single().intensity)
    }
    @Test fun `only short step records can imply cadence including zero as valid evidence`() {
        assertNull(CardioEvidenceMath.steps(start, start.plusSeconds(61), 200, source, "long"))
        assertNull(CardioEvidenceMath.steps(start, start, 200, source, "zero_duration"))
        assertNull(CardioEvidenceMath.steps(start, start.plusSeconds(60), -1, source, "invalid"))
        assertEquals(1, CardioEvidenceMath.steps(start, start.plusSeconds(30), 50, source, "moderate")!!.intensity)
        assertEquals(2, CardioEvidenceMath.steps(start, start.plusSeconds(60), 130, source, "vigorous")!!.intensity)
        assertEquals(0, CardioEvidenceMath.steps(start, start.plusSeconds(60), 0, source, "still")!!.intensity)
    }
    @Test fun `reader retrieves more than five pages`() = runBlocking {
        var calls = 0
        val result = readCardioPages { token ->
            val page = token?.toInt() ?: 0
            calls++
            listOf(page) to if (page < 8) (page + 1).toString() else null
        }
        assertEquals(9, calls)
        assertEquals((0..8).toList(), result)
    }
    @Test fun `reader propagates failure and cancellation rather than returning a partial list`() = runBlocking {
        for (failure in listOf(IllegalStateException("rate limit"), CancellationException("cancelled"))) {
            var calls = 0
            try {
                readCardioPages { _: String? -> if (++calls == 2) throw failure else listOf(1) to "next" }
                fail("Should not return incomplete data")
            } catch (actual: Exception) { assertSame(failure, actual) }
        }
    }
    @Test fun `reader rejects repeated page tokens`() = runBlocking {
        try { readCardioPages { _: String? -> listOf(1) to "same" }; fail("Repeated token must fail") }
        catch (_: IllegalStateException) { }
    }
}
