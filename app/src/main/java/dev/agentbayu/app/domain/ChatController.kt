package dev.agentbayu.app.domain

import android.util.Log
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
    private val scope: CoroutineScope
) {

    private val respondingState = MutableStateFlow(false)

    @Volatile
    private var activeJob: Job? = null

    val messages: StateFlow<List<ChatMessage>> = repository.messages
    val isResponding: StateFlow<Boolean> = respondingState.asStateFlow()

    fun send(text: String, screenContext: String? = null) {
        val prompt = text.trim()
        if (prompt.isEmpty() || respondingState.value) {
            return
        }
        val history = repository.messages.value
        repository.append(MessageAuthor.USER, prompt)
        val placeholder = repository.append(MessageAuthor.AGENT, "", streaming = true)
        respondingState.value = true
        activeJob = scope.launch {
            var streamed = false
            try {
                engine.reply(
                    AgentRequest(prompt = prompt, screenContext = screenContext, history = history)
                ).collect { event ->
                    when (event) {
                        is AgentEvent.Delta -> {
                            streamed = true
                            repository.appendDelta(placeholder.id, event.text)
                        }

                        is AgentEvent.Route -> repository.attachRoute(placeholder.id, event.decision)

                        is AgentEvent.Completed ->
                            repository.complete(placeholder.id, event.decision, event.usage)

                        is AgentEvent.Failed ->
                            if (!streamed) repository.replaceText(placeholder.id, event.message)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Agent reply failed: " + error.javaClass.simpleName)
                if (!streamed) repository.replaceText(placeholder.id, errorReply)
            } finally {
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

    fun clear() {
        cancel()
        repository.clear()
    }

    private companion object {
        const val TAG = "AgentBayu"
    }
}
