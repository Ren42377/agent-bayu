package dev.agentbayu.app.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextWindowSliderTest {

    @Test
    fun `the slider exposes four fixed stops`() {
        assertEquals(
            listOf(131_072, 262_144, 524_288, 1_048_576),
            CONTEXT_WINDOW_STOPS
        )
    }

    @Test
    fun `an override maps back to its stop`() {
        assertEquals(0, contextWindowStopOf(131_072))
        assertEquals(1, contextWindowStopOf(262_144))
        assertEquals(2, contextWindowStopOf(524_288))
        assertEquals(3, contextWindowStopOf(1_048_576))
        assertEquals(-1, contextWindowStopOf(null))
        assertEquals(-1, contextWindowStopOf(32_768))
    }

    @Test
    fun `stops render as short labels`() {
        assertEquals("128K", contextWindowLabel(131_072))
        assertEquals("256K", contextWindowLabel(262_144))
        assertEquals("512K", contextWindowLabel(524_288))
        assertEquals("1M", contextWindowLabel(1_048_576))
        assertEquals("32K", contextWindowLabel(32_768))
    }

    @Test
    fun `non stop sizes compact too`() {
        assertEquals("200K", contextWindowLabel(200_000))
        assertEquals("250K", contextWindowLabel(256_000))
        assertEquals("1M", contextWindowLabel(1_000_000))
        assertEquals("1.5M", contextWindowLabel(1_500_000))
        assertEquals("98K", contextWindowLabel(100_352))
    }
}
