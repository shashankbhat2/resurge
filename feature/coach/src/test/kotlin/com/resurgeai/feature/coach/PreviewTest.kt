package com.resurgeai.feature.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTest {
    @Test
    fun `previews drop markdown, line breaks and trailing ellipses`() {
        assertEquals("Yes, with room to spare. That plan gives about 12,000 steps.", plainPreview("**Yes, with room to spare.** That plan\ngives about 12,000 steps."))
        assertEquals("Stand down. This is almost certainly fine.", plainPreview("Stand down. This is almost certainly fine.\n\n..."))
        assertEquals("Sleep first", plainPreview("## Sleep first…"))
    }
}
