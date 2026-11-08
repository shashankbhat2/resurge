package com.resurgeai.core.healthconnect

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

@Suppress("DEPRECATION")
class HealthAppLabelsTest {
    private val context = mockk<Context>()
    private val pm = mockk<PackageManager>()
    private val labels = HealthAppLabels(context)

    init { every { context.packageManager } returns pm }

    @Test fun `known apps keep their stable labels without package lookups`() {
        assertEquals("Samsung Health", labels.label("com.sec.android.app.shealth"))
        verify(exactly = 0) { context.packageManager }
    }

    @Test fun `unlisted installed apps use and cache their application label`() {
        val pkg = "com.example.trailjournal"
        val info = mockk<ApplicationInfo>()
        every { pm.getApplicationInfo(pkg, 0) } returns info
        every { pm.getApplicationLabel(info) } returns "Trail Journal"
        repeat(2) { assertEquals("Trail Journal", labels.label(pkg)) }
        verify(exactly = 1) { pm.getApplicationInfo(pkg, 0) }
        verify(exactly = 1) { pm.getApplicationLabel(info) }
    }

    @Test fun `inaccessible app labels fail gracefully without inventing a name`() {
        every { pm.getApplicationInfo("hidden.app", 0) } throws SecurityException("Not visible")
        assertNull(labels.label("hidden.app"))
    }
}
