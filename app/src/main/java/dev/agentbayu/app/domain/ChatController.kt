package dev.agentbayu.app.domain

import android.util.Log
import dev.agentbayu.app.ai.LogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatController(
    private val repository: ConversationRepository,
    private val engine: AgentEngine,
    private val errorReply: String,
    private val logStore: LogStore,
    private val scope: CoroutineScope
) {

    private val respondingState = MutableStateFlow(false)

    @Volatile
    private var activeJob: Job? = null

    val messages: StateFlow<List<ChatMessage>> = repository.messages
    val isResponding: StateFlow<Boolean> = respondingState.asStateFlow()

    fun send(
        text: String,
        screenContext: String? = null,
        attachments: List<MessageAttachment> = emptyList()
    ) {
        val prompt = text.trim()
        if ((prompt.isEmpty() && attachments.isEmpty()) || respondingState.value) {
            return
        }
        val history = repository.messages.value
        repository.append(MessageAuthor.USER, prompt, attachments = attachments)
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        respondingState.value = true
        activeJob = scope.launch {
            var streamed = false
            val pending = StringBuilder()
            var lastFlushNanos = 0L

            fun flush() {
                if (pending.isEmpty()) return
                repository.appendDelta(placeholder.id, pending.toString())
                pending.setLength(0)
            }

            fun flushIfDue() {
                val now = System.nanoTime()
                if (lastFlushNanos == 0L || now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
                    flush()
                    lastFlushNanos = now
                }
            }

            try {
                engine.reply(
                    AgentRequest(
                        prompt = prompt,
                        screenContext = screenContext,
                        history = history,
                        attachments = attachments
                    )
                ).collect { event ->
                    when (event) {
                        is AgentEvent.Delta -> {
                            streamed = true
                            pending.append(event.text)
                            flushIfDue()
                        }

                        is AgentEvent.Detail -> {
                            flush()
                            repository.attachDetail(placeholder.id, event.detail)
                        }

                        is AgentEvent.ToolStarted -> flush()

                        is AgentEvent.ToolFinished -> flush()

                        is AgentEvent.Completed -> {
                            flush()
                            repository.complete(placeholder.id, event.detail, event.usage)
                        }

                        is AgentEvent.Failed -> {
                            flush()
                            if (!streamed) repository.replaceText(placeholder.id, event.message)
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Agent reply failed: " + error.javaClass.simpleName)
                logStore.error(SOURCE, "Agent reply failed", error.javaClass.simpleName)
                if (!streamed) repository.replaceText(placeholder.id, errorReply)
            } finally {
                flush()
                repository.finishStreaming(placeholder.id)
                respondingState.value = false
                activeJob = null
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }

    private companion object {
        const val TAG = "AgentBayu"
        const val SOURCE = "Chat"
        const val FLUSH_INTERVAL_NANOS = 90_000_000L
    }
}
