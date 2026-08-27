package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.TokenUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatControllerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ConversationRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = ConversationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun controller(engine: AgentEngine, errorReply: String = "error"): ChatController =
        ChatController(
            repository = repository,
            engine = engine,
            errorReply = errorReply,
            scope = CoroutineScope(SupervisorJob() + dispatcher)
        )

    private fun engine(block: suspend (AgentRequest) -> List<AgentEvent>): AgentEngine =
        object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                block(request).forEach { event -> emit(event) }
            }
        }

    @Test
    fun deltasAreAppendedToOneAgentMessage() = runTest {
        val chat = controller(
            engine {
                listOf(
                    AgentEvent.Delta("Hal"),
                    AgentEvent.Delta("lo "),
                    AgentEvent.Delta("Bayu"),
                    AgentEvent.Completed(decision(), TokenUsage(12, 34, 0.5, true))
                )
            }
        )

        chat.send(" apa kabar ")
        dispatcher.scheduler.advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(listOf(MessageAuthor.USER, MessageAuthor.AGENT), messages.map { it.author })
        assertEquals("apa kabar", messages[0].text)
        assertEquals("Hallo Bayu", messages[1].text)
        assertFalse(messages[1].streaming)
        assertNotNull(messages[1].route)
        assertEquals(46, messages[1].usage?.totalTokens)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun promptCarriesHistoryWithoutThePlaceholder() = runTest {
        var seen: AgentRequest? = null
        val chat = controller(
            engine { request ->
                seen = request
                listOf(AgentEvent.Delta("ok"))
            }
        )

        repository.append(MessageAuthor.USER, "pesan lama")
        repository.append(MessageAuthor.AGENT, "jawaban lama")
        chat.send("pesan baru")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("pesan baru", seen?.prompt)
        assertEquals(listOf("pesan lama", "jawaban lama"), seen?.history?.map { it.text })
    }

    @Test
    fun routeEventAttachesBeforeCompletion() = runTest {
        val chat = controller(
            engine {
                listOf(AgentEvent.Route(decision()), AgentEvent.Delta("ok"))
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val agent = repository.messages.value.last()
        assertEquals("groq", agent.route?.providerId)
        assertNull(agent.usage)
    }

    @Test
    fun failureEventReplacesEmptyReply() = runTest {
        val chat = controller(engine { listOf(AgentEvent.Failed("semua koneksi gagal")) })

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("semua koneksi gagal", repository.messages.value.last().text)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun failureAfterFirstTokenKeepsStreamedText() = runTest {
        val chat = controller(
            engine {
                listOf(AgentEvent.Delta("separuh"), AgentEvent.Failed("putus"))
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("separuh", repository.messages.value.last().text)
    }

    @Test
    fun engineErrorProducesErrorReply() = runTest {
        val failing = object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                throw IllegalStateException("backend down")
            }
        }
        val chat = controller(failing, errorReply = "fallback")

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals("fallback", messages[1].text)
        assertFalse(messages[1].streaming)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun blankInputIsIgnored() = runTest {
        val chat = controller(engine { listOf(AgentEvent.Delta("ok")) })
        chat.send("   ")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun secondSendIsIgnoredWhileResponding() = runTest {
        val chat = controller(
            engine {
                delay(1_000L)
                listOf(AgentEvent.Delta("ok"))
            }
        )

        chat.send("pertama")
        dispatcher.scheduler.advanceTimeBy(1L)
        chat.send("kedua")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("pertama", "ok"), repository.messages.value.map { it.text })
    }

    @Test
    fun cancelStopsStreamingAndKeepsPartialText() = runTest {
        val slow = object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                emit(AgentEvent.Delta("seten"))
                delay(10_000L)
                emit(AgentEvent.Delta("gah"))
            }
        }
        val chat = controller(slow)

        chat.send("hi")
        dispatcher.scheduler.advanceTimeBy(100L)
        chat.cancel()
        dispatcher.scheduler.advanceUntilIdle()

        val agent = repository.messages.value.last()
        assertEquals("seten", agent.text)
        assertFalse(agent.streaming)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun clearRemovesAllMessages() = runTest {
        val chat = controller(engine { listOf(AgentEvent.Delta("ok")) })
        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()
        chat.clear()
        assertTrue(repository.messages.value.isEmpty())
    }

    private fun decision(): RouteDecision = RouteDecision(
        channel = "auto",
        strategy = "priority",
        providerId = "groq",
        providerLabel = "Groq",
        model = "llama-3.3-70b-versatile",
        connectionId = "conn-1",
        connectionLabel = "Groq utama",
        tier = ProviderTier.FREE,
        attempt = 1,
        candidatesConsidered = 2
    )
}
