package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateScorerTest {

    private val fastCandidate = testCandidate(
        connectionId = "fast",
        modelId = "fast-model",
        inputPrice = 8.0,
        outputPrice = 24.0
    )
    private val cheapCandidate = testCandidate(
        connectionId = "cheap",
        modelId = "cheap-model",
        tier = ProviderTier.CHEAP,
        inputPrice = 0.1,
        outputPrice = 0.2
    )
    private val freeCandidate = testCandidate(
        connectionId = "free",
        modelId = "free-model",
        tier = ProviderTier.FREE,
        free = true
    )

    private val pool = listOf(fastCandidate, cheapCandidate, freeCandidate)

    private fun context(): RoutingContext = RoutingContext(
        channel = AutoChannels.AUTO,
        nowMillis = 10_000L,
        usage = mapOf(
            "fast" to usageStats(firstTokenEwmaMillis = 200.0),
            "cheap" to usageStats(firstTokenEwmaMillis = 2_500.0),
            "free" to usageStats(firstTokenEwmaMillis = 6_000.0)
        )
    )

    private fun neutralContext(): RoutingContext = RoutingContext(
        channel = AutoChannels.CHEAP,
        nowMillis = 10_000L
    )

    private fun ids(candidates: List<Candidate>): List<String> =
        candidates.map { it.connection.id }

    @Test
    fun fastChannelRanksLowLatencyFirst() {
        val ordered = CandidateScorer.forChannel(AutoChannels.FAST).order(pool, context())
        assertEquals("fast", ids(ordered).first())
    }

    @Test
    fun cheapChannelRanksLowPriceFirst() {
        val ordered = CandidateScorer.forChannel(AutoChannels.CHEAP).order(pool, neutralContext())
        assertEquals(listOf("free", "cheap", "fast"), ids(ordered))
    }

    @Test
    fun freeChannelKeepsOnlyFreeCandidates() {
        val ordered = CandidateScorer.forChannel(AutoChannels.FREE).order(pool, context())
        assertEquals(listOf("free"), ids(ordered))
    }

    @Test
    fun freeChannelFallsBackWhenNothingIsFree() {
        val paidOnly = listOf(fastCandidate, cheapCandidate)
        val ordered = CandidateScorer.forChannel(AutoChannels.FREE).order(paidOnly, context())
        assertEquals(paidOnly.size, ordered.size)
    }

    @Test
    fun balancedChannelPunishesOpenBreakers() {
        val ordered = CandidateScorer.forChannel(AutoChannels.AUTO).order(
            pool,
            context().copy(
                health = mapOf(
                    "fast|fast-model" to CandidateHealth(
                        breaker = BreakerState.OPEN,
                        breakerOpenRemainingMillis = 30_000L
                    )
                )
            )
        )
        assertEquals("fast", ids(ordered).last())
    }

    @Test
    fun balancedChannelPunishesLongCooldown() {
        val scorer = CandidateScorer.forChannel(AutoChannels.AUTO)
        val cool = scorer.score(
            cheapCandidate,
            context().copy(
                health = mapOf("cheap|cheap-model" to CandidateHealth(cooldownRemainingMillis = 600_000L))
            )
        )
        val warm = scorer.score(cheapCandidate, context())
        assertTrue(warm.total > cool.total)
        assertTrue(cool.cooldown < 0.2)
    }

    @Test
    fun unknownChannelUsesTheBalancedScorer() {
        assertEquals(AutoChannels.AUTO, CandidateScorer.forChannel("nonsense").name)
    }

    @Test
    fun contextFitFallsWhenTheWindowIsTooSmall() {
        val scorer = CandidateScorer.forChannel(AutoChannels.AUTO)
        val small = testCandidate(connectionId = "small", contextLength = 4_000)
        val large = testCandidate(connectionId = "large", contextLength = 200_000)
        val ctx = context().copy(estimatedInputTokens = 16_000)
        assertTrue(scorer.score(small, ctx).contextFit < 0.4)
        assertEquals(1.0, scorer.score(large, ctx).contextFit, 0.0001)
    }

    @Test
    fun missingLatencySamplesScoreNeutral() {
        val scorer = CandidateScorer.forChannel(AutoChannels.FAST)
        val breakdown = scorer.score(fastCandidate, RoutingContext(AutoChannels.FAST, 0L))
        assertEquals(CandidateScorer.NEUTRAL_LATENCY_SCORE, breakdown.latency, 0.0001)
    }

    @Test
    fun rankExposesTheBreakdownForEveryCandidate() {
        val ranked = CandidateScorer.forChannel(AutoChannels.AUTO).rank(pool, context())
        assertEquals(pool.size, ranked.size)
        ranked.forEach { scored -> assertTrue(scored.breakdown.total > 0.0) }
        val totals = ranked.map { it.breakdown.total }
        assertEquals(totals.sortedDescending(), totals)
    }

    @Test
    fun emptyPoolRanksEmpty() {
        assertTrue(CandidateScorer.forChannel(AutoChannels.AUTO).rank(emptyList(), context()).isEmpty())
    }
}
