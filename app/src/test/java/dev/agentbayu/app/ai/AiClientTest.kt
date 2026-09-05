package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.WireEvent
import dev.agentbayu.app.ai.tools.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {

    private class RecordingAdapter(private val events: List<WireEvent>) : ChatAdapter {
        var lastKey: String? = null
        var lastRequest: ChatRequest? = null
        var lastHeaders: Map<String, String> = emptyMap()
        var lastCandidate: Candidate? = null

        override fun stream(
            candidate: Candidate,
            apiKey: String?,
            request: ChatRequest,
            authHeaders: Map<String, String>
        ): Flow<WireEvent> = flow {
            lastKey = apiKey
            lastRequest = request
            lastHeaders = authHeaders
            lastCandidate = candidate
            events.forEach { emit(it) }
        }
    }

    private class StubProjectResolver(private val projectId: String?) : ProjectResolver {
        var calls = 0

        override suspend fun resolve(
            candidate: Candidate,
            credential: WireCredential
        ): ProjectResolution {
            calls += 1
            if (projectId == null) return ProjectResolution.Failed(setupFailure)
            return ProjectResolution.Ready(
                candidate.copy(connection = candidate.connection.copy(projectId = projectId))
            )
        }

        companion object {
            val setupFailure = RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "sign in again to finish Antigravity project setup",
                needsSetup = true
            )
        }
    }

    private class FlakyAdapter(
        private val failure: RouteFailure,
        private val failures: Int
    ) : ChatAdapter {
        var calls = 0

        override fun stream(
            candidate: Candidate,
            apiKey: String?,
            request: ChatRequest,
            authHeaders: Map<String, String>
        ): Flow<WireEvent> = flow {
            calls += 1
            if (calls <= failures) {
                emit(WireEvent.Failure(failure))
            } else {
                emit(WireEvent.Delta("ok"))
            }
            emit(WireEvent.Done)
        }
    }

    private class SilentAdapter(private val silentAttempts: Int) : ChatAdapter {
        var calls = 0

        override fun stream(
            candidate: Candidate,
            apiKey: String?,
            request: ChatRequest,
            authHeaders: Map<String, String>
        ): Flow<WireEvent> = flow {
            calls += 1
            if (calls > silentAttempts) emit(WireEvent.Delta("ok"))
            emit(WireEvent.Done)
        }
    }

    private class ToolOnlyAdapter(private val call: ToolCall) : ChatAdapter {
        var calls = 0

        override fun stream(
            candidate: Candidate,
            apiKey: String?,
            request: ChatRequest,
            authHeaders: Map<String, String>
        ): Flow<WireEvent> = flow {
            calls += 1
            emit(WireEvent.ToolUse(call))
            emit(WireEvent.Done)
        }
    }

    private val storedKeys = FakeKeys(mapOf("conn-1" to "key-1234"))

    private val request = ChatRequest(
        systemPrompt = "you are bayu",
        turns = listOf(ChatTurn(ChatRole.USER, "halo")),
        temperature = 0.7
    )

    private fun clientFor(
        candidate: Candidate,
        adapter: ChatAdapter?,
        keys: KeySource = storedKeys,
        credentials: CredentialProvider = KeySourceCredentials(keys),
        connections: FakeConnectionSource = FakeConnectionSource(
            listOf(candidate.connection),
            candidate.connection.id
        ),
        tracker: UsageTracker = UsageTracker(FakeClock()),
        logStore: LogStore = LogStore(FakeClock()),
        clock: Clock = FakeClock(1_000L),
        projects: ProjectResolver = ReadyProjectResolver
    ): AiClient = AiClient(
        activeProvider = ActiveProvider(
            connections,
            ProviderCatalog(listOf(candidate.provider)),
            keys
        ),
        connections = connections,
        credentials = credentials,
        adapters = adapter?.let { mapOf(candidate.provider.wireFormat to it) } ?: emptyMap(),
        usageTracker = tracker,
        logStore = logStore,
        clock = clock,
        projects = projects
    )

    @Test
    fun `emits started delta and completed on a healthy stream`() = runTest {
        val candidate = testCandidate(inputPrice = 1.0, outputPrice = 2.0)
        val adapter = RecordingAdapter(
            listOf(
                WireEvent.Delta("ha"),
                WireEvent.Delta("lo"),
                WireEvent.Usage(inputTokens = 11, outputTokens = 7),
                WireEvent.Done
            )
        )
        val tracker = UsageTracker(FakeClock())

        val events = clientFor(candidate, adapter, tracker = tracker).stream(request).toList()

        assertTrue(events.first() is ReplyEvent.Started)
        assertEquals(
            listOf("ha", "lo"),
            events.filterIsInstance<ReplyEvent.Delta>().map { it.text }
        )
        val completed = events.last() as ReplyEvent.Completed
        assertEquals(11, completed.usage.inputTokens)
        assertEquals(7, completed.usage.outputTokens)
        assertFalse(completed.usage.estimated)
        assertNotNull(completed.usage.estimatedCostUsd)
        assertEquals("groq", completed.detail.providerId)
        assertEquals("model-a", completed.detail.model)
        assertEquals("conn-1", completed.detail.connectionId)
        assertEquals(1, tracker.statsFor("conn-1").successes)
    }

    @Test
    fun `estimates tokens when the provider reports none`() = runTest {
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("12345678"), WireEvent.Done))

        val events = clientFor(testCandidate(), adapter).stream(request).toList()

        val completed = events.last() as ReplyEvent.Completed
        assertTrue(completed.usage.estimated)
        assertEquals(2, completed.usage.outputTokens)
        assertEquals(4, completed.usage.inputTokens)
        assertNull(completed.usage.estimatedCostUsd)
    }

    @Test
    fun `flags usage as estimated when no token counts come back`() = runTest {
        val adapter = RecordingAdapter(
            listOf(
                WireEvent.Delta("hey"),
                WireEvent.Usage(inputTokens = 0, outputTokens = 0),
                WireEvent.Done
            )
        )

        val events = clientFor(testCandidate(), adapter).stream(request).toList()

        assertTrue((events.last() as ReplyEvent.Completed).usage.estimated)
    }

    @Test
    fun `passes the stored key to the adapter`() = runTest {
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))

        clientFor(testCandidate(), adapter).stream(request).toList()

        assertEquals("key-1234", adapter.lastKey)
    }

    @Test
    fun `passes credential headers to the adapter`() = runTest {
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        val credentials = object : CredentialProvider {
            override suspend fun resolve(candidate: Candidate): WireCredential = WireCredential(
                token = "token-1",
                headers = mapOf("chatgpt-account-id" to "account-1")
            )
        }

        clientFor(testCandidate(), adapter, credentials = credentials).stream(request).toList()

        assertEquals("token-1", adapter.lastKey)
        assertEquals(mapOf("chatgpt-account-id" to "account-1"), adapter.lastHeaders)
    }

    @Test
    fun `falls back to the anonymous key`() = runTest {
        val candidate = testCandidate(
            authKind = AuthKind.NONE,
            optionalKey = true,
            anonymousKey = "0000000000"
        )
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))

        clientFor(candidate, adapter, keys = FakeKeys()).stream(request).toList()

        assertEquals("0000000000", adapter.lastKey)
    }

    @Test
    fun `sends no key for a keyless provider`() = runTest {
        val candidate = testCandidate(authKind = AuthKind.NONE, optionalKey = true)
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))

        clientFor(candidate, adapter, keys = FakeKeys()).stream(request).toList()

        assertNull(adapter.lastKey)
    }

    @Test
    fun `raises an output token request below the provider floor`() = runTest {
        val candidate = testCandidate(minOutputTokens = 16)
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))

        clientFor(candidate, adapter).stream(request.copy(maxOutputTokens = 8)).toList()

        assertEquals(16, adapter.lastRequest?.maxOutputTokens)
    }

    @Test
    fun `leaves an unset output token request alone`() = runTest {
        val candidate = testCandidate(minOutputTokens = 16)
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))

        clientFor(candidate, adapter).stream(request).toList()

        assertNull(adapter.lastRequest?.maxOutputTokens)
    }

    @Test
    fun `reports a wire failure and marks the connection as needing a key`() = runTest {
        val candidate = testCandidate()
        val failure = RouteFailure(
            kind = FailureKind.TERMINAL,
            message = "unauthorized",
            statusCode = 401
        )
        val adapter = RecordingAdapter(listOf(WireEvent.Failure(failure), WireEvent.Done))
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")
        val tracker = UsageTracker(FakeClock())

        val events = clientFor(candidate, adapter, connections = connections, tracker = tracker)
            .stream(request)
            .toList()

        assertEquals(failure, (events.single() as ReplyEvent.Failed).failure)
        assertEquals("conn-1" to ConnectionHealth.NEEDS_KEY, connections.healthCalls.single())
        assertEquals(1, tracker.statsFor("conn-1").failures)
    }

    @Test
    fun `marks needs attention on a terminal failure`() = runTest {
        val candidate = testCandidate()
        val failure = RouteFailure(
            kind = FailureKind.TERMINAL,
            message = "quota exhausted",
            statusCode = 429
        )
        val adapter = RecordingAdapter(listOf(WireEvent.Failure(failure), WireEvent.Done))
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")

        clientFor(candidate, adapter, connections = connections).stream(request).toList()

        assertEquals("conn-1" to ConnectionHealth.NEEDS_ATTENTION, connections.healthCalls.single())
    }

    @Test
    fun `leaves health untouched on a retryable failure`() = runTest {
        val candidate = testCandidate()
        val failure = RouteFailure(
            kind = FailureKind.COOLDOWN,
            message = "rate limited",
            statusCode = 429
        )
        val adapter = RecordingAdapter(listOf(WireEvent.Failure(failure), WireEvent.Done))
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")

        clientFor(candidate, adapter, connections = connections).stream(request).toList()

        assertTrue(connections.healthCalls.isEmpty())
    }

    @Test
    fun `treats an empty stream as a retryable failure`() = runTest {
        val candidate = testCandidate()
        val adapter = RecordingAdapter(listOf(WireEvent.Done))
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")

        val events = clientFor(candidate, adapter, connections = connections).stream(request).toList()

        val failed = events.single() as ReplyEvent.Failed
        assertEquals(FailureKind.RETRYABLE, failed.failure.kind)
        assertTrue(connections.healthCalls.isEmpty())
    }

    @Test
    fun `retries an empty stream and reports the later success`() = runTest {
        val candidate = testCandidate()
        val adapter = SilentAdapter(silentAttempts = 1)
        val tracker = UsageTracker(FakeClock())

        val events = clientFor(candidate, adapter, tracker = tracker).stream(request).toList()

        assertEquals(2, adapter.calls)
        assertEquals(listOf("ok"), events.filterIsInstance<ReplyEvent.Delta>().map { it.text })
        assertTrue(events.last() is ReplyEvent.Completed)
        assertEquals(1, tracker.statsFor("conn-1").successes)
        assertEquals(0, tracker.statsFor("conn-1").failures)
    }

    @Test
    fun `stops retrying an empty stream at the attempt ceiling`() = runTest {
        val adapter = SilentAdapter(silentAttempts = 9)

        val events = clientFor(testCandidate(), adapter).stream(request).toList()

        assertEquals(3, adapter.calls)
        val failed = events.single() as ReplyEvent.Failed
        assertEquals(FailureKind.RETRYABLE, failed.failure.kind)
        assertEquals("no content", failed.failure.message)
    }

    @Test
    fun `keeps a reply that carries only tool calls`() = runTest {
        val adapter = ToolOnlyAdapter(ToolCall(id = "call-1", name = "list_tasks", arguments = "{}"))
        val tracker = UsageTracker(FakeClock())

        val events = clientFor(testCandidate(), adapter, tracker = tracker)
            .stream(request)
            .toList()

        assertEquals(1, adapter.calls)
        assertEquals(
            listOf("list_tasks"),
            events.filterIsInstance<ReplyEvent.ToolUse>().map { it.call.name }
        )
        assertTrue(events.last() is ReplyEvent.Completed)
        assertEquals(1, tracker.statsFor("conn-1").successes)
        assertEquals(0, tracker.statsFor("conn-1").failures)
    }

    @Test
    fun `retries a server error and reports the later success`() = runTest {
        val candidate = testCandidate()
        val adapter = FlakyAdapter(
            RouteFailure(kind = FailureKind.RETRYABLE, message = "server error", statusCode = 500),
            failures = 1
        )
        val tracker = UsageTracker(FakeClock())

        val events = clientFor(candidate, adapter, tracker = tracker).stream(request).toList()

        assertEquals(2, adapter.calls)
        assertEquals(listOf("ok"), events.filterIsInstance<ReplyEvent.Delta>().map { it.text })
        assertTrue(events.last() is ReplyEvent.Completed)
        assertEquals(1, tracker.statsFor("conn-1").successes)
        assertEquals(0, tracker.statsFor("conn-1").failures)
    }

    @Test
    fun `stops retrying at the attempt ceiling`() = runTest {
        val candidate = testCandidate()
        val failure = RouteFailure(
            kind = FailureKind.RETRYABLE,
            message = "server error",
            statusCode = 500
        )
        val adapter = FlakyAdapter(failure, failures = 9)

        val events = clientFor(candidate, adapter).stream(request).toList()

        assertEquals(3, adapter.calls)
        assertEquals(failure, (events.single() as ReplyEvent.Failed).failure)
    }

    @Test
    fun `retries a cooldown only when the wait is known`() = runTest {
        val candidate = testCandidate()
        val burst = FlakyAdapter(
            RouteFailure(
                kind = FailureKind.COOLDOWN,
                message = "burst limited",
                statusCode = 429,
                retryAfterMillis = 2_000L
            ),
            failures = 1
        )
        val blind = FlakyAdapter(
            RouteFailure(kind = FailureKind.COOLDOWN, message = "rate limited", statusCode = 429),
            failures = 1
        )

        val retried = clientFor(candidate, burst).stream(request).toList()
        val abandoned = clientFor(candidate, blind).stream(request).toList()

        assertEquals(2, burst.calls)
        assertTrue(retried.last() is ReplyEvent.Completed)
        assertEquals(1, blind.calls)
        assertTrue(abandoned.single() is ReplyEvent.Failed)
    }

    @Test
    fun `marks the connection ready after a success`() = runTest {
        val candidate = testCandidate()
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")

        clientFor(candidate, adapter, connections = connections).stream(request).toList()

        assertEquals("conn-1" to ConnectionHealth.READY, connections.healthCalls.single())
    }

    @Test
    fun `fails when no adapter handles the wire format`() = runTest {
        val events = clientFor(testCandidate(), null).stream(request).toList()

        assertEquals(FailureKind.TERMINAL, (events.single() as ReplyEvent.Failed).failure.kind)
    }

    @Test
    fun `passes an unavailable resolution straight through`() = runTest {
        val client = AiClient(
            activeProvider = ActiveProvider(
                FakeConnectionSource(),
                ProviderCatalog.empty(),
                FakeKeys()
            ),
            connections = FakeConnectionSource(),
            credentials = KeySourceCredentials(FakeKeys()),
            adapters = emptyMap(),
            usageTracker = UsageTracker(FakeClock()),
            logStore = LogStore(FakeClock()),
            clock = FakeClock()
        )

        val events = client.stream(request).toList()

        assertEquals(
            ActiveProviderProblem.NO_CONNECTION,
            (events.single() as ReplyEvent.Unavailable).problem
        )
    }

    @Test
    fun `records first token latency once`() = runTest {
        val candidate = testCandidate()
        val clock = FakeClock(1_000L)
        val tracker = UsageTracker(FakeClock())
        val adapter = object : ChatAdapter {
            override fun stream(
                candidate: Candidate,
                apiKey: String?,
                request: ChatRequest,
                authHeaders: Map<String, String>
            ): Flow<WireEvent> = flow {
                clock.advance(40L)
                emit(WireEvent.Delta("a"))
                clock.advance(60L)
                emit(WireEvent.Delta("b"))
                emit(WireEvent.Done)
            }
        }

        val events = clientFor(candidate, adapter, tracker = tracker, clock = clock)
            .stream(request)
            .toList()

        assertEquals(40L, (events.first() as ReplyEvent.Started).detail.firstTokenMillis)
        val completed = events.last() as ReplyEvent.Completed
        assertEquals(40L, completed.detail.firstTokenMillis)
        assertEquals(100L, completed.detail.totalMillis)
        assertEquals(40L, tracker.statsFor("conn-1").p95FirstTokenMillis)
    }

    @Test
    fun `images only reach a model that can see`() = runTest {
        val picture = ChatImage("image/jpeg", "QUJD")
        val illustrated = ChatRequest(
            systemPrompt = "you are bayu",
            turns = listOf(ChatTurn(ChatRole.USER, "apa ini", listOf(picture)))
        )

        val blindAdapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        clientFor(testCandidate(), blindAdapter).stream(illustrated).toList()

        assertEquals(
            listOf("apa ini"),
            blindAdapter.lastRequest?.turns?.map { it.content }
        )
        assertTrue(blindAdapter.lastRequest?.turns?.single()?.images.orEmpty().isEmpty())

        val seeingAdapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        val events = clientFor(testCandidate(vision = true), seeingAdapter)
            .stream(illustrated)
            .toList()

        assertEquals(listOf(picture), seeingAdapter.lastRequest?.turns?.single()?.images)

        val usage = (events.last() as ReplyEvent.Completed).usage
        assertTrue(usage.estimated)
        assertTrue(usage.inputTokens > ChatImage.TOKEN_COST)
    }

    @Test
    fun `a resolved project reaches the adapter`() = runTest {
        val candidate = testCandidate()
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        val projects = StubProjectResolver("project-1")

        val events = clientFor(candidate, adapter, projects = projects).stream(request).toList()

        assertEquals(1, projects.calls)
        assertEquals("project-1", adapter.lastCandidate?.connection?.projectId)
        assertTrue(events.last() is ReplyEvent.Completed)
    }

    @Test
    fun `a failed project bootstrap fails the reply without calling the adapter`() = runTest {
        val candidate = testCandidate()
        val adapter = RecordingAdapter(listOf(WireEvent.Delta("ok"), WireEvent.Done))
        val tracker = UsageTracker(FakeClock())
        val connections = FakeConnectionSource(listOf(candidate.connection), "conn-1")

        val events = clientFor(
            candidate,
            adapter,
            connections = connections,
            tracker = tracker,
            projects = StubProjectResolver(null)
        ).stream(request).toList()

        val failed = events.single() as ReplyEvent.Failed
        assertTrue(failed.failure.needsSetup)
        assertEquals(FailureKind.TERMINAL, failed.failure.kind)
        assertNull(adapter.lastCandidate)
        assertEquals(1, tracker.statsFor("conn-1").failures)
        assertEquals(
            "conn-1" to ConnectionHealth.NEEDS_ATTENTION,
            connections.healthCalls.single()
        )
    }
}
