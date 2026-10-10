package com.resurgeai.core.healthconnect

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import com.resurgeai.core.model.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceResolverTest {

    private val auto = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED

    @Test
    fun `device type decides kind`() {
        assertEquals(SourceKind.WEARABLE, SourceResolver.classify(Device.TYPE_WATCH, auto, "com.any"))
        assertEquals(SourceKind.WEARABLE, SourceResolver.classify(Device.TYPE_RING, auto, "com.any"))
        assertEquals(SourceKind.WEARABLE, SourceResolver.classify(Device.TYPE_FITNESS_BAND, auto, "com.any"))
        assertEquals(SourceKind.PHONE, SourceResolver.classify(Device.TYPE_PHONE, auto, "com.any"))
        assertEquals(SourceKind.OTHER_DEVICE, SourceResolver.classify(Device.TYPE_SCALE, auto, "com.any"))
    }

    @Test
    fun `manual entry wins over device type`() {
        assertEquals(
            SourceKind.MANUAL,
            SourceResolver.classify(Device.TYPE_WATCH, Metadata.RECORDING_METHOD_MANUAL_ENTRY, "com.any"),
        )
    }

    @Test
    fun `falls back to known packages when device is missing`() {
        assertEquals(SourceKind.WEARABLE, SourceResolver.classify(null, auto, "com.fitbit.FitbitMobile"))
        assertEquals(SourceKind.WEARABLE, SourceResolver.classify(Device.TYPE_UNKNOWN, auto, "com.ouraring.oura"))
        assertEquals(SourceKind.PHONE, SourceResolver.classify(null, auto, "android"))
        assertEquals(SourceKind.UNKNOWN, SourceResolver.classify(null, auto, "com.sec.android.app.shealth"))
    }

    @Test
    fun `device name avoids repeating the manufacturer`() {
        assertEquals("Samsung Galaxy Watch6", SourceResolver.deviceName("Samsung", "Galaxy Watch6"))
        assertEquals("Samsung Galaxy Watch6", SourceResolver.deviceName("Samsung", "Samsung Galaxy Watch6"))
        assertEquals("Google Pixel Watch 3", SourceResolver.deviceName("Google", "Pixel Watch 3"))
        assertEquals("Pixel 9", SourceResolver.deviceName(null, "Pixel 9"))
        assertNull(SourceResolver.deviceName(" ", null))
    }

    @Test
    fun `resolve groups by source and sorts by share`() {
        val breakdown = SourceResolver.resolve(
            listOf(
                Contribution(SourceKind.PHONE, "android", "Pixel 9", 2_000.0),
                Contribution(SourceKind.WEARABLE, "com.fitbit.FitbitMobile", "Pixel Watch 3", 5_000.0),
                Contribution(SourceKind.WEARABLE, "com.fitbit.FitbitMobile", "Pixel Watch 3", 1_000.0),
                Contribution(SourceKind.PHONE, "android", "Pixel 9", 0.0),
            ),
            appName = { "App($it)" },
        )

        assertEquals(2, breakdown.shares.size)
        val primary = breakdown.primary!!
        assertEquals(SourceKind.WEARABLE, primary.kind)
        assertEquals("Pixel Watch 3", primary.deviceName)
        assertEquals("App(com.fitbit.FitbitMobile)", primary.appName)
        assertEquals(0.75f, breakdown.shares[0].fraction, 0.001f)
        assertEquals(0.25f, breakdown.shares[1].fraction, 0.001f)
    }

    @Test
    fun `resolve with no positive weight is empty`() {
        assertTrue(SourceResolver.resolve(emptyList()).shares.isEmpty())
        assertTrue(
            SourceResolver.resolve(listOf(Contribution(SourceKind.PHONE, "android", null, 0.0))).shares.isEmpty(),
        )
    }
}
