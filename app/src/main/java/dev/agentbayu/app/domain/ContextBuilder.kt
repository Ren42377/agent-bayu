package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn

class ContextBuilder(
    private val systemPrompt: String,
    private val screenContextTemplate: String,
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val temperature: Double? = DEFAULT_TEMPERATURE,
    private val images: (List<MessageAttachment>) -> List<ChatImage> = { emptyList() }
) {

    fun build(request: AgentRequest): ChatRequest {
        val prompt = request.prompt.trim()
        val screenContext = request.screenContext?.trim()
        val system = if (screenContext.isNullOrEmpty()) {
            systemPrompt
        } else {
            systemPrompt + "\n\n" + screenContextTemplate.format(screenContext)
        }

        val history = request.history
            .filter { it.text.isNotBlank() || it.attachments.isNotEmpty() }
            .takeLast(historyLimit)
        val imageStart = (history.size - IMAGE_HISTORY_LIMIT).coerceAtLeast(0)
        val recent = history.mapIndexed { index, message ->
            ChatTurn(
                role = if (message.author == MessageAuthor.USER) ChatRole.USER else ChatRole.ASSISTANT,
                content = message.text,
                images = if (index >= imageStart) images(message.attachments) else emptyList()
            )
        }

        val current = ChatTurn(ChatRole.USER, prompt, images(request.attachments))

        return ChatRequest(
            systemPrompt = system,
            turns = recent + current,
            maxOutputTokens = null,
            temperature = temperature
        )
    }

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 12
        const val DEFAULT_TEMPERATURE = 0.7
        const val IMAGE_HISTORY_LIMIT = 4
    }
}
