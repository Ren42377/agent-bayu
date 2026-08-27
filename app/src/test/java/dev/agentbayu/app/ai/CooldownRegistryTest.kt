package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CooldownRegistryTest {

    private val clock = FakeClock()

    private fun registry(
        baseMillis: Long = CooldownRegistry.DEFAULT_BASE_MILLIS,
        maxMillis: Long = CooldownRegistry.DEFAULT_MAX_MILLIS
    ): CooldownRegistry = CooldownRegistry(clock, baseMillis, maxMillis)

    @Test
    fun unknownConnectionIsNotCooling() {
        val registry = registry()
        assertFalse(registry.isCooling("conn-1"))
        assertEquals(0L, registry.remainingMillis("conn-1"))
        assertEquals(0, registry.snapshot("conn-1").consecutiveFailures)
    }

    @Test
    fun firstPenaltyUsesTheBaseDelay() {
        val registry = registry()
        assertEquals(CooldownRegistry.DEFAULT_BASE_MILLIS, registry.penalize("conn-1"))
        assertTrue(registry.isCooling("conn-1"))
        assertEquals(CooldownRegistry.DEFAULT_BASE_MILLIS, registry.remainingMillis("conn-1"))
    }

    @Test
    fun backoffDoublesWithEveryFailure() {
        val registry = registry(baseMillis = 2_000L)
        assertEquals(2_000L, registry.penalize("conn-1"))
        assertEquals(4_000L, registry.penalize("conn-1"))
        assertEquals(8_000L, registry.penalize("conn-1"))
        assertEquals(16_000L, registry.penalize("conn-1"))
        assertEquals(4, registry.snapshot("conn-1").consecutiveFailures)
    }

    @Test
    fun backoffStopsAtTheCeiling() {
        val registry = registry(baseMillis = 2_000L, maxMillis = 900_000L)
        repeat(20) { registry.penalize("conn-1") }
        assertEquals(900_000L, registry.remainingMillis("conn-1"))
    }

    @Test
    fun retryAfterOverridesTheBackoff() {
        val registry = registry(baseMillis = 2_000L)
        registry.penalize("conn-1")
        registry.penalize("conn-1")
        assertEquals(45_000L, registry.penalize("conn-1", retryAfterMillis = 45_000L))
        assertEquals(45_000L, registry.remainingMillis("conn-1"))
    }

    @Test
    fun retryAfterIsCappedAtTheCeiling() {
        val registry = registry(maxMillis = 900_000L)
        assertEquals(900_000L, registry.penalize("conn-1", retryAfterMillis = 3_600_000L))
    }

    @Test
    fun nonPositiveRetryAfterFallsBackToTheBackoff() {
        val registry = registry(baseMillis = 2_000L)
        assertEquals(2_000L, registry.penalize("conn-1", retryAfterMillis = 0L))
    }

    @Test
    fun remainingShrinksAsTimePasses() {
        val registry = registry(baseMillis = 10_000L)
        registry.penalize("conn-1")
        clock.advance(4_000L)
        assertEquals(6_000L, registry.remainingMillis("conn-1"))
        clock.advance(6_000L)
        assertEquals(0L, registry.remainingMillis("conn-1"))
        assertFalse(registry.isCooling("conn-1"))
    }

    @Test
    fun expiredCooldownKeepsTheFailureCount() {
        val registry = registry(baseMillis = 2_000L)
        registry.penalize("conn-1")
        clock.advance(10_000L)
        val snapshot = registry.snapshot("conn-1")
        assertEquals(0L, snapshot.remainingMillis)
        assertEquals(1, snapshot.consecutiveFailures)
        assertEquals(4_000L, registry.penalize("conn-1"))
    }

    @Test
    fun connectionsCoolDownIndependently() {
        val registry = registry()
        registry.penalize("conn-1")
        assertTrue(registry.isCooling("conn-1"))
        assertFalse(registry.isCooling("conn-2"))
    }

    @Test
    fun clearResetsTheBackoff() {
        val registry = registry(baseMillis = 2_000L)
        registry.penalize("conn-1")
        registry.penalize("conn-1")
        registry.clear("conn-1")
        assertFalse(registry.isCooling("conn-1"))
        assertEquals(2_000L, registry.penalize("conn-1"))
    }
}
