package dev.agentbayu.app.ai

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { INFO, WARNING, ERROR }

data class LogEntry(
    val id: Long,
    val atMillis: Long,
    val level: LogLevel,
    val source: String,
    val message: String,
    val detail: String? = null
)

class LogStore(private val clock: Clock = RealClock) {

    private val nextId = AtomicLong(1L)
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    fun info(source: String, message: String, detail: String? = null) {
        add(LogLevel.INFO, source, message, detail)
    }

    fun warning(source: String, message: String, detail: String? = null) {
        add(LogLevel.WARNING, source, message, detail)
    }

    fun error(source: String, message: String, detail: String? = null) {
        add(LogLevel.ERROR, source, message, detail)
    }

    fun clear() {
        state.value = emptyList()
    }

    private fun add(level: LogLevel, source: String, message: String, detail: String?) {
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            atMillis = clock.nowMillis(),
            level = level,
            source = source,
            message = message,
            detail = detail
        )
        state.update { current ->
            val appended = current + entry
            if (appended.size > MAX_ENTRIES) {
                appended.takeLast(MAX_ENTRIES)
            } else {
                appended
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 200
    }
}
