package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.WireEvent
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

        override fun stream(
            candidate: Candidate,
            apiKey: String?,
            request: ChatRequest
        ): Flow<WireEvent> = flow {
            lastKey = apiKey
            lastRequest = request
            events.forEach { emit(it) }
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
        connections: FakeConnectionSource = FakeConnectionSource(
            listOf(candidate.connection),
            candidate.connection.id
        ),
        tracker: UsageTracker = UsageTracker(FakeClock()),
        clock: Clock = FakeClock(1_000L)
    ): AiClient = AiClient(
        activeProvider = ActiveProvider(
            connections,
            ProviderCatalog(listOf(candidate.provider)),
            keys
        ),
        connections = connections,
        keys = keys,
        adapters = adapter?.let { mapOf(candidate.provider.wireFormat to it) } ?: emptyMap(),
        usageTracker = tracker,
        clock = clock
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
            keys = FakeKeys(),
            adapters = emptyMap(),
            usageTracker = UsageTracker(FakeClock()),
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
                request: ChatRequest
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
}
