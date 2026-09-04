package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ActiveProvider
import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.FakeClock
import dev.agentbayu.app.ai.FakeConnectionSource
import dev.agentbayu.app.ai.FakeKeys
import dev.agentbayu.app.ai.KeySourceCredentials
import dev.agentbayu.app.ai.LogStore
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.ProviderCatalog
import dev.agentbayu.app.ai.RouteFailure
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
import dev.agentbayu.app.ai.tools.ToolCall
import dev.agentbayu.app.ai.tools.ToolHandler
import dev.agentbayu.app.ai.tools.ToolRegistry
import dev.agentbayu.app.ai.tools.ToolResult
import dev.agentbayu.app.ai.tools.ToolSpec
import dev.agentbayu.app.ai.tools.toolSchema
import kotlinx.coroutines.flow.Flow
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
    val keys = ArrayList<String?>()

    var events: List<WireEvent> = emptyList()
    var script: List<List<WireEvent>> = emptyList()

    override fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String>
    ): Flow<WireEvent> = flow {
        val pass = requests.size
        requests += request
        keys += apiKey
        val frame = if (script.isEmpty()) {
            events
        } else {
            script[pass.coerceAtMost(script.lastIndex)]
        }
        frame.forEach { event -> emit(event) }
    }
}

private class FakeTool(name: String, private val content: String) : ToolHandler {

    val calls = ArrayList<ToolCall>()

    override val spec = ToolSpec(
        name = name,
        description = "test tool",
        parameters = toolSchema()
    )

    override suspend fun run(call: ToolCall): ToolResult {
        calls += call
        return ToolResult(callId = call.id, name = call.name, content = content)
    }
}

private fun drain(events: Flow<AgentEvent>): List<AgentEvent> = runBlocking { events.toList() }

class ProviderAgentEngineTest {

    private val clock = FakeClock(5_000L)
    private val adapter = RecordingAdapter()

    private val copy = ProviderCopy(
        noConnection = "Belum ada koneksi.",
        unknownProvider = "Provider tidak dikenal.",
        missingCredential = "Kunci belum diisi.",
        unauthorized = "Kunci ditolak.",
        outOfCredit = "Saldo tidak cukup.",
        quotaExhausted = "Kuota habis.",
        rateLimited = "Sedang dibatasi.",
        rateLimitedWait = "Coba lagi %1\$d detik.",
        modelUnavailable = "Model tidak tersedia.",
        serverError = "Penyedia bermasalah.",
        networkError = "Tidak ada jaringan.",
        genericError = "Status %1\$d."
    )

    private val catalog = ProviderCatalog(
        listOf(
            testProvider(
                id = "alpha",
                label = "Alpha",
                tools = true,
                models = listOf(
                    ModelEntry(
                        id = "alpha-model",
                        contextLength = 8_000,
                        inputPricePerMillion = 1.0,
                        outputPricePerMillion = 2.0
                    )
                )
            ),
            testProvider(
                id = "gratis",
                label = "Gratis",
                authKind = AuthKind.NONE,
                optionalKey = true,
                models = listOf(ModelEntry(id = "gratis-model"))
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
        activeId: String? = "first",
        keys: Map<String, String> = mapOf("first" to "key-1"),
        tools: ToolRegistry = ToolRegistry()
    ): ProviderAgentEngine {
        val connections = FakeConnectionSource(pool, activeId)
        val keySource = FakeKeys(keys)
        val client = AiClient(
            activeProvider = ActiveProvider(connections, catalog, keySource),
            connections = connections,
            credentials = KeySourceCredentials(keySource),
            adapters = mapOf(WireFormat.OPENAI to adapter),
            usageTracker = UsageTracker(clock),
            logStore = LogStore(clock),
            clock = clock,
            pause = {}
        )
        return ProviderAgentEngine(
            client = client,
            contextBuilder = ContextBuilder(SYSTEM_PROMPT, SCREEN_TEMPLATE),
            copy = copy,
            tools = tools
        )
    }

    @Test
    fun anEmptyConnectionListSpeaksTheNoConnectionCopy() {
        val events = drain(engine(pool = emptyList()).reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.noConnection)), events)
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun aConnectionWithoutAKeySpeaksTheMissingCredentialCopy() {
        val events = drain(engine(keys = emptyMap()).reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.missingCredential)), events)
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun aConnectionOnAnUnknownProviderSpeaksTheUnknownProviderCopy() {
        val orphan = testConnection(id = "ghost", providerId = "nowhere", model = "any")

        val events = drain(
            engine(pool = listOf(orphan), activeId = "ghost").reply(AgentRequest(prompt = "halo"))
        )

        assertEquals(listOf(AgentEvent.Failed(copy.unknownProvider)), events)
        assertTrue(adapter.requests.isEmpty())
    }

    @Test
    fun aKeylessConnectionReachesTheAdapterWithoutACredential() {
        adapter.events = listOf(WireEvent.Delta("ya"), WireEvent.Done)
        val free = testConnection(id = "free", providerId = "gratis", model = "gratis-model")

        val events = drain(
            engine(pool = listOf(free), activeId = "free", keys = emptyMap())
                .reply(AgentRequest(prompt = "halo"))
        )

        assertEquals(1, adapter.requests.size)
        assertNull(adapter.keys.single())
        assertEquals(AuthKind.NONE, (events.first() as AgentEvent.Detail).detail.authKind)
    }

    @Test
    fun aServerFailureSpeaksTheServerErrorCopy() {
        adapter.events = listOf(WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "boom", 500)))

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.serverError)), events)
        assertEquals(3, adapter.requests.size)
    }

    @Test
    fun aRejectedKeySpeaksTheUnauthorizedCopy() {
        adapter.events = listOf(WireEvent.Failure(RouteFailure(FailureKind.TERMINAL, "no", 401)))

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.unauthorized)), events)
    }

    @Test
    fun anExhaustedFreeQuotaSpeaksTheQuotaCopy() {
        adapter.events = listOf(WireEvent.Failure(RouteFailure(FailureKind.TERMINAL, "quota", 429)))

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.quotaExhausted)), events)
    }

    @Test
    fun aRateLimitWithRetryAfterSpeaksTheWaitCopy() {
        adapter.events = listOf(
            WireEvent.Failure(
                RouteFailure(
                    kind = FailureKind.COOLDOWN,
                    message = "slow down",
                    statusCode = 429,
                    retryAfterMillis = 2_400L
                )
            )
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed("Coba lagi 3 detik.")), events)
    }

    @Test
    fun aRateLimitWithoutRetryAfterSpeaksThePlainCopy() {
        adapter.events = listOf(
            WireEvent.Failure(RouteFailure(FailureKind.COOLDOWN, "slow down", 429))
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.rateLimited)), events)
    }

    @Test
    fun aLockedModelSpeaksTheModelUnavailableCopy() {
        adapter.events = listOf(
            WireEvent.Failure(RouteFailure(FailureKind.MODEL_LOCK, "unknown model", 404))
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.modelUnavailable)), events)
    }

    @Test
    fun aMissingNetworkSpeaksTheNetworkCopy() {
        adapter.events = listOf(
            WireEvent.Failure(RouteFailure(FailureKind.RETRYABLE, "offline", null))
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed(copy.networkError)), events)
    }

    @Test
    fun anUnclassifiedStatusFallsBackToTheGenericCopy() {
        adapter.events = listOf(
            WireEvent.Failure(RouteFailure(FailureKind.TERMINAL, "bad request", 400))
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(listOf(AgentEvent.Failed("Status 400.")), events)
    }

    @Test
    fun aSuccessfulTurnBecomesDetailDeltasAndCompleted() {
        adapter.events = listOf(
            WireEvent.Delta("Hal"),
            WireEvent.Delta("lo"),
            WireEvent.Usage(31, 9),
            WireEvent.Done
        )

        val events = drain(engine().reply(AgentRequest(prompt = "halo")))

        assertEquals(4, events.size)
        val started = events.first() as AgentEvent.Detail
        assertEquals("alpha", started.detail.providerId)
        assertEquals("alpha-model", started.detail.model)
        assertEquals("first", started.detail.connectionId)
        assertEquals(AuthKind.API_KEY, started.detail.authKind)
        assertEquals(listOf("Hal", "lo"), events.filterIsInstance<AgentEvent.Delta>().map { it.text })
        val completed = events.last() as AgentEvent.Completed
        assertEquals("first", completed.detail?.connectionId)
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
        assertEquals("key-1", adapter.keys.single())
    }

    @Test
    fun aToolCallRunsAndItsResultReachesTheNextRequest() {
        val tool = FakeTool(name = "list_tasks", content = "1. Beli susu")
        val call = ToolCall(id = "call-1", name = "list_tasks", arguments = "{}")
        adapter.script = listOf(
            listOf(WireEvent.ToolUse(call), WireEvent.Done),
            listOf(WireEvent.Delta("Ada satu tugas."), WireEvent.Done)
        )

        val events = drain(
            engine(tools = ToolRegistry(listOf(tool))).reply(AgentRequest(prompt = "cek tugas"))
        )

        assertEquals(2, adapter.requests.size)
        assertEquals(listOf("list_tasks"), adapter.requests.first().tools.map { it.name })
        assertEquals(listOf(call), tool.calls)
        assertEquals(
            listOf(
                AgentEvent.ToolStarted("list_tasks", "list_tasks"),
                AgentEvent.ToolFinished("list_tasks", true)
            ),
            events.filter { it is AgentEvent.ToolStarted || it is AgentEvent.ToolFinished }
        )

        val turns = adapter.requests[1].turns
        val asked = turns[turns.lastIndex - 1]
        assertEquals(ChatRole.ASSISTANT, asked.role)
        assertEquals(listOf(call), asked.toolCalls)
        val answered = turns.last()
        assertEquals(ChatRole.TOOL, answered.role)
        assertEquals("1. Beli susu", answered.content)
        assertEquals("call-1", answered.toolCallId)
        assertEquals(
            listOf("Ada satu tugas."),
            events.filterIsInstance<AgentEvent.Delta>().map { it.text }
        )
        assertTrue(events.last() is AgentEvent.Completed)
    }

    @Test
    fun theToolLoopStopsAtThePassCeiling() {
        val tool = FakeTool(name = "list_tasks", content = "kosong")
        adapter.events = listOf(
            WireEvent.ToolUse(ToolCall(id = "call-1", name = "list_tasks", arguments = "{}")),
            WireEvent.Done
        )

        val events = drain(
            engine(tools = ToolRegistry(listOf(tool))).reply(AgentRequest(prompt = "terus"))
        )

        assertEquals(8, adapter.requests.size)
        assertTrue(adapter.requests.last().tools.isEmpty())
        assertEquals(1, tool.calls.size)
        assertTrue(events.last() is AgentEvent.Completed)
    }
}
