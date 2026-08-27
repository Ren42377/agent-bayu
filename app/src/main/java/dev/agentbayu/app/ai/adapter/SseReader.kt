package dev.agentbayu.app.ai.adapter

sealed interface SseSignal {
    data class Data(val json: String) : SseSignal

    data object Done : SseSignal
}

class SseReader {

    private val buffer = StringBuilder()

    fun accept(rawLine: String): List<SseSignal> {
        val line = rawLine.removeSuffix("\r")
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return emptyList()

        val field = line.substringBefore(':', missingDelimiterValue = "")
        if (field != "data") return emptyList()

        val chunk = line.substringAfter(':').removePrefix(" ")
        if (chunk.trim() == DONE_SENTINEL) {
            val pending = flush()
            return pending + SseSignal.Done
        }

        val pending = if (looksComplete()) flush() else emptyList()
        if (buffer.isNotEmpty()) buffer.append('\n')
        buffer.append(chunk)
        return pending
    }

    fun flush(): List<SseSignal> {
        if (buffer.isEmpty()) return emptyList()
        val payload = buffer.toString().trim()
        buffer.setLength(0)
        if (payload.isEmpty()) return emptyList()
        return listOf(SseSignal.Data(payload))
    }

    private fun looksComplete(): Boolean {
        if (buffer.isEmpty()) return false
        val trimmed = buffer.toString().trim()
        if (trimmed.isEmpty()) return false
        val last = trimmed.last()
        return last == '}' || last == ']'
    }

    companion object {
        const val DONE_SENTINEL = "[DONE]"
    }
}
