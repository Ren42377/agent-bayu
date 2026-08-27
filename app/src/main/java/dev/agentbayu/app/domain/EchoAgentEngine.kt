package dev.agentbayu.app.domain

import kotlinx.coroutines.delay

class EchoAgentEngine(
    private val replyTemplate: String,
    private val contextReplyTemplate: String,
    private val thinkingDelayMillis: Long = DEFAULT_THINKING_DELAY_MILLIS
) : AgentEngine {

    override suspend fun reply(prompt: String, screenContext: String?): String {
        delay(thinkingDelayMillis)
        val trimmedPrompt = prompt.trim()
        val trimmedContext = screenContext?.trim()
        return if (trimmedContext.isNullOrEmpty()) {
            replyTemplate.format(trimmedPrompt)
        } else {
            contextReplyTemplate.format(trimmedPrompt, trimmedContext.take(MAX_CONTEXT_PREVIEW))
        }
    }

    private companion object {
        const val DEFAULT_THINKING_DELAY_MILLIS = 600L
        const val MAX_CONTEXT_PREVIEW = 160
    }
}
