package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.AiRouter
import dev.agentbayu.app.ai.AutoChannels
import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.CircuitBreaker
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ConnectionSource
import dev.agentbayu.app.ai.CooldownRegistry
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.ai.KeySource
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.ModelLockout
import dev.agentbayu.app.ai.ProviderCatalog
import dev.agentbayu.app.ai.ResilienceGate
import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.RoutingConfig
import dev.agentbayu.app.ai.RoutingConfigSource
import dev.agentbayu.app.ai.TokenUsage
import dev.agentbayu.app.ai.UsageTracker
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.WireEvent
import dev.agentbayu.app.ai.testConnection
import dev.agentbayu.app.ai.testProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SYSTEM_PROMPT = "Kamu Bayu."

private const val SCREEN_TEMPLATE = "Konteks layar: %s"

private class RecordingAdapter : ChatAdapter {

    val requests = ArrayList<ChatRequest>()

    var events: List<WireEvent> = emptyList()

    override fun stream(candidate: Candidate, apiKey: String?, request: ChatRequest): Flow<WireEvent> = flow {
        requests += request
        events.forEach { event -> emit(event) }
    }
}

private fun drain(events: Flow<AgentEvent>): List<AgentEvent> = runBlocking { events.toList() }

class RouterAgentEngineTest {

    private val clock = FakeClock(5_000L)
    private val adapter = RecordingAdapter()

    private val copy = RouterCopy(
        noConnection = "Belum ada koneksi.",
        noCandidateAvailable = "Koneksi belum siap.",
        allCandidatesFailed = "Semua koneksi gagal."
    )

    private val catalog = ProviderCatalog(
        listOf(
            testProvider(
                id = "alpha",
                label = "Alpha",
                models = listOf(
                    ModelEntry(
                        id = "alpha-model",
                        contextLength = 8_000,
                        inputPricePerMillion = 1.0,
                        outputPricePerMillion = 2.0
                    )
                )
            )
        )
    )

    private val connection = testConnection(
        id = "first",
        providerId = "alpha",
        label = "First",
        model = "alpha-model"
    )

    private fun engine(
        pool: List<Connection> = listOf(connection),
        keys: Map<String, String> = mapOf("first" to "key-1")
    ): RouterAgentEngine {
        val connectionSource = object : ConnectionSource {
            override val connections: StateFlow<List<Connection>> = MutableStateFlow(pool)

            override fun markHealth(connectionId: String, health: ConnectionHealth, detail: String?) = Unit
        }
        val keySource = object : KeySource {
            override fun key(connectionId: String): String? = keys[connectionId]

            override fun hasKey(connectionId: String): Boolean = keys[connectionId]?.isNotBlank() == true
        }
        val configSource = object : RoutingConfigSource {
            override val config: StateFlow<RoutingConfig> = MutableStateFlow(RoutingConfig())
        }
        val router = AiRouter(
            catalog = catalog,
            connectionSource = connectionSource,
            keySource = keySource,
            configSource = configSource,
            gate = ResilienceGate(CircuitBreaker(clock), CooldownRegistry(clock), ModelLockout(clock)),
            usageTracker = UsageTracker(clock),
            adapters = mapOf(WireFormat.OPENAI to adapter),
            clock = clock
        )
        return RouterAgentEngine(
            router = router,
            contextBuilder = ContextBuilder(SYSTEM_PROMPT, SCREEN_TEMPLATE),
            copy = copy
        )
    }

    @Test
    fun anEmptyConnectionListSpeaksTheNoConnectionCopy() {
        val events = drain(engine(pool = emptyList()).reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.noConnection)), events)
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun aConnectionWithoutAKeySpeaksTheNoCandidateCopy() {
        val events = drain(engine(keys = emptyMap()).reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.noCandidateAvailable)), events)
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun anExhaustedRotationSpeaksTheAllCandidatesFailedCopy() {
        adapter.events = listOf(WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 500)))

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.allCandidatesFailed)), events)
        assertEquals(1, adapter.requests.size)
    }

    @Test
    fun aSuccessfulTurnBecomesRouteDeltasAndCompleted() {
        adapter.events = listOf(
            WireEvent.Delta("Hal"),
            WireEvent.Delta("lo"),
            WireEvent.Usage(31, 9),
            WireEvent.Done
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(4, events.size)
        val route = events.first() as AgentEvent.Route
        assertEquals(AutoChannels.AUTO, route.decision.channel)
        assertEquals("alpha", route.decision.providerId)
        assertEquals("alpha-model", route.decision.model)
        assertEquals("first", route.decision.connectionId)
        assertEquals(listOf("Hal", "lo"), events.filterIsInstance<AgentEvent.Delta>().map { it.text })
        val completed = events.last() as AgentEvent.Completed
        assertEquals("first", completed.decision?.connectionId)
        assertEquals(1, completed.decision?.attempt)
        assertEquals(
            TokenUsage(31, 9, (31 * 1.0 + 9 * 2.0) / 1_000_000.0, false),
            completed.usage
        )
    }

    @Test
    fun theBuiltContextIsWhatReachesTheAdapter() {
        adapter.events = listOf(WireEvent.Delta("ya"), WireEvent.Done)
        val history = listOf(
            ChatMessage(id = 1L, author = MessageAuthor.USER, text = "pertanyaan lama"),
            ChatMessage(id = 2L, author = MessageAuthor.AGENT, text = "   "),
            ChatMessage(id = 3L, author = MessageAuthor.AGENT, text = "jawaban lama")
        )

        drain(
            engine().reply(
                AgentRequest(
                    prompt = "  halo  ",
                    screenContext = " Layar catatan ",
                    history = history
                )
            )
        )

        val request = adapter.requests.single()
        assertEquals(
            SYSTEM_PROMPT + "\n\n" + SCREEN_TEMPLATE.format("Layar catatan"),
            request.systemPrompt
        )
        assertEquals(
            listOf(
                ChatTurn(ChatRole.USER, "pertanyaan lama"),
                ChatTurn(ChatRole.ASSISTANT, "jawaban lama"),
                ChatTurn(ChatRole.USER, "halo")
            ),
            request.turns
        )
        assertEquals(ContextBuilder.DEFAULT_TEMPERATURE, request.temperature)
        assertNull(request.maxOutputTokens)
    }
}
