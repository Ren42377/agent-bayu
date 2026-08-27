package dev.agentbayu.app.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun controller(engine: AgentEngine, errorReply: String = "error"): ChatController {
        return ChatController(
            repository = repository,
            engine = engine,
            errorReply = errorReply,
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + dispatcher
            )
        )
    }

    @Test
    fun sendAppendsUserMessageThenAgentReply() = runTest {
        val chat = controller(EchoAgentEngine("reply: %1\$s", "unused", 0L))
        chat.send(" hello ")
        dispatcher.scheduler.advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(listOf(MessageAuthor.USER, MessageAuthor.AGENT), messages.map { it.author })
        assertEquals("hello", messages[0].text)
        assertEquals("reply: hello", messages[1].text)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun blankInputIsIgnored() = runTest {
        val chat = controller(EchoAgentEngine("reply", "unused", 0L))
        chat.send("   ")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.messages.value.isEmpty())
    }

    @Test
    fun engineFailureProducesErrorReply() = runTest {
        val failingEngine = object : AgentEngine {
            override suspend fun reply(prompt: String, screenContext: String?): String {
                throw IllegalStateException("backend down")
            }
        }
        val chat = controller(failingEngine, errorReply = "fallback")
        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals("fallback", messages[1].text)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun clearRemovesAllMessages() = runTest {
        val chat = controller(EchoAgentEngine("reply", "unused", 0L))
        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()
        chat.clear()
        assertTrue(repository.messages.value.isEmpty())
    }
}
