package dev.agentbayu.app.ai

class ModelLockout(
    private val clock: Clock,
    private val lockMillis: Long = DEFAULT_LOCK_MILLIS
) {

    private val entries = HashMap<String, Long>()

    fun lock(providerId: String, modelId: String, durationMillis: Long = lockMillis) {
        synchronized(entries) {
            entries[key(providerId, modelId)] = clock.nowMillis() + durationMillis
        }
    }

    fun remainingMillis(providerId: String, modelId: String): Long = synchronized(entries) {
        val until = entries[key(providerId, modelId)] ?: return 0L
        (until - clock.nowMillis()).coerceAtLeast(0L)
    }

    fun isLocked(providerId: String, modelId: String): Boolean =
        remainingMillis(providerId, modelId) > 0L

    fun clear(providerId: String, modelId: String) {
        synchronized(entries) {
            entries.remove(key(providerId, modelId))
        }
    }

    private fun key(providerId: String, modelId: String): String = providerId + "|" + modelId

    companion object {
        const val DEFAULT_LOCK_MILLIS = 30 * 60_000L
    }
}
