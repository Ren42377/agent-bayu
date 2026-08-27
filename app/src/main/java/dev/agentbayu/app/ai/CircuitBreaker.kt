package dev.agentbayu.app.ai

enum class BreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

data class BreakerSnapshot(
    val state: BreakerState,
    val consecutiveFailures: Int,
    val openRemainingMillis: Long
)

class CircuitBreaker(
    private val clock: Clock,
    private val remoteThreshold: Int = DEFAULT_REMOTE_THRESHOLD,
    private val localThreshold: Int = DEFAULT_LOCAL_THRESHOLD,
    private val baseOpenMillis: Long = DEFAULT_OPEN_MILLIS,
    private val maxOpenMillis: Long = DEFAULT_MAX_OPEN_MILLIS
) {

    private class Entry {
        var state: BreakerState = BreakerState.CLOSED
        var consecutiveFailures: Int = 0
        var openedAtMillis: Long = 0L
        var openWindowMillis: Long = 0L
    }

    private val entries = HashMap<String, Entry>()

    fun state(providerId: String): BreakerState = synchronized(entries) {
        val entry = entries[providerId] ?: return BreakerState.CLOSED
        promote(entry)
        entry.state
    }

    fun allows(providerId: String): Boolean = state(providerId) != BreakerState.OPEN

    fun openRemainingMillis(providerId: String): Long = synchronized(entries) {
        val entry = entries[providerId] ?: return 0L
        promote(entry)
        if (entry.state != BreakerState.OPEN) return 0L
        remaining(entry)
    }

    fun snapshot(providerId: String): BreakerSnapshot = synchronized(entries) {
        val entry = entries[providerId] ?: return BreakerSnapshot(BreakerState.CLOSED, 0, 0L)
        promote(entry)
        BreakerSnapshot(
            state = entry.state,
            consecutiveFailures = entry.consecutiveFailures,
            openRemainingMillis = if (entry.state == BreakerState.OPEN) remaining(entry) else 0L
        )
    }

    fun recordSuccess(providerId: String) = synchronized(entries) {
        val entry = entries[providerId] ?: return
        entry.state = BreakerState.CLOSED
        entry.consecutiveFailures = 0
        entry.openedAtMillis = 0L
        entry.openWindowMillis = 0L
    }

    fun recordFailure(providerId: String, local: Boolean = false) = synchronized(entries) {
        val entry = entries.getOrPut(providerId) { Entry() }
        promote(entry)
        entry.consecutiveFailures += 1
        if (entry.state == BreakerState.HALF_OPEN) {
            val next = (entry.openWindowMillis * 2).coerceIn(baseOpenMillis, maxOpenMillis)
            open(entry, next)
            return
        }
        val threshold = if (local) localThreshold else remoteThreshold
        if (entry.consecutiveFailures >= threshold) {
            open(entry, baseOpenMillis)
        }
    }

    fun reset(providerId: String) = synchronized(entries) {
        entries.remove(providerId)
        Unit
    }

    private fun open(entry: Entry, windowMillis: Long) {
        entry.state = BreakerState.OPEN
        entry.openedAtMillis = clock.nowMillis()
        entry.openWindowMillis = windowMillis
    }

    private fun promote(entry: Entry) {
        if (entry.state != BreakerState.OPEN) return
        if (remaining(entry) <= 0L) {
            entry.state = BreakerState.HALF_OPEN
        }
    }

    private fun remaining(entry: Entry): Long {
        val elapsed = clock.nowMillis() - entry.openedAtMillis
        return (entry.openWindowMillis - elapsed).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_REMOTE_THRESHOLD = 15
        const val DEFAULT_LOCAL_THRESHOLD = 2
        const val DEFAULT_OPEN_MILLIS = 60_000L
        const val DEFAULT_MAX_OPEN_MILLIS = 15 * 60_000L
    }
}
