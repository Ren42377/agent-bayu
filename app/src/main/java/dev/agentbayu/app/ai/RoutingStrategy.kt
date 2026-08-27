package dev.agentbayu.app.ai

import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

data class RoutingContext(
    val channel: String,
    val nowMillis: Long,
    val estimatedInputTokens: Int = 0,
    val health: Map<String, CandidateHealth> = emptyMap(),
    val usage: Map<String, UsageStats> = emptyMap()
) {
    fun healthOf(candidate: Candidate): CandidateHealth =
        health[candidate.key] ?: CandidateHealth()

    fun usageOf(candidate: Candidate): UsageStats =
        usage[candidate.connection.id] ?: UsageStats()
}

interface RoutingStrategy {
    val name: String

    fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate>
}

class PriorityStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.PRIORITY

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareBy(
                { it.connection.priority },
                { it.tier.order },
                { it.connection.label },
                { it.model.id }
            )
        )
}

class FillFirstStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.FILL_FIRST

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareByDescending<Candidate> { context.usageOf(it).lastSuccessAtMillis }
                .thenBy { it.connection.priority }
                .thenBy { it.tier.order }
                .thenBy { it.model.id }
        )
}

class RoundRobinStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.ROUND_ROBIN

    private val cursor = AtomicInteger(0)

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> {
        if (candidates.size <= 1) return candidates
        val base = PriorityStrategy().order(candidates, context)
        val offset = Math.floorMod(cursor.getAndIncrement(), base.size)
        return base.subList(offset, base.size) + base.subList(0, offset)
    }
}

class WeightedStrategy(private val random: Random = Random.Default) : RoutingStrategy {
    override val name: String = RoutingStrategies.WEIGHTED

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> {
        if (candidates.size <= 1) return candidates
        val pool = candidates.toMutableList()
        val ordered = ArrayList<Candidate>(candidates.size)
        while (pool.isNotEmpty()) {
            val total = pool.sumOf { weightOf(it) }
            if (total <= 0) {
                ordered += pool
                break
            }
            var ticket = random.nextInt(total)
            var index = pool.lastIndex
            for (position in pool.indices) {
                val weight = weightOf(pool[position])
                if (ticket < weight) {
                    index = position
                    break
                }
                ticket -= weight
            }
            ordered += pool.removeAt(index)
        }
        return ordered
    }

    private fun weightOf(candidate: Candidate): Int = candidate.connection.weight.coerceAtLeast(0)
}

class LeastUsedStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.LEAST_USED

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareBy<Candidate> { context.usageOf(it).inFlight }
                .thenBy { context.usageOf(it).requests }
                .thenBy { it.connection.priority }
                .thenBy { it.model.id }
        )
}

class CostOptimizedStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.COST_OPTIMIZED

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareBy<Candidate> { effectivePrice(it) }
                .thenBy { it.connection.priority }
                .thenBy { it.model.id }
        )

    private fun effectivePrice(candidate: Candidate): Double =
        if (candidate.model.free) 0.0 else candidate.blendedPricePerMillion
}

class HeadroomStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.HEADROOM

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareByDescending<Candidate> { headroom(it, context) }
                .thenBy { it.connection.priority }
                .thenBy { it.model.id }
        )

    private fun headroom(candidate: Candidate, context: RoutingContext): Double {
        val health = context.healthOf(candidate)
        val usage = context.usageOf(candidate)
        val contextRoom = (candidate.model.contextLength - context.estimatedInputTokens)
            .coerceAtLeast(0)
            .toDouble() / candidate.model.contextLength.coerceAtLeast(1).toDouble()
        val failurePenalty = 1.0 / (1.0 + usage.failures.toDouble())
        return health.score * contextRoom * failurePenalty
    }
}

class LkgpStrategy : RoutingStrategy {
    override val name: String = RoutingStrategies.LKGP

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> =
        candidates.sortedWith(
            compareByDescending<Candidate> { context.usageOf(it).lastSuccessAtMillis }
                .thenByDescending { context.usageOf(it).successes }
                .thenBy { it.connection.priority }
                .thenBy { it.model.id }
        )
}

class P2cStrategy(private val random: Random = Random.Default) : RoutingStrategy {
    override val name: String = RoutingStrategies.P2C

    override fun order(candidates: List<Candidate>, context: RoutingContext): List<Candidate> {
        if (candidates.size <= 1) return candidates
        val pool = candidates.toMutableList()
        val ordered = ArrayList<Candidate>(candidates.size)
        while (pool.size > 1) {
            val first = random.nextInt(pool.size)
            var second = random.nextInt(pool.size)
            if (second == first) second = (first + 1) % pool.size
            val chosen = if (load(pool[first], context) <= load(pool[second], context)) first else second
            ordered += pool.removeAt(chosen)
        }
        ordered += pool
        return ordered
    }

    private fun load(candidate: Candidate, context: RoutingContext): Long {
        val usage = context.usageOf(candidate)
        return usage.inFlight.toLong() * 10_000L + usage.requests.toLong()
    }
}

object RoutingStrategies {
    const val PRIORITY = "priority"
    const val FILL_FIRST = "fill-first"
    const val ROUND_ROBIN = "round-robin"
    const val WEIGHTED = "weighted"
    const val LEAST_USED = "least-used"
    const val COST_OPTIMIZED = "cost-optimized"
    const val HEADROOM = "headroom"
    const val LKGP = "lkgp"
    const val P2C = "p2c"

    val names: List<String> = listOf(
        PRIORITY,
        FILL_FIRST,
        ROUND_ROBIN,
        WEIGHTED,
        LEAST_USED,
        COST_OPTIMIZED,
        HEADROOM,
        LKGP,
        P2C
    )

    fun create(name: String, random: Random = Random.Default): RoutingStrategy = when (name) {
        FILL_FIRST -> FillFirstStrategy()
        ROUND_ROBIN -> RoundRobinStrategy()
        WEIGHTED -> WeightedStrategy(random)
        LEAST_USED -> LeastUsedStrategy()
        COST_OPTIMIZED -> CostOptimizedStrategy()
        HEADROOM -> HeadroomStrategy()
        LKGP -> LkgpStrategy()
        P2C -> P2cStrategy(random)
        else -> PriorityStrategy()
    }
}
