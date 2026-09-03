package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTest {

    private fun model(contextLength: Int, maxOutputTokens: Int): ModelEntry = ModelEntry(
        id = "model-a",
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens
    )

    private fun request(vararg contents: String): ChatRequest = ChatRequest(
        systemPrompt = "You are Bayu.",
        turns = contents.mapIndexed { index, content ->
            ChatTurn(
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                content = content
            )
        }
    )

    @Test
    fun `the budget reserves room for the output and keeps a floor`() {
        assertEquals(28_800, inputTokenBudget(model(64_000, 32_000)))
        assertEquals(14_745, inputTokenBudget(model(32_768, 999_999)))
        assertEquals(MIN_INPUT_BUDGET, inputTokenBudget(model(64, 64)))
    }

    @Test
    fun `a big window keeps the whole conversation`() {
        val long = "x".repeat(40_000)
        val turns = fitToContext(request(long, long, "singkat", "sekarang"), model(1_048_576, 65_536))

        assertEquals(4, turns.size)
        assertEquals("sekarang", turns.last().content)
    }

    @Test
    fun `a small window drops the oldest turns first`() {
        val long = "x".repeat(40_000)
        val turns = fitToContext(request(long, long, "singkat", "sekarang"), model(8_192, 4_096))

        assertEquals(listOf("singkat", "sekarang"), turns.map { it.content })
    }

    @Test
    fun `an oversized prompt still goes out alone`() {
        val turns = fitToContext(
            request("lama", "y".repeat(400_000)),
            model(8_192, 4_096)
        )

        assertEquals(1, turns.size)
        assertTrue(turns.single().content.startsWith("y"))
    }

    @Test
    fun `an empty conversation stays empty`() {
        assertEquals(emptyList<ChatTurn>(), fitToContext(request(), model(8_192, 4_096)))
    }

    @Test
    fun `every image adds a fixed cost on top of the text`() {
        val plain = ChatTurn(ChatRole.USER, "apa ini")
        val withImages = plain.copy(images = listOf(image(), image()))

        assertEquals(
            turnTokenCost(plain) + 2 * ChatImage.TOKEN_COST,
            turnTokenCost(withImages)
        )
    }

    @Test
    fun `images push older turns out of a small window`() {
        val heavy = ChatTurn(ChatRole.USER, "gambar", List(8) { image() })
        val turns = fitToContext(
            ChatRequest(turns = listOf(ChatTurn(ChatRole.USER, "lama"), heavy)),
            model(4_096, 1_024)
        )

        assertEquals(listOf("gambar"), turns.map { it.content })
    }

    private fun image(): ChatImage = ChatImage("image/jpeg", "QUJD")
}
