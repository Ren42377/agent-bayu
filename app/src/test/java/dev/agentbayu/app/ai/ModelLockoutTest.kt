package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLockoutTest {

    private val clock = FakeClock()

    @Test
    fun unknownPairIsOpen() {
        val lockout = ModelLockout(clock)
        assertFalse(lockout.isLocked("groq", "model-a"))
        assertEquals(0L, lockout.remainingMillis("groq", "model-a"))
    }

    @Test
    fun lockUsesTheDefaultWindow() {
        val lockout = ModelLockout(clock)
        lockout.lock("groq", "model-a")
        assertTrue(lockout.isLocked("groq", "model-a"))
        assertEquals(ModelLockout.DEFAULT_LOCK_MILLIS, lockout.remainingMillis("groq", "model-a"))
    }

    @Test
    fun lockAcceptsACustomWindow() {
        val lockout = ModelLockout(clock)
        lockout.lock("groq", "model-a", durationMillis = 5_000L)
        assertEquals(5_000L, lockout.remainingMillis("groq", "model-a"))
    }

    @Test
    fun lockExpiresOnItsOwn() {
        val lockout = ModelLockout(clock, lockMillis = 30_000L)
        lockout.lock("groq", "model-a")
        clock.advance(29_999L)
        assertTrue(lockout.isLocked("groq", "model-a"))
        clock.advance(1L)
        assertFalse(lockout.isLocked("groq", "model-a"))
        assertEquals(0L, lockout.remainingMillis("groq", "model-a"))
    }

    @Test
    fun otherModelsOnTheSameProviderKeepWorking() {
        val lockout = ModelLockout(clock)
        lockout.lock("groq", "model-a")
        assertFalse(lockout.isLocked("groq", "model-b"))
    }

    @Test
    fun theSameModelOnAnotherProviderKeepsWorking() {
        val lockout = ModelLockout(clock)
        lockout.lock("groq", "model-a")
        assertFalse(lockout.isLocked("cerebras", "model-a"))
    }

    @Test
    fun relockRestartsTheWindow() {
        val lockout = ModelLockout(clock, lockMillis = 30_000L)
        lockout.lock("groq", "model-a")
        clock.advance(20_000L)
        assertEquals(10_000L, lockout.remainingMillis("groq", "model-a"))
        lockout.lock("groq", "model-a")
        assertEquals(30_000L, lockout.remainingMillis("groq", "model-a"))
    }

    @Test
    fun clearUnlocksImmediately() {
        val lockout = ModelLockout(clock)
        lockout.lock("groq", "model-a")
        lockout.clear("groq", "model-a")
        assertFalse(lockout.isLocked("groq", "model-a"))
    }
}
