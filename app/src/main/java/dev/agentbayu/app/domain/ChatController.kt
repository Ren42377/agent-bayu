package dev.agentbayu.app.domain

import android.util.Log
import kotlinx.coroutines.CoroutineScope
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

    val messages: StateFlow<List<ChatMessage>> = repository.messages
    val isResponding: StateFlow<Boolean> = respondingState.asStateFlow()

    fun send(text: String, screenContext: String? = null) {
        val prompt = text.trim()
        if (prompt.isEmpty() || respondingState.value) {
            return
        }
        repository.append(MessageAuthor.USER, prompt)
        respondingState.value = true
        scope.launch {
            val reply = try {
                engine.reply(prompt, screenContext)
            } catch (error: Exception) {
                Log.e(TAG, "Agent reply failed", error)
                errorReply
            }
            repository.append(MessageAuthor.AGENT, reply)
            respondingState.value = false
        }
    }

    fun clear() {
        repository.clear()
    }

    private companion object {
        const val TAG = "AgentBayu"
    }
}
