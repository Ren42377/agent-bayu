package dev.agentbayu.app.domain

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.adapter.ChatImage
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ContextBuilder(
    private val systemPrompt: String,
    private val screenContextTemplate: String,
    private val momentTemplate: String = "",
    private val customPrompt: () -> String = { "" },
    private val historyLimit: Int = DEFAULT_HISTORY_LIMIT,
    private val temperature: Double? = DEFAULT_TEMPERATURE,
    private val images: (List<MessageAttachment>) -> List<ChatImage> = { emptyList() },
    private val clock: Clock = RealClock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() }
) {

    fun build(request: AgentRequest): ChatRequest {
        val prompt = request.prompt.trim()
        val screenContext = request.screenContext?.trim()
        val system = StringBuilder(systemPrompt)
        customPrompt().trim().takeIf { it.isNotEmpty() }?.let { ownerInstructions ->
            system.append("\n\nOwner instructions:\n").append(ownerInstructions)
        }
        if (momentTemplate.isNotEmpty()) {
            system.append("\n\n").append(momentTemplate.format(momentText()))
        }
        if (!screenContext.isNullOrEmpty()) {
            system.append("\n\n").append(screenContextTemplate.format(screenContext))
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
            systemPrompt = system.toString(),
            turns = recent + current,
            maxOutputTokens = null,
            temperature = temperature
        )
    }

    private fun momentText(): String = MOMENT_FORMATTER.format(
        Instant.ofEpochMilli(clock.nowMillis()).atZone(zone())
    )

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 12
        const val DEFAULT_TEMPERATURE = 0.7
        const val IMAGE_HISTORY_LIMIT = 4
        private val MOMENT_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm zzz", Locale.US)
    }
}
