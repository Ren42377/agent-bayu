package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.WireEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeConnectionSource(initial: List<Connection>) : ConnectionSource {

    private val state = MutableStateFlow(initial)

    val healthUpdates = ArrayList<Triple<String, ConnectionHealth, String?>>()

    override val connections: StateFlow<List<Connection>> = state

    override fun markHealth(connectionId: String, health: ConnectionHealth, detail: String?) {
        healthUpdates += Triple(connectionId, health, detail)
    }
}

private class FakeKeySource(private val keys: Map<String, String>) : KeySource {

    override fun key(connectionId: String): String? = keys[connectionId]

    override fun hasKey(connectionId: String): Boolean = keys[connectionId]?.isNotBlank() == true
}

private class FakeConfigSource(initial: RoutingConfig) : RoutingConfigSource {

    override val config: StateFlow<RoutingConfig> = MutableStateFlow(initial)
}

private class ScriptedAdapter : ChatAdapter {

    private val scripts = HashMap<String, List<WireEvent>>()

    val calls = ArrayList<String>()

    val keys = ArrayList<String?>()

    fun script(connectionId: String, vararg events: WireEvent) {
        scripts[connectionId] = events.toList()
    }

    override fun stream(candidate: Candidate, apiKey: String?, request: ChatRequest): Flow<WireEvent> = flow {
        calls += candidate.connection.id
        keys += apiKey
        scripts[candidate.connection.id].orEmpty().forEach { event -> emit(event) }
    }
}

private fun adapterOf(block: suspend FlowCollector<WireEvent>.() -> Unit): ChatAdapter = object : ChatAdapter {
    override fun stream(candidate: Candidate, apiKey: String?, request: ChatRequest): Flow<WireEvent> =
        flow { block() }
}

private fun drain(events: Flow<RouterEvent>): List<RouterEvent> = runBlocking { events.toList() }

private fun List<RouterEvent>.text(): String =
    filterIsInstance<RouterEvent.Delta>().joinToString("") { it.text }

private fun List<RouterEvent>.routes(): List<RouteDecision> =
    filterIsInstance<RouterEvent.Route>().map { it.decision }

private fun List<RouterEvent>.completed(): RouterEvent.Completed? =
    filterIsInstance<RouterEvent.Completed>().firstOrNull()

private fun List<RouterEvent>.failed(): RouterEvent.Failed? =
    filterIsInstance<RouterEvent.Failed>().firstOrNull()

class AiRouterTest {

    private val clock = FakeClock(1_000L)
    private val breaker = CircuitBreaker(clock)
    private val cooldown = CooldownRegistry(clock)
    private val lockout = ModelLockout(clock)
    private val gate = ResilienceGate(breaker, cooldown, lockout)
    private val usageTracker = UsageTracker(clock)
    private val adapter = ScriptedAdapter()

    private val catalog = ProviderCatalog(
        listOf(
            testProvider(
                id = "alpha",
                label = "Alpha",
                models = listOf(
                    ModelEntry(
                        id = "alpha-model",
                        contextLength = 4_000,
                        inputPricePerMillion = 1.0,
                        outputPricePerMillion = 2.0
                    )
                )
            ),
            testProvider(
                id = "beta",
                label = "Beta",
                models = listOf(
                    ModelEntry(
                        id = "beta-model",
                        contextLength = 2_000,
                        inputPricePerMillion = 1.0,
                        outputPricePerMillion = 2.0
                    )
                )
            )
        )
    )

    private val firstConnection = testConnection(
        id = "first",
        providerId = "alpha",
        label = "First",
        model = "alpha-model",
        priority = 10
    )

    private val secondConnection = testConnection(
        id = "second",
        providerId = "beta",
        label = "Second",
        model = "beta-model",
        priority = 20
    )

    private lateinit var connectionSource: FakeConnectionSource

    private fun router(
        connections: List<Connection> = listOf(firstConnection, secondConnection),
        keys: Map<String, String> = mapOf("first" to "key-1", "second" to "key-2"),
        config: RoutingConfig = RoutingConfig(),
        adapters: Map<WireFormat, ChatAdapter> = mapOf(WireFormat.OPENAI to adapter)
    ): AiRouter {
        connectionSource = FakeConnectionSource(connections)
        return AiRouter(
            catalog = catalog,
            connectionSource = connectionSource,
            keySource = FakeKeySource(keys),
            configSource = FakeConfigSource(config),
            gate = gate,
            usageTracker = usageTracker,
            adapters = adapters,
            clock = clock
        )
    }

    private fun request(prompt: String = "halo"): ChatRequest = ChatRequest(
        systemPrompt = "Kamu Bayu.",
        turns = listOf(ChatTurn(ChatRole.USER, prompt)),
        temperature = 0.4
    )

    @Test
    fun anEmptyConnectionListFailsBeforeAnyRequest() {
        val events = drain(router(connections = emptyList()).stream(request()))

        assertEquals(RouterEvent.Failed(RouterFailure.NO_CONNECTION), events.single())
        assertTrue(adapter.calls.isEmpty())
    }

    @Test
    fun connectionsWithoutAKeyAreNeverTried() {
        val events = drain(router(keys = emptyMap()).stream(request()))

        assertEquals(
            RouterEvent.Failed(RouterFailure.NO_CANDIDATE_AVAILABLE, SkipReason.MISSING_KEY.name),
            events.single()
        )
        assertTrue(adapter.calls.isEmpty())
    }

    @Test
    fun theBestCandidateAnswersAndTheDecisionIsComplete() {
        adapter.script(
            "first",
            WireEvent.Delta("Hal"),
            WireEvent.Delta("lo"),
            WireEvent.Usage(31, 9),
            WireEvent.Done
        )

        val events = drain(router().stream(request()))

        assertEquals("Hallo", events.text())
        assertEquals(1, events.routes().size)
        val completed = events.completed()
        val decision = completed?.decision
        assertEquals(AutoChannels.AUTO, decision?.channel)
        assertEquals(AutoChannels.AUTO, decision?.strategy)
        assertEquals("alpha", decision?.providerId)
        assertEquals("Alpha", decision?.providerLabel)
        assertEquals("alpha-model", decision?.model)
        assertEquals("first", decision?.connectionId)
        assertEquals("First", decision?.connectionLabel)
        assertEquals(ProviderTier.API_KEY, decision?.tier)
        assertEquals(1, decision?.attempt)
        assertEquals(2, decision?.candidatesConsidered)
        assertEquals(emptyList<SkippedCandidate>(), decision?.skipped)
        assertFalse(decision?.degraded == true)
        assertEquals(listOf("first"), adapter.calls)
        assertEquals(listOf("key-1"), adapter.keys)
        assertEquals(
            TokenUsage(31, 9, (31 * 1.0 + 9 * 2.0) / 1_000_000.0, false),
            completed?.usage
        )
        assertEquals(
            listOf(Triple("first", ConnectionHealth.READY, null)),
            connectionSource.healthUpdates
        )
        val stats = usageTracker.statsFor("first")
        assertEquals(1, stats.requests)
        assertEquals(1, stats.successes)
        assertEquals(0, stats.inFlight)
    }

    @Test
    fun aFailedCandidateHandsTheTurnToTheNextOne() {
        adapter.script(
            "first",
            WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 500, tripsBreaker = true))
        )
        adapter.script("second", WireEvent.Delta("dari kedua"), WireEvent.Done)

        val events = drain(router().stream(request()))

        assertEquals("dari kedua", events.text())
        assertEquals(listOf("first", "second"), adapter.calls)
        val decision = events.completed()?.decision
        assertEquals("second", decision?.connectionId)
        assertEquals("beta-model", decision?.model)
        assertEquals(2, decision?.attempt)
        assertEquals(
            listOf(SkippedCandidate("First", "alpha-model", SkipReason.FAILED, "500")),
            decision?.skipped
        )
        assertEquals(1, breaker.snapshot("alpha").consecutiveFailures)
        assertEquals(1, usageTracker.statsFor("first").failures)
        assertEquals("status=500 kind=RETRYABLE", usageTracker.statsFor("first").lastFailure)
        assertEquals(1, usageTracker.statsFor("second").successes)
    }

    @Test
    fun theRouteIsFrozenOnceTheFirstTokenIsOut() {
        adapter.script(
            "first",
            WireEvent.Delta("separuh"),
            WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 500))
        )
        adapter.script("second", WireEvent.Delta("tidak dipakai"), WireEvent.Done)

        val events = drain(router().stream(request()))

        assertEquals("separuh", events.text())
        assertEquals(listOf("first"), adapter.calls)
        val completed = events.completed()
        assertTrue(completed?.decision?.degraded == true)
        assertEquals(1, completed?.decision?.attempt)
        assertEquals(
            TokenUsage(4, 2, (4 * 1.0 + 2 * 2.0) / 1_000_000.0, true),
            completed?.usage
        )
        val stats = usageTracker.statsFor("first")
        assertEquals(1, stats.failures)
        assertEquals(0, stats.successes)
        assertEquals(0, stats.inFlight)
    }

    @Test
    fun terminalFailuresFlagTheConnectionAndAreNotRetried() {
        adapter.script(
            "first",
            WireEvent.Failure(RouteFailure(FailureKind.TERMINAL, "unauthorized", 401))
        )
        adapter.script("second", WireEvent.Delta("ok"), WireEvent.Done)
        val target = router()

        val opening = drain(target.stream(request()))

        assertEquals("ok", opening.text())
        assertEquals(2, opening.completed()?.decision?.attempt)
        assertEquals(
            listOf(
                Triple("first", ConnectionHealth.NEEDS_ATTENTION, "status=401 kind=TERMINAL"),
                Triple("second", ConnectionHealth.READY, null)
            ),
            connectionSource.healthUpdates
        )
        assertEquals(CooldownRegistry.DEFAULT_MAX_MILLIS, cooldown.remainingMillis("first"))

        adapter.calls.clear()
        val retry = drain(target.stream(request()))

        assertEquals(listOf("second"), adapter.calls)
        val decision = retry.completed()?.decision
        assertEquals(1, decision?.attempt)
        assertEquals(1, decision?.candidatesConsidered)
        assertEquals(
            listOf(SkippedCandidate("First", "alpha-model", SkipReason.COOLDOWN, "900")),
            decision?.skipped
        )
    }

    @Test
    fun modelLockFailuresParkTheModelWithoutCoolingTheConnection() {
        adapter.script(
            "first",
            WireEvent.Failure(RouteFailure(FailureKind.MODEL_LOCK, "model unavailable", 404))
        )
        adapter.script("second", WireEvent.Delta("ganti"), WireEvent.Done)

        val events = drain(router().stream(request()))

        assertEquals("ganti", events.text())
        assertEquals(ModelLockout.DEFAULT_LOCK_MILLIS, lockout.remainingMillis("alpha", "alpha-model"))
        assertEquals(0L, cooldown.remainingMillis("first"))
        assertEquals(0, breaker.snapshot("alpha").consecutiveFailures)
        assertEquals(
            listOf(Triple("second", ConnectionHealth.READY, null)),
            connectionSource.healthUpdates
        )
    }

    @Test
    fun everyCandidateFailingReportsTheLastLabel() {
        adapter.script(
            "first",
            WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 500, tripsBreaker = true))
        )
        adapter.script(
            "second",
            WireEvent.Failure(
                RouteFailure(FailureKind.COOLDOWN, "rate limited", 429, retryAfterMillis = 5_000L)
            )
        )

        val events = drain(router().stream(request()))

        assertEquals("", events.text())
        assertNull(events.completed())
        assertEquals(
            RouterEvent.Failed(RouterFailure.ALL_CANDIDATES_FAILED, "status=429 kind=COOLDOWN"),
            events.failed()
        )
        assertEquals(5_000L, cooldown.remainingMillis("second"))
    }

    @Test
    fun candidatesWithoutAnAdapterAreSkipped() {
        val events = drain(router(adapters = emptyMap()).stream(request()))

        assertEquals(RouterEvent.Failed(RouterFailure.ALL_CANDIDATES_FAILED), events.single())
        assertTrue(adapter.calls.isEmpty())
    }

    @Test
    fun disabledConnectionsAreLeftOut() {
        adapter.script("second", WireEvent.Delta("kedua"), WireEvent.Done)

        val events = drain(
            router(connections = listOf(firstConnection.copy(enabled = false), secondConnection))
                .stream(request())
        )

        assertEquals("kedua", events.text())
        val decision = events.completed()?.decision
        assertEquals("second", decision?.connectionId)
        assertEquals(1, decision?.candidatesConsidered)
        assertEquals(emptyList<SkippedCandidate>(), decision?.skipped)
    }

    @Test
    fun pinningAChannelToOneConnectionOverridesScoring() {
        adapter.script("second", WireEvent.Delta("pin"), WireEvent.Done)
        val channel = RoutingConfig.connectionChannel("second")

        val events = drain(router(config = RoutingConfig(channel = channel)).stream(request()))

        assertEquals("pin", events.text())
        val decision = events.completed()?.decision
        assertEquals(channel, decision?.channel)
        assertEquals(RoutingStrategies.PRIORITY, decision?.strategy)
        assertEquals("second", decision?.connectionId)
        assertEquals(1, decision?.candidatesConsidered)
        assertEquals(listOf("second"), adapter.calls)
    }

    @Test
    fun comboStepsDecideTheAttemptOrder() {
        adapter.script(
            "second",
            WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 503, tripsBreaker = true))
        )
        adapter.script("first", WireEvent.Delta("balik"), WireEvent.Done)
        val combo = Combo(
            id = "mine",
            label = "Mine",
            steps = listOf(
                ComboStep(strategy = RoutingStrategies.COST_OPTIMIZED, connectionIds = listOf("second")),
                ComboStep(strategy = RoutingStrategies.PRIORITY)
            )
        )
        val config = RoutingConfig(
            channel = RoutingConfig.comboChannel("mine"),
            combos = listOf(combo)
        )

        val events = drain(router(config = config).stream(request()))

        assertEquals("balik", events.text())
        assertEquals(listOf("second", "first"), adapter.calls)
        val decision = events.completed()?.decision
        assertEquals(RoutingConfig.comboChannel("mine"), decision?.channel)
        assertEquals(RoutingStrategies.PRIORITY, decision?.strategy)
        assertEquals("first", decision?.connectionId)
        assertEquals(2, decision?.attempt)
        assertEquals(2, decision?.candidatesConsidered)
    }

    @Test
    fun openBreakersTakeTheProviderOutOfRotation() {
        repeat(CircuitBreaker.DEFAULT_REMOTE_THRESHOLD) { breaker.recordFailure("alpha") }
        adapter.script("second", WireEvent.Delta("cadangan"), WireEvent.Done)

        val events = drain(router().stream(request()))

        assertEquals("cadangan", events.text())
        assertEquals(listOf("second"), adapter.calls)
        assertEquals(
            listOf(SkippedCandidate("First", "alpha-model", SkipReason.BREAKER_OPEN, "60")),
            events.completed()?.decision?.skipped
        )
    }

    @Test
    fun oversizedPromptsStillGetTheRoomiestModel() {
        adapter.script("first", WireEvent.Delta("panjang"), WireEvent.Done)

        val events = drain(router().stream(request(prompt = "a".repeat(12_000))))

        assertEquals("panjang", events.text())
        val decision = events.completed()?.decision
        assertEquals("first", decision?.connectionId)
        assertEquals(2, decision?.candidatesConsidered)
        assertEquals(emptyList<SkippedCandidate>(), decision?.skipped)
    }

    @Test
    fun latencyIsMeasuredFromTheClock() {
        val timed = adapterOf {
            clock.advance(250L)
            emit(WireEvent.Delta("halo"))
            clock.advance(100L)
            emit(WireEvent.Done)
        }

        val events = drain(
            router(adapters = mapOf(WireFormat.OPENAI to timed)).stream(request())
        )

        val decision = events.completed()?.decision
        assertEquals(250L, decision?.firstTokenMillis)
        assertEquals(350L, decision?.totalMillis)
        assertEquals(250L, events.routes().single().firstTokenMillis)
        val stats = usageTracker.statsFor("first")
        assertEquals(250.0, stats.firstTokenEwmaMillis, 0.0001)
        assertEquals(250L, stats.p95FirstTokenMillis)
    }

    @Test
    fun previewAndActiveChannelMirrorTheConfig() {
        val target = router()

        assertEquals(AutoChannels.AUTO, target.activeChannel())
        assertEquals(listOf("first", "second"), target.preview().map { it.candidate.connection.id })
        assertEquals(
            listOf(AutoChannels.AUTO, AutoChannels.AUTO),
            target.preview().map { it.strategy }
        )
        assertEquals(2, target.candidates().size)
        assertTrue(target.health(target.candidates().first()).available)
        assertEquals(
            listOf("second"),
            target.preview(RoutingConfig.connectionChannel("second")).map { it.candidate.connection.id }
        )
    }
}
