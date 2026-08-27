package dev.agentbayu.app

import android.content.Context
import dev.agentbayu.app.domain.ChatController
import dev.agentbayu.app.domain.ConversationRepository
import dev.agentbayu.app.domain.EchoAgentEngine
import dev.agentbayu.app.platform.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object AppGraph {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val conversation = ConversationRepository()

    @Volatile
    private var chatController: ChatController? = null

    @Volatile
    private var appSettings: AppSettings? = null

    fun chat(context: Context): ChatController {
        chatController?.let { return it }
        return synchronized(this) {
            chatController ?: createChatController(context).also { chatController = it }
        }
    }

    fun settings(context: Context): AppSettings {
        appSettings?.let { return it }
        return synchronized(this) {
            appSettings ?: AppSettings(context).also { appSettings = it }
        }
    }

    private fun createChatController(context: Context): ChatController {
        val resources = context.applicationContext
        return ChatController(
            repository = conversation,
            engine = EchoAgentEngine(
                replyTemplate = resources.getString(R.string.agent_stub_reply),
                contextReplyTemplate = resources.getString(R.string.agent_stub_reply_context)
            ),
            errorReply = resources.getString(R.string.agent_error_reply),
            scope = scope
        )
    }
}
