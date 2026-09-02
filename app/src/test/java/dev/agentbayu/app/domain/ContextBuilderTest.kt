package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.adapter.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {

    private val systemPrompt = "You are Bayu."
    private val screenTemplate = "Screen: %s"

    private fun builder(
        historyLimit: Int = ContextBuilder.DEFAULT_HISTORY_LIMIT
    ): ContextBuilder = ContextBuilder(
        systemPrompt = systemPrompt,
        screenContextTemplate = screenTemplate,
        historyLimit = historyLimit
    )

    private var nextId = 0L

    private fun message(author: MessageAuthor, text: String): ChatMessage =
        ChatMessage(id = nextId++, author = author, text = text)

    private fun history(size: Int): List<ChatMessage> = (1..size).map { index ->
        message(
            if (index % 2 == 1) MessageAuthor.USER else MessageAuthor.AGENT,
            "turn " + index
        )
    }

    @Test
    fun promptBecomesTheLastUserTurn() {
        val request = builder().build(AgentRequest(prompt = "  halo  "))
        assertEquals(1, request.turns.size)
        assertEquals(ChatRole.USER, request.turns.last().role)
        assertEquals("halo", request.turns.last().content)
        assertEquals(systemPrompt, request.systemPrompt)
        assertEquals(ContextBuilder.DEFAULT_TEMPERATURE, request.temperature ?: 0.0, 0.0001)
        assertNull(request.maxOutputTokens)
    }

    @Test
    fun historyKeepsAuthorRoles() {
        val request = builder().build(
            AgentRequest(
                prompt = "lanjut",
                history = listOf(
                    message(MessageAuthor.USER, "pertama"),
                    message(MessageAuthor.AGENT, "jawaban")
                )
            )
        )
        assertEquals(
            listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER),
            request.turns.map { it.role }
        )
        assertEquals(listOf("pertama", "jawaban", "lanjut"), request.turns.map { it.content })
    }

    @Test
    fun blankHistoryEntriesAreDropped() {
        val request = builder().build(
            AgentRequest(
                prompt = "lanjut",
                history = listOf(
                    message(MessageAuthor.USER, "isi"),
                    message(MessageAuthor.AGENT, "   "),
                    message(MessageAuthor.AGENT, "")
                )
            )
        )
        assertEquals(listOf("isi", "lanjut"), request.turns.map { it.content })
    }

    @Test
    fun onlyTheNewestHistoryEntriesSurvive() {
        val request = builder(historyLimit = 4).build(
            AgentRequest(prompt = "sekarang", history = history(10))
        )
        assertEquals(
            listOf("turn 7", "turn 8", "turn 9", "turn 10", "sekarang"),
            request.turns.map { it.content }
        )
    }

    @Test
    fun screenContextIsAppendedToTheSystemPrompt() {
        val request = builder().build(
            AgentRequest(prompt = "apa ini", screenContext = "  Gmail inbox  ")
        )
        assertEquals(systemPrompt + "\n\nScreen: Gmail inbox", request.systemPrompt)
    }

    @Test
    fun blankScreenContextIsIgnored() {
        assertEquals(
            systemPrompt,
            builder().build(AgentRequest(prompt = "hi", screenContext = "   ")).systemPrompt
        )
        assertEquals(
            systemPrompt,
            builder().build(AgentRequest(prompt = "hi", screenContext = null)).systemPrompt
        )
    }

    @Test
    fun longTurnsAreLeftForTheModelWindowToTrim() {
        val long = "x".repeat(40_000)
        val request = builder().build(
            AgentRequest(
                prompt = "sekarang",
                history = listOf(
                    message(MessageAuthor.USER, long),
                    message(MessageAuthor.AGENT, long),
                    message(MessageAuthor.USER, "singkat")
                )
            )
        )
        assertEquals(4, request.turns.size)
        assertTrue(request.turns.first().content == long)
    }
}
