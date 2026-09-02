package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureClassifierTest {

    private fun kind(statusCode: Int, body: String = "", retryAfter: String? = null): FailureKind =
        FailureClassifier.classifyHttp(statusCode, body, retryAfter).kind

    @Test
    fun badKeyIsTerminal() {
        val failure = FailureClassifier.classifyHttp(401, "invalid api key")
        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertFalse(failure.tripsBreaker)
        assertEquals(401, failure.statusCode)
    }

    @Test
    fun paymentAndForbiddenAreTerminal() {
        assertEquals(FailureKind.TERMINAL, kind(402))
        assertEquals(FailureKind.TERMINAL, kind(403))
    }

    @Test
    fun rateLimitBecomesCooldown() {
        val failure = FailureClassifier.classifyHttp(429, "rate limit reached")
        assertEquals(FailureKind.COOLDOWN, failure.kind)
        assertNull(failure.retryAfterMillis)
        assertFalse(failure.tripsBreaker)
    }

    @Test
    fun rateLimitKeepsTheRetryAfterHint() {
        val failure = FailureClassifier.classifyHttp(429, "slow down", "30")
        assertEquals(FailureKind.COOLDOWN, failure.kind)
        assertEquals(30_000L, failure.retryAfterMillis)
    }

    @Test
    fun exhaustedQuotaIsTerminalNotCooldown() {
        assertEquals(FailureKind.TERMINAL, kind(429, "{\"code\":\"insufficient_quota\"}"))
        assertEquals(FailureKind.TERMINAL, kind(429, "Your credit balance is too low"))
    }

    @Test
    fun antigravityQuotaWordingsAreTerminal() {
        assertEquals(FailureKind.TERMINAL, kind(429, "Individual quota exhausted, resets after 12m"))
        assertEquals(FailureKind.TERMINAL, kind(429, "You have exhausted your capacity"))
        assertEquals(FailureKind.TERMINAL, kind(429, "Enable overages to keep going"))
        assertEquals(FailureKind.TERMINAL, kind(429, "Free tier daily limit reached"))
    }

    @Test
    fun aZeroResetHintIsATransientBurst() {
        val failure = FailureClassifier.classifyHttp(429, "Quota exhausted, reset after 0s")
        assertEquals(FailureKind.COOLDOWN, failure.kind)
        assertEquals(2_000L, failure.retryAfterMillis)
    }

    @Test
    fun aRateLimitBodyCanCarryTheWait() {
        val failure = FailureClassifier.classifyHttp(429, "Too many requests, resets in 45s")
        assertEquals(FailureKind.COOLDOWN, failure.kind)
        assertEquals(45_000L, failure.retryAfterMillis)
    }

    @Test
    fun resetHintsParseHoursMinutesAndSeconds() {
        assertEquals(3_723_000L, FailureClassifier.parseResetHint("resets after 1h2m3s"))
        assertEquals(600_000L, FailureClassifier.parseResetHint("reset in 10 m"))
        assertEquals(0L, FailureClassifier.parseResetHint("reset after 0s"))
        assertNull(FailureClassifier.parseResetHint("rate limit reached"))
        assertNull(FailureClassifier.parseResetHint("reset after a while"))
    }

    @Test
    fun timeoutRetriesAndTripsTheBreaker() {
        val failure = FailureClassifier.classifyHttp(408, "timeout")
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertTrue(failure.tripsBreaker)
    }

    @Test
    fun serverErrorsRetryAndTripTheBreaker() {
        listOf(500, 502, 503, 599).forEach { status ->
            val failure = FailureClassifier.classifyHttp(status, "oops")
            assertEquals(status.toString(), FailureKind.RETRYABLE, failure.kind)
            assertTrue(status.toString(), failure.tripsBreaker)
        }
    }

    @Test
    fun missingModelLocksTheModel() {
        assertEquals(FailureKind.MODEL_LOCK, kind(404, "The model `llama-x` does not exist"))
        assertEquals(FailureKind.MODEL_LOCK, kind(404, "Model has been decommissioned"))
    }

    @Test
    fun missingEndpointIsTerminal() {
        assertEquals(FailureKind.TERMINAL, kind(404, "not found"))
    }

    @Test
    fun modelRejectionOnBadRequestLocksTheModel() {
        assertEquals(FailureKind.MODEL_LOCK, kind(400, "unsupported model parameter"))
    }

    @Test
    fun plainBadRequestIsTerminal() {
        assertEquals(FailureKind.TERMINAL, kind(400, "messages must not be empty"))
    }

    @Test
    fun oversizedPayloadLocksTheModel() {
        assertEquals(FailureKind.MODEL_LOCK, kind(413, "payload too large"))
    }

    @Test
    fun unexpectedStatusRetriesWithoutTrippingTheBreaker() {
        val failure = FailureClassifier.classifyHttp(418, "teapot")
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertFalse(failure.tripsBreaker)
    }

    @Test
    fun transportErrorsRetryAndTripTheBreaker() {
        val failure = FailureClassifier.classifyError(java.net.SocketTimeoutException("read"))
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertTrue(failure.tripsBreaker)
        assertEquals("SocketTimeoutException", failure.message)
        assertNull(failure.statusCode)
    }

    @Test
    fun retryAfterParsesSecondsAndFractions() {
        assertEquals(5_000L, FailureClassifier.parseRetryAfter("5"))
        assertEquals(5_000L, FailureClassifier.parseRetryAfter(" 5 "))
        assertEquals(1_500L, FailureClassifier.parseRetryAfter("1.5"))
        assertEquals(0L, FailureClassifier.parseRetryAfter("-3"))
    }

    @Test
    fun retryAfterIgnoresGarbage() {
        assertNull(FailureClassifier.parseRetryAfter(null))
        assertNull(FailureClassifier.parseRetryAfter(""))
        assertNull(FailureClassifier.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun logLabelStaysFreeOfPayload() {
        val failure = FailureClassifier.classifyHttp(429, "secret body", "10")
        assertEquals("status=429 kind=COOLDOWN", failure.logLabel)
        assertEquals("status=0 kind=RETRYABLE", FailureClassifier.classifyError(RuntimeException()).logLabel)
    }
}
