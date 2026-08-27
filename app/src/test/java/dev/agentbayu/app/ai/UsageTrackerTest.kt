package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageTrackerTest {

    private val clock = FakeClock()
    private val tracker = UsageTracker(clock)

    @Test
    fun unknownConnectionReportsZeroes() {
        val stats = tracker.statsFor("conn-1")
        assertEquals(0, stats.requests)
        assertEquals(0, stats.inFlight)
        assertEquals(0L, stats.totalTokens)
        assertTrue(tracker.stats.value.isEmpty())
    }

    @Test
    fun beginRequestCountsTheAttempt() {
        clock.set(5_000L)
        tracker.beginRequest("conn-1")
        val stats = tracker.statsFor("conn-1")
        assertEquals(1, stats.requests)
        assertEquals(1, stats.inFlight)
        assertEquals(5_000L, stats.lastUsedAtMillis)
    }

    @Test
    fun successAddsTokensAndCost() {
        tracker.beginRequest("conn-1")
        clock.set(9_000L)
        tracker.recordSuccess("conn-1", TokenUsage(inputTokens = 120, outputTokens = 80, estimatedCostUsd = 0.25))
        tracker.beginRequest("conn-1")
        tracker.recordSuccess("conn-1", TokenUsage(inputTokens = 10, outputTokens = 5, estimatedCostUsd = 0.05))
        val stats = tracker.statsFor("conn-1")
        assertEquals(2, stats.successes)
        assertEquals(0, stats.inFlight)
        assertEquals(130L, stats.inputTokens)
        assertEquals(85L, stats.outputTokens)
        assertEquals(215L, stats.totalTokens)
        assertEquals(0.30, stats.costUsd, 0.0001)
        assertEquals(9_000L, stats.lastSuccessAtMillis)
    }

    @Test
    fun missingCostCountsAsZero() {
        tracker.beginRequest("conn-1")
        tracker.recordSuccess("conn-1", TokenUsage(inputTokens = 5, outputTokens = 5))
        assertEquals(0.0, tracker.statsFor("conn-1").costUsd, 0.0001)
    }

    @Test
    fun failureKeepsOnlyTheStatusLabel() {
        tracker.beginRequest("conn-1")
        clock.set(7_000L)
        tracker.recordFailure("conn-1", FailureClassifier.classifyHttp(429, "secret body"))
        val stats = tracker.statsFor("conn-1")
        assertEquals(1, stats.failures)
        assertEquals(0, stats.inFlight)
        assertEquals(7_000L, stats.lastFailureAtMillis)
        assertEquals("status=429 kind=COOLDOWN", stats.lastFailure)
    }

    @Test
    fun inFlightNeverGoesNegative() {
        tracker.recordFailure("conn-1", FailureClassifier.classifyError(RuntimeException()))
        assertEquals(0, tracker.statsFor("conn-1").inFlight)
    }

    @Test
    fun firstSampleSeedsTheEwma() {
        tracker.recordFirstToken("conn-1", 1_000L)
        assertEquals(1_000.0, tracker.statsFor("conn-1").firstTokenEwmaMillis, 0.0001)
    }

    @Test
    fun laterSamplesBlendIntoTheEwma() {
        tracker.recordFirstToken("conn-1", 1_000L)
        tracker.recordFirstToken("conn-1", 2_000L)
        assertEquals(1_300.0, tracker.statsFor("conn-1").firstTokenEwmaMillis, 0.0001)
    }

    @Test
    fun p95TakesTheSlowestOfASmallWindow() {
        listOf(300L, 100L, 500L, 200L, 400L).forEach { tracker.recordFirstToken("conn-1", it) }
        assertEquals(500L, tracker.statsFor("conn-1").p95FirstTokenMillis)
    }

    @Test
    fun p95IgnoresSamplesBeyondTheWindow() {
        (1L..60L).forEach { tracker.recordFirstToken("conn-1", it) }
        assertEquals(58L, tracker.statsFor("conn-1").p95FirstTokenMillis)
    }

    @Test
    fun connectionsAreTrackedSeparately() {
        tracker.beginRequest("conn-1")
        tracker.beginRequest("conn-2")
        tracker.beginRequest("conn-2")
        assertEquals(1, tracker.statsFor("conn-1").requests)
        assertEquals(2, tracker.statsFor("conn-2").requests)
        assertEquals(setOf("conn-1", "conn-2"), tracker.stats.value.keys)
    }

    @Test
    fun forgetDropsCountersAndSamples() {
        tracker.recordFirstToken("conn-1", 4_000L)
        tracker.beginRequest("conn-1")
        tracker.beginRequest("conn-2")
        tracker.forget("conn-1")
        assertEquals(setOf("conn-2"), tracker.stats.value.keys)
        tracker.recordFirstToken("conn-1", 100L)
        val stats = tracker.statsFor("conn-1")
        assertEquals(100.0, stats.firstTokenEwmaMillis, 0.0001)
        assertEquals(100L, stats.p95FirstTokenMillis)
    }

    @Test
    fun resetClearsEverything() {
        tracker.recordFirstToken("conn-1", 900L)
        tracker.beginRequest("conn-2")
        tracker.reset()
        assertTrue(tracker.stats.value.isEmpty())
        tracker.recordFirstToken("conn-1", 200L)
        assertEquals(200.0, tracker.statsFor("conn-1").firstTokenEwmaMillis, 0.0001)
    }
}
