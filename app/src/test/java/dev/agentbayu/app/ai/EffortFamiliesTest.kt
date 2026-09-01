package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffortFamiliesTest {

    @Test
    fun `suffix split separates the base from the level`() {
        assertEquals(
            "gemini-3.7-flash" to ReasoningEffort.HIGH,
            splitEffortSuffix("gemini-3.7-flash-high")
        )
        assertEquals(
            "gemini-3.7-flash" to ReasoningEffort.XHIGH,
            splitEffortSuffix("gemini-3.7-flash-xhigh")
        )
        assertNull(splitEffortSuffix("gemini-3.7-flash-tiered"))
        assertNull(splitEffortSuffix("-low"))
        assertEquals("gemini-3.7-flash-tiered", effortBaseOf("gemini-3.7-flash-tiered"))
        assertEquals("gemini-3.7-flash", effortBaseOf(" gemini-3.7-flash-low "))
    }

    @Test
    fun `a family needs at least two siblings`() {
        val families = effortFamilies(
            listOf(
                "gemini-3.7-flash-low",
                "gemini-3.7-flash-medium",
                "gemini-3.7-flash-high",
                "gemini-3.1-pro-low",
                "gemini-3.7-flash-tiered"
            )
        )

        assertEquals(setOf("gemini-3.7-flash"), families.keys)
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            families.getValue("gemini-3.7-flash")
        )
    }

    @Test
    fun `an id ending in max without siblings has no family`() {
        assertTrue(effortFamilies(listOf("grok-max")).isEmpty())
        assertEquals(emptyList<ReasoningEffort>(), effortsFor("grok-max", listOf("grok-max")))
    }

    @Test
    fun `levels follow the family of the selected model`() {
        val ids = listOf("sol-low", "sol-high", "luna-medium", "sol-low")

        assertEquals(listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH), effortsFor("sol-high", ids))
        assertEquals(listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH), effortsFor("sol", ids))
        assertEquals(emptyList<ReasoningEffort>(), effortsFor("luna-medium", ids))
    }

    @Test
    fun `suffix providers read the family from catalog and discovered ids`() {
        val provider = testProvider(
            id = "agy",
            effortMode = EffortMode.MODEL_SUFFIX,
            models = listOf(ModelEntry(id = "flash-low"), ModelEntry(id = "flash-medium"))
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM),
            availableEfforts(provider, "flash-low")
        )
        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.XHIGH),
            availableEfforts(provider, "flash-low", listOf("flash-xhigh"))
        )
    }

    @Test
    fun `request field providers read the family from the model entry`() {
        val provider = testProvider(
            id = "codex",
            effortMode = EffortMode.REQUEST_FIELD,
            models = listOf(
                ModelEntry(
                    id = "gpt-5.6-sol",
                    efforts = listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH)
                ),
                ModelEntry(id = "gpt-5.6-plain")
            )
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH),
            availableEfforts(provider, "gpt-5.6-sol")
        )
        assertEquals(emptyList<ReasoningEffort>(), availableEfforts(provider, "gpt-5.6-plain"))
        assertEquals(emptyList<ReasoningEffort>(), availableEfforts(provider, "unknown"))
    }

    @Test
    fun `providers without an effort mode expose no levels`() {
        val provider = testProvider(models = listOf(ModelEntry(id = "flash-low"), ModelEntry(id = "flash-high")))

        assertEquals(emptyList<ReasoningEffort>(), availableEfforts(provider, "flash-low"))
    }

    @Test
    fun `nearest level clamps into the supported set`() {
        val supported = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)

        assertEquals(ReasoningEffort.HIGH, nearestEffort(ReasoningEffort.MAX, supported))
        assertEquals(ReasoningEffort.HIGH, nearestEffort(ReasoningEffort.XHIGH, supported))
        assertEquals(ReasoningEffort.LOW, nearestEffort(ReasoningEffort.LOW, supported))
        assertNull(nearestEffort(ReasoningEffort.LOW, emptyList()))
    }

    @Test
    fun `resolve prefers the stored level then the model suffix`() {
        val options = listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH)

        assertEquals(ReasoningEffort.LOW, resolveEffort(options, ReasoningEffort.LOW, "flash-high"))
        assertEquals(ReasoningEffort.HIGH, resolveEffort(options, ReasoningEffort.MAX, "flash-low"))
        assertEquals(ReasoningEffort.HIGH, resolveEffort(options, null, "flash-high"))
        assertEquals(ReasoningEffort.MEDIUM, resolveEffort(options, null, "flash-tiered"))
        assertNull(resolveEffort(emptyList(), ReasoningEffort.HIGH, "flash-high"))
    }

    @Test
    fun `resolve falls back to the middle of the set`() {
        assertEquals(
            ReasoningEffort.HIGH,
            resolveEffort(listOf(ReasoningEffort.LOW, ReasoningEffort.HIGH), null, null)
        )
        assertEquals(
            ReasoningEffort.MEDIUM,
            resolveEffort(listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM), null, null)
        )
    }

    @Test
    fun `wire values map back to levels`() {
        assertEquals(ReasoningEffort.XHIGH, ReasoningEffort.fromWire("XHigh"))
        assertEquals(ReasoningEffort.LOW, ReasoningEffort.fromWire(" low "))
        assertNull(ReasoningEffort.fromWire("turbo"))
        assertNull(ReasoningEffort.fromWire(null))
        assertNull(ReasoningEffort.fromWire(" "))
    }
}
