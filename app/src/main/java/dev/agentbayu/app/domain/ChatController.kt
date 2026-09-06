package dev.agentbayu.app.domain

import android.util.Log
import dev.agentbayu.app.ai.LogStore
import java.util.concurrent.atomic.AtomicLong
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
    private val sends = AtomicLong(0L)

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var streamingId: Long? = null

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
        streamingId = placeholder.id
        val token = sends.incrementAndGet()
        activeJob = scope.launch {
            var streamed = false
            val pending = StringBuilder()
            val pendingThought = StringBuilder()
            var lastFlushNanos = 0L
            var thinkingStartedNanos = 0L

            fun flush() {
                if (pendingThought.isNotEmpty()) {
                    repository.appendThinking(placeholder.id, pendingThought.toString())
                    pendingThought.setLength(0)
                }
                if (pending.isNotEmpty()) {
                    repository.appendDelta(placeholder.id, pending.toString())
                    pending.setLength(0)
                }
            }

            fun flushIfDue() {
                val now = System.nanoTime()
                if (lastFlushNanos == 0L || now - lastFlushNanos >= FLUSH_INTERVAL_NANOS) {
                    flush()
                    lastFlushNanos = now
                }
            }

            fun closeThinking() {
                if (thinkingStartedNanos == 0L) return
                val elapsed = (System.nanoTime() - thinkingStartedNanos) / NANOS_PER_MILLI
                thinkingStartedNanos = 0L
                flush()
                repository.closeThinking(placeholder.id, elapsed)
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
                        is AgentEvent.Thinking -> {
                            if (thinkingStartedNanos == 0L) {
                                thinkingStartedNanos = System.nanoTime()
                            }
                            pendingThought.append(event.text)
                            flushIfDue()
                        }

                        is AgentEvent.Delta -> {
                            streamed = true
                            closeThinking()
                            pending.append(event.text)
                            flushIfDue()
                        }

                        is AgentEvent.Detail -> {
                            flush()
                            repository.attachDetail(placeholder.id, event.detail)
                        }

                        is AgentEvent.ToolStarted -> {
                            closeThinking()
                            flush()
                            repository.startToolRun(placeholder.id, event.name, event.label)
                        }

                        is AgentEvent.ToolFinished -> {
                            flush()
                            repository.finishToolRun(placeholder.id, event.name, event.ok)
                        }

                        is AgentEvent.AutoApproved -> {
                            flush()
                            repository.appendAutoApprove(placeholder.id, event.reason)
                        }

                        is AgentEvent.Completed -> {
                            closeThinking()
                            flush()
                            repository.complete(placeholder.id, event.detail, event.usage)
                        }

                        is AgentEvent.Failed -> {
                            closeThinking()
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
                if (sends.get() == token) {
                    respondingState.value = false
                    streamingId = null
                    activeJob = null
                }
            }
        }
    }

    fun cancel() {
        val job = activeJob ?: return
        activeJob = null
        respondingState.value = false
        streamingId?.let { id -> repository.finishStreaming(id) }
        job.cancel()
    }

    private companion object {
        const val TAG = "AgentBayu"
        const val SOURCE = "Chat"
        const val FLUSH_INTERVAL_NANOS = 90_000_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
