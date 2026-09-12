package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.LogStore
import dev.agentbayu.app.ai.ReplyDetail
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

    private fun controller(
        engine: AgentEngine,
        errorReply: String = "error",
        discardAttachments: (Set<String>) -> Unit = {}
    ): ChatController =
        ChatController(
            repository = repository,
            engine = engine,
            errorReply = errorReply,
            logStore = LogStore(),
            scope = CoroutineScope(SupervisorJob() + dispatcher),
            discardAttachments = discardAttachments
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
                    AgentEvent.Completed(detail(), TokenUsage(12, 34, 0.5, true))
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
        assertNotNull(messages[1].detail)
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
    fun detailEventAttachesBeforeCompletion() = runTest {
        val chat = controller(
            engine {
                listOf(AgentEvent.Detail(detail()), AgentEvent.Delta("ok"))
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val agent = repository.messages.value.last()
        assertEquals("kilocode", agent.detail?.providerId)
        assertEquals(AuthKind.NONE, agent.detail?.authKind)
        assertNull(agent.usage)
    }

    @Test
    fun failureEventReplacesEmptyReply() = runTest {
        val chat = controller(engine { listOf(AgentEvent.Failed("penyedia menolak permintaan")) })

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("penyedia menolak permintaan", repository.messages.value.last().text)
        assertFalse(chat.isResponding.value)
    }

    @Test
    fun failureAfterFirstTokenKeepsStreamedTextAndShowsError() = runTest {
        val chat = controller(
            engine {
                listOf(AgentEvent.Delta("separuh"), AgentEvent.Failed("putus"))
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("separuh\n\nputus", repository.messages.value.last().text)
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
    fun anImageAloneIsEnoughToSend() = runTest {
        val picture = MessageAttachment(id = "img-1", mimeType = "image/jpeg")
        var sent: List<MessageAttachment> = emptyList()
        val chat = controller(
            engine { request ->
                sent = request.attachments
                listOf(AgentEvent.Delta("ok"))
            }
        )

        chat.send("   ", attachments = listOf(picture))
        dispatcher.scheduler.advanceUntilIdle()

        val messages = repository.messages.value
        assertEquals(2, messages.size)
        assertEquals("", messages.first().text)
        assertEquals(listOf(picture), messages.first().attachments)
        assertEquals(listOf(picture), sent)
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
    fun thinkingClosesIntoItsOwnSegmentBeforeTheProse() = runTest {
        val chat = controller(
            engine {
                listOf(
                    AgentEvent.Thinking("menimbang "),
                    AgentEvent.Thinking("pilihan"),
                    AgentEvent.Delta("Jawabannya ini."),
                    AgentEvent.Completed(detail(), TokenUsage(1, 1))
                )
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val agent = repository.messages.value.last()
        assertEquals("Jawabannya ini.", agent.text)
        val thinking = agent.segments.first() as MessageSegment.Thinking
        assertEquals("menimbang pilihan", thinking.text)
        assertTrue(thinking.done)
        assertEquals(MessageSegment.Prose("Jawabannya ini."), agent.segments.last())
    }

    @Test
    fun toolActivitySitsBetweenTheProseAroundIt() = runTest {
        val chat = controller(
            engine {
                listOf(
                    AgentEvent.Delta("Aku cek."),
                    AgentEvent.ToolStarted("read_file", "read_file {\"path\":\"a.txt\"}"),
                    AgentEvent.ToolFinished("read_file", true),
                    AgentEvent.Delta(" Sudah.")
                )
            }
        )

        chat.send("hi")
        dispatcher.scheduler.advanceUntilIdle()

        val agent = repository.messages.value.last()
        assertEquals("Aku cek. Sudah.", agent.text)
        assertEquals(
            listOf(
                MessageSegment.Prose("Aku cek."),
                MessageSegment.Tool(
                    name = "read_file",
                    label = "read_file {\"path\":\"a.txt\"}",
                    running = false,
                    ok = true
                ),
                MessageSegment.Prose(" Sudah.")
            ),
            agent.segments
        )
    }

    @Test
    fun editReplacesTheSelectedTurnAndDropsTheFollowingBranch() = runTest {
        val requests = mutableListOf<AgentRequest>()
        val chat = controller(
            engine { request ->
                requests += request
                listOf(AgentEvent.Delta("new reply"))
            }
        )
        val earlierUser = repository.append(MessageAuthor.USER, "earlier")
        val earlierAgent = repository.append(MessageAuthor.AGENT, "earlier reply")
        val edited = repository.append(MessageAuthor.USER, "old prompt")
        repository.append(MessageAuthor.AGENT, "old reply")
        repository.append(MessageAuthor.USER, "later prompt")
        val replacement = MessageAttachment(id = "new-image", mimeType = "image/png")

        chat.restartFrom(edited, " updated prompt ", listOf(replacement))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("earlier", "earlier reply", "updated prompt", "new reply"),
            repository.messages.value.map { it.text }
        )
        assertEquals(listOf(earlierUser, earlierAgent), requests.single().history)
        assertEquals("updated prompt", requests.single().prompt)
        assertEquals(listOf(replacement), requests.single().attachments)
    }

    @Test
    fun regenerateReplacesTheSelectedReplyAndDropsTheFollowingBranch() = runTest {
        var request: AgentRequest? = null
        val chat = controller(
            engine { seen ->
                request = seen
                listOf(AgentEvent.Delta("replacement reply"))
            }
        )
        val earlierUser = repository.append(MessageAuthor.USER, "earlier")
        val earlierAgent = repository.append(MessageAuthor.AGENT, "earlier reply")
        val picture = MessageAttachment(id = "image", mimeType = "image/jpeg")
        val prompt = repository.append(
            MessageAuthor.USER,
            "retry this",
            attachments = listOf(picture)
        )
        val reply = repository.append(MessageAuthor.AGENT, "old reply")
        repository.append(MessageAuthor.USER, "later prompt")
        repository.append(MessageAuthor.AGENT, "later reply")

        chat.regenerate(reply, prompt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf("earlier", "earlier reply", "retry this", "replacement reply"),
            repository.messages.value.map { it.text }
        )
        assertEquals(prompt, repository.messages.value[2])
        assertEquals(listOf(earlierUser, earlierAgent), request?.history)
        assertEquals("retry this", request?.prompt)
        assertEquals(listOf(picture), request?.attachments)
    }

    @Test
    fun editProtectsAttachmentsStillReferencedEarlierInTheConversation() = runTest {
        val discarded = mutableSetOf<String>()
        val chat = controller(
            engine = engine { listOf(AgentEvent.Delta("new reply")) },
            discardAttachments = discarded::addAll
        )
        val shared = MessageAttachment(id = "shared", mimeType = "image/jpeg")
        repository.append(MessageAuthor.USER, "earlier", attachments = listOf(shared))
        val edited = repository.append(
            MessageAuthor.USER,
            "old",
            attachments = listOf(shared)
        )

        chat.restartFrom(edited, "updated", emptyList())
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(discarded.isEmpty())
    }

    @Test
    fun editDiscardsAttachmentsOnlyFromTheRemovedBranch() = runTest {
        val discarded = mutableSetOf<String>()
        val chat = controller(
            engine = engine { listOf(AgentEvent.Delta("new reply")) },
            discardAttachments = discarded::addAll
        )
        val earlier = MessageAttachment(id = "earlier", mimeType = "image/jpeg")
        val replacement = MessageAttachment(id = "kept", mimeType = "image/jpeg")
        val removed = MessageAttachment(id = "removed", mimeType = "image/jpeg")
        repository.append(MessageAuthor.USER, "earlier", attachments = listOf(earlier))
        val edited = repository.append(
            MessageAuthor.USER,
            "old",
            attachments = listOf(replacement, removed)
        )
        repository.append(MessageAuthor.AGENT, "old reply")

        chat.restartFrom(edited, "updated", listOf(replacement))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("removed"), discarded)
    }

    @Test
    fun regenerateDiscardsAttachmentsOnlyAfterTheRepeatedPrompt() = runTest {
        val discarded = mutableSetOf<String>()
        val chat = controller(
            engine = engine { listOf(AgentEvent.Delta("new reply")) },
            discardAttachments = discarded::addAll
        )
        val promptImage = MessageAttachment(id = "prompt", mimeType = "image/jpeg")
        val laterImage = MessageAttachment(id = "later", mimeType = "image/jpeg")
        val prompt = repository.append(
            MessageAuthor.USER,
            "retry",
            attachments = listOf(promptImage)
        )
        val reply = repository.append(MessageAuthor.AGENT, "old reply")
        repository.append(MessageAuthor.USER, "later", attachments = listOf(laterImage))

        chat.regenerate(reply, prompt)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("later"), discarded)
    }

    @Test
    fun regenerateIgnoresMessagesThatDoNotFormAUserReplyPair() = runTest {
        var requests = 0
        val chat = controller(
            engine {
                requests += 1
                listOf(AgentEvent.Delta("unexpected"))
            }
        )
        val user = repository.append(MessageAuthor.USER, "prompt")
        val nextUser = repository.append(MessageAuthor.USER, "not a reply")

        chat.regenerate(nextUser, user)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, requests)
        assertEquals(listOf("prompt", "not a reply"), repository.messages.value.map { it.text })
    }

    @Test
    fun editingAStaleMessageDoesNotCancelTheActiveReply() = runTest {
        val slow = object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                emit(AgentEvent.Delta("active"))
                delay(60_000L)
            }
        }
        val chat = controller(slow)
        val stale = ChatMessage(id = 999L, author = MessageAuthor.USER, text = "stale")

        chat.send("current")
        dispatcher.scheduler.advanceTimeBy(100L)
        val accepted = chat.restartFrom(stale, "replacement")

        assertFalse(accepted)
        assertTrue(chat.isResponding.value)
        assertEquals(listOf("current", "active"), repository.messages.value.map { it.text })
    }

    @Test
    fun sendsAreIgnoredWhileTheSessionIsSwitching() = runTest {
        var requests = 0
        val chat = controller(
            engine {
                requests += 1
                listOf(AgentEvent.Delta("unexpected"))
            }
        )

        chat.setSessionSwitching(true)
        chat.send("blocked")
        dispatcher.scheduler.advanceUntilIdle()
        chat.setSessionSwitching(false)
        chat.send("allowed")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, requests)
        assertEquals(listOf("allowed", "unexpected"), repository.messages.value.map { it.text })
    }

    @Test
    fun cancelReleasesTheStopButtonBeforeTheChainUnwinds() = runTest {
        val stubborn = object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                emit(AgentEvent.Delta("mulai"))
                delay(60_000L)
            }
        }
        val chat = controller(stubborn)

        chat.send("hi")
        dispatcher.scheduler.advanceTimeBy(100L)
        assertTrue(chat.isResponding.value)

        chat.cancel()

        assertFalse(chat.isResponding.value)
        assertFalse(repository.messages.value.last().streaming)
    }

    @Test
    fun cancelDoesNotClearAFreshSendThatFollowsIt() = runTest {
        val slow = object : AgentEngine {
            override fun reply(request: AgentRequest): Flow<AgentEvent> = flow {
                emit(AgentEvent.Delta("satu"))
                delay(60_000L)
            }
        }
        val chat = controller(slow)

        chat.send("pertama")
        dispatcher.scheduler.advanceTimeBy(100L)
        chat.cancel()
        chat.send("kedua")
        dispatcher.scheduler.advanceTimeBy(100L)

        assertTrue(chat.isResponding.value)
    }

    private fun detail(): ReplyDetail = ReplyDetail(
        providerId = "kilocode",
        providerLabel = "Kilo Code",
        model = "minimax/minimax-m3:free",
        connectionId = "conn-1",
        connectionLabel = "Kilo Code",
        authKind = AuthKind.NONE
    )
}
