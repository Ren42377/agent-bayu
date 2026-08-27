package dev.agentbayu.app.ai

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingStrategyTest {

    private val context = RoutingContext(channel = AutoChannels.AUTO, nowMillis = 1_000L)

    private fun labels(candidates: List<Candidate>): List<String> =
        candidates.map { it.connection.id }

    @Test
    fun everyStrategyHandlesAnEmptyList() {
        RoutingStrategies.names.forEach { name ->
            val ordered = RoutingStrategies.create(name).order(emptyList(), context)
            assertTrue(name, ordered.isEmpty())
        }
    }

    @Test
    fun everyStrategyKeepsAllCandidates() {
        val candidates = listOf(
            testCandidate(connectionId = "a", priority = 30),
            testCandidate(connectionId = "b", priority = 20),
            testCandidate(connectionId = "c", priority = 10)
        )
        RoutingStrategies.names.forEach { name ->
            val ordered = RoutingStrategies.create(name, Random(7)).order(candidates, context)
            assertEquals(name, candidates.size, ordered.size)
            assertEquals(name, candidates.toSet(), ordered.toSet())
        }
    }

    @Test
    fun unknownStrategyFallsBackToPriority() {
        val strategy = RoutingStrategies.create("does-not-exist")
        assertEquals(RoutingStrategies.PRIORITY, strategy.name)
    }

    @Test
    fun priorityOrdersByPriorityThenTier() {
        val candidates = listOf(
            testCandidate(connectionId = "late", priority = 200),
            testCandidate(connectionId = "free", priority = 100, tier = ProviderTier.FREE),
            testCandidate(connectionId = "paid", priority = 100, tier = ProviderTier.SUBSCRIPTION)
        )
        val ordered = PriorityStrategy().order(candidates, context)
        assertEquals(listOf("paid", "free", "late"), labels(ordered))
    }

    @Test
    fun costOptimizedPutsFreeAndCheapFirst() {
        val candidates = listOf(
            testCandidate(connectionId = "expensive", inputPrice = 10.0, outputPrice = 30.0),
            testCandidate(connectionId = "cheap", inputPrice = 0.1, outputPrice = 0.3),
            testCandidate(connectionId = "free", free = true)
        )
        val ordered = CostOptimizedStrategy().order(candidates, context)
        assertEquals(listOf("free", "cheap", "expensive"), labels(ordered))
    }

    @Test
    fun costOptimizedTreatsMissingPriceAsExpensive() {
        val candidates = listOf(
            testCandidate(connectionId = "unknown"),
            testCandidate(connectionId = "known", inputPrice = 1.0, outputPrice = 2.0)
        )
        val ordered = CostOptimizedStrategy().order(candidates, context)
        assertEquals(listOf("known", "unknown"), labels(ordered))
    }

    @Test
    fun leastUsedPrefersIdleConnections() {
        val busy = testCandidate(connectionId = "busy")
        val idle = testCandidate(connectionId = "idle")
        val warm = testCandidate(connectionId = "warm")
        val ordered = LeastUsedStrategy().order(
            listOf(busy, idle, warm),
            context.copy(
                usage = mapOf(
                    "busy" to usageStats(requests = 5, inFlight = 2),
                    "warm" to usageStats(requests = 5)
                )
            )
        )
        assertEquals(listOf("idle", "warm", "busy"), labels(ordered))
    }

    @Test
    fun lkgpPrefersTheLastKnownGoodConnection() {
        val ordered = LkgpStrategy().order(
            listOf(
                testCandidate(connectionId = "stale"),
                testCandidate(connectionId = "recent"),
                testCandidate(connectionId = "never")
            ),
            context.copy(
                usage = mapOf(
                    "stale" to usageStats(successes = 9, lastSuccessAtMillis = 100L),
                    "recent" to usageStats(successes = 1, lastSuccessAtMillis = 900L)
                )
            )
        )
        assertEquals(listOf("recent", "stale", "never"), labels(ordered))
    }

    @Test
    fun fillFirstStaysOnTheWarmConnection() {
        val ordered = FillFirstStrategy().order(
            listOf(
                testCandidate(connectionId = "cold", priority = 10),
                testCandidate(connectionId = "warm", priority = 90)
            ),
            context.copy(usage = mapOf("warm" to usageStats(lastSuccessAtMillis = 500L)))
        )
        assertEquals(listOf("warm", "cold"), labels(ordered))
    }

    @Test
    fun headroomPrefersHealthyRoomyCandidates() {
        val ordered = HeadroomStrategy().order(
            listOf(
                testCandidate(connectionId = "small", contextLength = 8_000),
                testCandidate(connectionId = "large", contextLength = 200_000),
                testCandidate(connectionId = "tripped", contextLength = 200_000)
            ),
            context.copy(
                estimatedInputTokens = 4_000,
                health = mapOf(
                    "tripped|model-a" to CandidateHealth(
                        breaker = BreakerState.OPEN,
                        breakerOpenRemainingMillis = 5_000L
                    )
                )
            )
        )
        assertEquals(listOf("large", "small", "tripped"), labels(ordered))
    }

    @Test
    fun headroomPenalizesRepeatedFailures() {
        val ordered = HeadroomStrategy().order(
            listOf(
                testCandidate(connectionId = "flaky"),
                testCandidate(connectionId = "steady")
            ),
            context.copy(usage = mapOf("flaky" to usageStats(failures = 4)))
        )
        assertEquals(listOf("steady", "flaky"), labels(ordered))
    }

    @Test
    fun roundRobinRotatesAcrossCalls() {
        val candidates = listOf(
            testCandidate(connectionId = "a", priority = 10),
            testCandidate(connectionId = "b", priority = 20),
            testCandidate(connectionId = "c", priority = 30)
        )
        val strategy = RoundRobinStrategy()
        assertEquals(listOf("a", "b", "c"), labels(strategy.order(candidates, context)))
        assertEquals(listOf("b", "c", "a"), labels(strategy.order(candidates, context)))
        assertEquals(listOf("c", "a", "b"), labels(strategy.order(candidates, context)))
        assertEquals(listOf("a", "b", "c"), labels(strategy.order(candidates, context)))
    }

    @Test
    fun roundRobinReturnsSingleCandidateUnchanged() {
        val single = listOf(testCandidate(connectionId = "only"))
        assertEquals(single, RoundRobinStrategy().order(single, context))
    }

    @Test
    fun weightedFavorsTheHeavierConnection() {
        val candidates = listOf(
            testCandidate(connectionId = "light", weight = 1),
            testCandidate(connectionId = "heavy", weight = 99)
        )
        val strategy = WeightedStrategy(Random(42))
        val firstPicks = (1..40).map { strategy.order(candidates, context).first().connection.id }
        val heavyWins = firstPicks.count { it == "heavy" }
        assertTrue("heavy won " + heavyWins + " of 40", heavyWins > 30)
    }

    @Test
    fun weightedKeepsZeroWeightCandidates() {
        val candidates = listOf(
            testCandidate(connectionId = "zero-a", weight = 0),
            testCandidate(connectionId = "zero-b", weight = 0)
        )
        val ordered = WeightedStrategy(Random(1)).order(candidates, context)
        assertEquals(candidates.toSet(), ordered.toSet())
    }

    @Test
    fun weightedPlacesZeroWeightLast() {
        val candidates = listOf(
            testCandidate(connectionId = "zero", weight = 0),
            testCandidate(connectionId = "one", weight = 5)
        )
        val ordered = WeightedStrategy(Random(3)).order(candidates, context)
        assertEquals(listOf("one", "zero"), labels(ordered))
    }

    @Test
    fun p2cAvoidsTheBusiestCandidate() {
        val candidates = listOf(
            testCandidate(connectionId = "busy"),
            testCandidate(connectionId = "idle")
        )
        val ordered = P2cStrategy(Random(11)).order(
            candidates,
            context.copy(usage = mapOf("busy" to usageStats(requests = 20, inFlight = 3)))
        )
        assertEquals(listOf("idle", "busy"), labels(ordered))
    }
}
