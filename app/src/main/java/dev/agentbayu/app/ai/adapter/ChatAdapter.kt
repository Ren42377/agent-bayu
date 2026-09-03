package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.ReasoningEffort
import kotlinx.coroutines.flow.Flow

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

data class ChatTurn(
    val role: ChatRole,
    val content: String,
    val images: List<ChatImage> = emptyList()
)

data class ChatRequest(
    val systemPrompt: String? = null,
    val turns: List<ChatTurn> = emptyList(),
    val maxOutputTokens: Int? = null,
    val temperature: Double? = null,
    val effort: ReasoningEffort? = null
)

interface ChatAdapter {
    fun stream(
        candidate: Candidate,
        apiKey: String?,
        request: ChatRequest,
        authHeaders: Map<String, String> = emptyMap()
    ): Flow<WireEvent>
}

object WireParams {
    const val MAX_TOKENS = "max_tokens"
    const val TEMPERATURE = "temperature"
    const val STREAM_OPTIONS = "stream_options"
    const val REASONING = "reasoning"

    fun supports(candidate: Candidate, param: String): Boolean =
        !candidate.provider.unsupportedParams.contains(param) &&
            !candidate.model.unsupportedParams.contains(param)
}
