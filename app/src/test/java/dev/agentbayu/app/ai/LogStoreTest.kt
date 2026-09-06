package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogStoreTest {

    @Test
    fun aCrashRetainsItsTypeAndLimitsTheStackTrace() {
        val store = LogStore(FixedClock())
        val error = IllegalStateException("broken")
        error.stackTrace = Array(LogStore.MAX_CRASH_DETAIL_CHARS) {
            StackTraceElement("Class", "method", "File.kt", it)
        }

        store.recordCrash(error)

        val entry = store.entries.value.single()
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("Crash", entry.source)
        assertEquals("IllegalStateException", entry.message)
        assertTrue(entry.detail.orEmpty().length <= LogStore.MAX_CRASH_DETAIL_CHARS)
    }

    private class FixedClock : Clock {
        override fun nowMillis(): Long = 42L
    }
}
