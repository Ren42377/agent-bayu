package dev.agentbayu.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsageStats(
    val requests: Int = 0,
    val successes: Int = 0,
    val failures: Int = 0,
    val inFlight: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val costUsd: Double = 0.0,
    val costEstimated: Boolean = true,
    val firstTokenEwmaMillis: Double = 0.0,
    val p95FirstTokenMillis: Long = 0L,
    val lastUsedAtMillis: Long = 0L,
    val lastSuccessAtMillis: Long = 0L,
    val lastFailureAtMillis: Long = 0L,
    val lastFailure: String? = null
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

class UsageTracker(private val clock: Clock) {

    private val state = MutableStateFlow<Map<String, UsageStats>>(emptyMap())
    private val samples = HashMap<String, ArrayDeque<Long>>()

    val stats: StateFlow<Map<String, UsageStats>> = state.asStateFlow()

    fun statsFor(connectionId: String): UsageStats = state.value[connectionId] ?: UsageStats()

    fun beginRequest(connectionId: String, counted: Boolean = true) = update(connectionId) {
        it.copy(
            requests = if (counted) it.requests + 1 else it.requests,
            inFlight = it.inFlight + 1,
            lastUsedAtMillis = clock.nowMillis()
        )
    }

    fun recordFirstToken(connectionId: String, millis: Long) {
        val deque = synchronized(samples) {
            val target = samples.getOrPut(connectionId) { ArrayDeque() }
            target.addLast(millis)
            while (target.size > MAX_SAMPLES) target.removeFirst()
            target.toList()
        }
        val percentile = percentile95(deque)
        update(connectionId) {
            val previous = it.firstTokenEwmaMillis
            val ewma = if (previous <= 0.0) {
                millis.toDouble()
            } else {
                EWMA_ALPHA * millis.toDouble() + (1.0 - EWMA_ALPHA) * previous
            }
            it.copy(firstTokenEwmaMillis = ewma, p95FirstTokenMillis = percentile)
        }
    }

    fun recordSuccess(connectionId: String, usage: TokenUsage) = update(connectionId) {
        it.copy(
            successes = it.successes + 1,
            inFlight = (it.inFlight - 1).coerceAtLeast(0),
            inputTokens = it.inputTokens + usage.inputTokens,
            outputTokens = it.outputTokens + usage.outputTokens,
            costUsd = it.costUsd + (usage.estimatedCostUsd ?: 0.0),
            costEstimated = it.costEstimated || usage.estimated,
            lastSuccessAtMillis = clock.nowMillis()
        )
    }

    fun recordFailure(connectionId: String, failure: RouteFailure) = update(connectionId) {
        it.copy(
            failures = it.failures + 1,
            inFlight = (it.inFlight - 1).coerceAtLeast(0),
            lastFailureAtMillis = clock.nowMillis(),
            lastFailure = failure.logLabel
        )
    }

    fun forget(connectionId: String) {
        synchronized(samples) { samples.remove(connectionId) }
        state.value = state.value - connectionId
    }

    fun reset() {
        synchronized(samples) { samples.clear() }
        state.value = emptyMap()
    }

    private fun update(connectionId: String, block: (UsageStats) -> UsageStats) {
        val current = state.value
        val existing = current[connectionId] ?: UsageStats()
        state.value = current + (connectionId to block(existing))
    }

    private fun percentile95(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = Math.ceil(sorted.size * 0.95).toInt() - 1
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }

    companion object {
        const val MAX_SAMPLES = 50
        const val EWMA_ALPHA = 0.3
    }
}
