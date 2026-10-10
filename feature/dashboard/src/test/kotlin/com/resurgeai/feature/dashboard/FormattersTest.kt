package com.resurgeai.feature.dashboard

import com.resurgeai.core.model.DataSource
import com.resurgeai.core.model.SourceBreakdown
import com.resurgeai.core.model.SourceKind
import com.resurgeai.core.model.SourceShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.Locale

class FormattersTest {
    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    private fun source(kind: SourceKind, device: String? = null, app: String? = null) =
        DataSource(kind, "pkg", app, device)

    @Test
    fun `durations`() {
        assertEquals("7h 12m", Formatters.duration(Duration.ofMinutes(432)))
        assertEquals("8h", Formatters.duration(Duration.ofHours(8)))
        assertEquals("45m", Formatters.duration(Duration.ofMinutes(45)))
        assertEquals("0m", Formatters.duration(Duration.ZERO))
        assertEquals("7h 30m", Formatters.hours(7.5))
    }

    @Test
    fun `numbers`() {
        assertEquals("8,412", Formatters.count(8412))
        assertEquals("5.80", Formatters.kilometres(5_800.0))
        assertEquals("12.3", Formatters.kilometres(12_340.0))
        assertEquals("2,381", Formatters.kcal(2380.6))
    }

    @Test
    fun `source label prefers device then app then kind`() {
        assertEquals("Galaxy Watch6", Formatters.sourceLabel(source(SourceKind.WEARABLE, "Galaxy Watch6", "Samsung Health")))
        assertEquals("Samsung Health", Formatters.sourceLabel(source(SourceKind.WEARABLE, app = "Samsung Health")))
        assertEquals("Phone", Formatters.sourceLabel(source(SourceKind.PHONE)))
        assertEquals("Manual · Fitbit", Formatters.sourceLabel(source(SourceKind.MANUAL, app = "Fitbit")))
    }

    @Test
    fun `summary says phone plus wearable when both contribute`() {
        val mixed = SourceBreakdown(
            listOf(
                SourceShare(source(SourceKind.WEARABLE, "Pixel Watch 3"), 0.6f),
                SourceShare(source(SourceKind.PHONE, "Pixel 9"), 0.4f),
            ),
        )
        assertEquals("Phone + Wearable", Formatters.sourceSummary(mixed)?.label)

        val mostlyWatch = SourceBreakdown(
            listOf(
                SourceShare(source(SourceKind.WEARABLE, "Pixel Watch 3"), 0.95f),
                SourceShare(source(SourceKind.PHONE, "Pixel 9"), 0.05f),
            ),
        )
        assertEquals("Pixel Watch 3", Formatters.sourceSummary(mostlyWatch)?.label)
        assertNull(Formatters.sourceSummary(SourceBreakdown.Empty))
    }
}
