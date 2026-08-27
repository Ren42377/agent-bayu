package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.TokenUsage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn

class ContextBuilder(
    private val systemPrompt: String,
    private val screenContextTemplate: String,
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val tokenBudget: Int = DEFAULT_TOKEN_BUDGET,
    private val temperature: Double? = DEFAULT_TEMPERATURE
) {

    fun build(request: AgentRequest): ChatRequest {
        val prompt = request.prompt.trim()
        val screenContext = request.screenContext?.trim()
        val system = if (screenContext.isNullOrEmpty()) {
            systemPrompt
        } else {
            systemPrompt + "\n\n" + screenContextTemplate.format(screenContext)
        }

        val recent = request.history
            .filter { it.text.isNotBlank() }
            .takeLast(historyLimit)
            .map { message ->
                ChatTurn(
                    role = if (message.author == MessageAuthor.USER) ChatRole.USER else ChatRole.ASSISTANT,
                    content = message.text
                )
            }

        val current = ChatTurn(ChatRole.USER, prompt)
        val turns = fitBudget(system, recent, current)

        return ChatRequest(
            systemPrompt = system,
            turns = turns,
            maxOutputTokens = null,
            temperature = temperature
        )
    }

    private fun fitBudget(
        system: String,
        history: List<ChatTurn>,
        current: ChatTurn
    ): List<ChatTurn> {
        val fixed = TokenUsage.estimateTokens(system) + TokenUsage.estimateTokens(current.content)
        val window = ArrayDeque(history)
        var used = fixed + window.sumOf { TokenUsage.estimateTokens(it.content) }
        while (window.isNotEmpty() && used > tokenBudget) {
            val dropped = window.removeFirst()
            used -= TokenUsage.estimateTokens(dropped.content)
        }
        return window.toList() + current
    }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 12
        const val DEFAULT_TOKEN_BUDGET = 6_000
        const val DEFAULT_TEMPERATURE = 0.7
    }
}
