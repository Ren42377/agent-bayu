package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircuitBreakerTest {

    private val clock = FakeClock()

    private fun breaker(
        remoteThreshold: Int = 3,
        localThreshold: Int = 2,
        baseOpenMillis: Long = CircuitBreaker.DEFAULT_OPEN_MILLIS,
        maxOpenMillis: Long = CircuitBreaker.DEFAULT_MAX_OPEN_MILLIS
    ): CircuitBreaker = CircuitBreaker(
        clock = clock,
        remoteThreshold = remoteThreshold,
        localThreshold = localThreshold,
        baseOpenMillis = baseOpenMillis,
        maxOpenMillis = maxOpenMillis
    )

    @Test
    fun unknownProviderIsClosed() {
        val breaker = breaker()
        assertEquals(BreakerState.CLOSED, breaker.state("groq"))
        assertTrue(breaker.allows("groq"))
        assertEquals(0L, breaker.openRemainingMillis("groq"))
        assertEquals(0, breaker.snapshot("groq").consecutiveFailures)
    }

    @Test
    fun staysClosedBelowTheThreshold() {
        val breaker = breaker(remoteThreshold = 3)
        breaker.recordFailure("groq")
        breaker.recordFailure("groq")
        assertEquals(BreakerState.CLOSED, breaker.state("groq"))
        assertEquals(2, breaker.snapshot("groq").consecutiveFailures)
    }

    @Test
    fun opensOnTheThresholdFailure() {
        val breaker = breaker(remoteThreshold = 3)
        repeat(3) { breaker.recordFailure("groq") }
        assertEquals(BreakerState.OPEN, breaker.state("groq"))
        assertFalse(breaker.allows("groq"))
        assertEquals(CircuitBreaker.DEFAULT_OPEN_MILLIS, breaker.openRemainingMillis("groq"))
    }

    @Test
    fun localEndpointsTripEarlier() {
        val breaker = breaker(remoteThreshold = 15, localThreshold = 2)
        breaker.recordFailure("local", local = true)
        assertEquals(BreakerState.CLOSED, breaker.state("local"))
        breaker.recordFailure("local", local = true)
        assertEquals(BreakerState.OPEN, breaker.state("local"))
    }

    @Test
    fun defaultRemoteThresholdIsFifteen() {
        val breaker = CircuitBreaker(clock)
        repeat(CircuitBreaker.DEFAULT_REMOTE_THRESHOLD - 1) { breaker.recordFailure("groq") }
        assertEquals(BreakerState.CLOSED, breaker.state("groq"))
        breaker.recordFailure("groq")
        assertEquals(BreakerState.OPEN, breaker.state("groq"))
    }

    @Test
    fun openWindowCountsDown() {
        val breaker = breaker(remoteThreshold = 1, baseOpenMillis = 60_000L)
        breaker.recordFailure("groq")
        clock.advance(20_000L)
        assertEquals(40_000L, breaker.openRemainingMillis("groq"))
        assertEquals(40_000L, breaker.snapshot("groq").openRemainingMillis)
    }

    @Test
    fun windowExpiryPromotesToHalfOpen() {
        val breaker = breaker(remoteThreshold = 1, baseOpenMillis = 60_000L)
        breaker.recordFailure("groq")
        clock.advance(60_000L)
        assertEquals(BreakerState.HALF_OPEN, breaker.state("groq"))
        assertTrue(breaker.allows("groq"))
        assertEquals(0L, breaker.openRemainingMillis("groq"))
    }

    @Test
    fun successfulProbeClosesTheBreaker() {
        val breaker = breaker(remoteThreshold = 1, baseOpenMillis = 60_000L)
        breaker.recordFailure("groq")
        clock.advance(60_000L)
        assertEquals(BreakerState.HALF_OPEN, breaker.state("groq"))
        breaker.recordSuccess("groq")
        val snapshot = breaker.snapshot("groq")
        assertEquals(BreakerState.CLOSED, snapshot.state)
        assertEquals(0, snapshot.consecutiveFailures)
    }

    @Test
    fun failedProbeReopensWithTwiceTheWindow() {
        val breaker = breaker(remoteThreshold = 1, baseOpenMillis = 60_000L)
        breaker.recordFailure("groq")
        clock.advance(60_000L)
        breaker.recordFailure("groq")
        assertEquals(BreakerState.OPEN, breaker.state("groq"))
        assertEquals(120_000L, breaker.openRemainingMillis("groq"))
    }

    @Test
    fun openWindowNeverPassesTheCeiling() {
        val breaker = breaker(
            remoteThreshold = 1,
            baseOpenMillis = 600_000L,
            maxOpenMillis = 900_000L
        )
        breaker.recordFailure("groq")
        clock.advance(600_000L)
        breaker.recordFailure("groq")
        assertEquals(900_000L, breaker.openRemainingMillis("groq"))
    }

    @Test
    fun successBeforeTheThresholdClearsTheCount() {
        val breaker = breaker(remoteThreshold = 3)
        breaker.recordFailure("groq")
        breaker.recordFailure("groq")
        breaker.recordSuccess("groq")
        breaker.recordFailure("groq")
        assertEquals(BreakerState.CLOSED, breaker.state("groq"))
        assertEquals(1, breaker.snapshot("groq").consecutiveFailures)
    }

    @Test
    fun providersAreTrackedSeparately() {
        val breaker = breaker(remoteThreshold = 1)
        breaker.recordFailure("groq")
        assertFalse(breaker.allows("groq"))
        assertTrue(breaker.allows("cerebras"))
    }

    @Test
    fun resetForgetsTheProvider() {
        val breaker = breaker(remoteThreshold = 1)
        breaker.recordFailure("groq")
        breaker.reset("groq")
        assertEquals(BreakerState.CLOSED, breaker.state("groq"))
        assertEquals(0, breaker.snapshot("groq").consecutiveFailures)
    }
}
