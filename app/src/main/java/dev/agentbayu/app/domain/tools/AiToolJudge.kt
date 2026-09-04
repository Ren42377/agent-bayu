package dev.agentbayu.app.domain.tools

import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.ReplyEvent
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.booleanField
import dev.agentbayu.app.ai.adapter.parseJsonObject

class AiToolJudge(private val client: AiClient) : ToolApprovalJudge {

    override suspend fun review(
        request: ToolApprovalRequest,
        userIntent: String
    ): ToolVerdict? {
        val answer = StringBuilder()
        var usable = true
        client.stream(requestFor(request, userIntent), countRequest = false).collect { event ->
            when (event) {
                is ReplyEvent.Delta -> answer.append(event.text)
                is ReplyEvent.Failed -> usable = false
                is ReplyEvent.Unavailable -> usable = false
                else -> Unit
            }
        }
        if (!usable) return null
        return verdictOf(answer.toString())
    }

    private fun requestFor(request: ToolApprovalRequest, userIntent: String): ChatRequest =
        ChatRequest(
            systemPrompt = SYSTEM_PROMPT,
            turns = listOf(ChatTurn(ChatRole.USER, briefOf(request, userIntent))),
            maxOutputTokens = MAX_OUTPUT_TOKENS,
            temperature = 0.0
        )

    private fun briefOf(request: ToolApprovalRequest, userIntent: String): String {
        val lines = ArrayList<String>()
        lines += "Owner request: " + userIntent.trim().take(MAX_INTENT_CHARS).ifEmpty { "unknown" }
        lines += "Tool: " + request.toolName
        lines += "Action: " + request.kind.name
        lines += "Path: " + request.path
        request.destination?.let { lines += "Destination: " + it }
        if (request.preview.isNotEmpty()) {
            lines += "Lines added: " + request.added
            lines += "Lines removed: " + request.removed
            lines += "Preview:"
            request.preview.take(MAX_PREVIEW_LINES).forEach { line ->
                lines += markerOf(line.kind) + line.text.take(MAX_LINE_CHARS)
            }
            val extra = request.preview.size - MAX_PREVIEW_LINES
            if (extra > 0) lines += "... " + extra + " more preview lines"
        }
        return lines.joinToString("\n")
    }

    private fun markerOf(kind: DiffKind): String = when (kind) {
        DiffKind.ADD -> "+ "
        DiffKind.REMOVE -> "- "
        DiffKind.KEEP -> "  "
    }

    private fun verdictOf(answer: String): ToolVerdict? {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed = parseJsonObject(answer.substring(start, end + 1)) ?: return null
        val safe = parsed.booleanField(FIELD_SAFE) ?: return null
        val relevant = parsed.booleanField(FIELD_RELEVANT) ?: return null
        return ToolVerdict(safe = safe, relevant = relevant)
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
        const val MAX_INTENT_CHARS = 600
        const val MAX_PREVIEW_LINES = 40
        const val MAX_LINE_CHARS = 200
        const val FIELD_SAFE = "safe"
        const val FIELD_RELEVANT = "relevant"
        const val SYSTEM_PROMPT =
            "You screen one file action that an assistant wants to run on the owner phone. " +
                "Judge two things. Safe means the action destroys nothing the owner did not " +
                "ask about, touches no credentials or private application data, and stays " +
                "small enough to undo. Relevant means the action matches what the owner just " +
                "asked for. Reply with one JSON object and nothing else, in the form " +
                "{\"safe\": true, \"relevant\": true}. Answer false whenever you are unsure."
    }
}
