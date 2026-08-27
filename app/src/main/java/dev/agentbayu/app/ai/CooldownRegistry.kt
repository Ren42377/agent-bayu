package dev.agentbayu.app.ai

data class CooldownSnapshot(
    val remainingMillis: Long,
    val consecutiveFailures: Int
)

class CooldownRegistry(
    private val clock: Clock,
    private val baseMillis: Long = DEFAULT_BASE_MILLIS,
    private val maxMillis: Long = DEFAULT_MAX_MILLIS
) {

    private class Entry {
        var failures: Int = 0
        var untilMillis: Long = 0L
    }

    private val entries = HashMap<String, Entry>()

    fun remainingMillis(connectionId: String): Long = synchronized(entries) {
        val entry = entries[connectionId] ?: return 0L
        (entry.untilMillis - clock.nowMillis()).coerceAtLeast(0L)
    }

    fun isCooling(connectionId: String): Boolean = remainingMillis(connectionId) > 0L

    fun snapshot(connectionId: String): CooldownSnapshot = synchronized(entries) {
        val entry = entries[connectionId] ?: return CooldownSnapshot(0L, 0)
        CooldownSnapshot(
            remainingMillis = (entry.untilMillis - clock.nowMillis()).coerceAtLeast(0L),
            consecutiveFailures = entry.failures
        )
    }

    fun penalize(connectionId: String, retryAfterMillis: Long? = null): Long = synchronized(entries) {
        val entry = entries.getOrPut(connectionId) { Entry() }
        entry.failures += 1
        val backoff = if (retryAfterMillis != null && retryAfterMillis > 0L) {
            retryAfterMillis.coerceAtMost(maxMillis)
        } else {
            exponential(entry.failures)
        }
        entry.untilMillis = clock.nowMillis() + backoff
        backoff
    }

    fun clear(connectionId: String) = synchronized(entries) {
        entries.remove(connectionId)
        Unit
    }

    private fun exponential(failures: Int): Long {
        val shift = (failures - 1).coerceIn(0, 20)
        val scaled = baseMillis.toDouble() * Math.pow(2.0, shift.toDouble())
        if (scaled >= maxMillis.toDouble()) return maxMillis
        return scaled.toLong()
    }

    companion object {
        const val DEFAULT_BASE_MILLIS = 2_000L
        const val DEFAULT_MAX_MILLIS = 15 * 60_000L
    }
}
