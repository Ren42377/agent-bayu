package dev.agentbayu.app.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.MainActivity
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatController
import dev.agentbayu.app.ui.components.AssistantPanel
import dev.agentbayu.app.ui.components.defaultSuggestions
import dev.agentbayu.app.ui.theme.AgentBayuAppTheme

class BayuVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val viewTreeOwner = SessionViewTreeOwner()
    private val panel = AssistantPanelController()

    override fun onCreate() {
        setTheme(R.style.Theme_AgentBayu_Session)
        super.onCreate()
        viewTreeOwner.create()
    }

    override fun onCreateContentView(): View {
        val chat = AppGraph.chat(context)
        val settings = AppGraph.settings(context)
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(viewTreeOwner)
        view.setViewTreeViewModelStoreOwner(viewTreeOwner)
        view.setViewTreeSavedStateRegistryOwner(viewTreeOwner)
        view.setContent {
            val resetToken by panel.resetToken.collectAsState()
            val visible by panel.visible.collectAsState()
            val input by panel.input.collectAsState()
            val messages by chat.messages.collectAsState()
            val responding by chat.isResponding.collectAsState()
            val useScreenContext by settings.useScreenContext.collectAsState()
            AgentBayuAppTheme {
                key(resetToken) {
                    AssistantPanel(
                        visible = visible,
                        messages = messages,
                        input = input,
                        isResponding = responding,
                        suggestions = defaultSuggestions(),
                        onInputChange = panel::updateInput,
                        onSend = { send(chat, panel.takeInput(), useScreenContext) },
                        onSuggestionClick = { text -> send(chat, text, useScreenContext) },
                        onMicClick = ::showMicNotice,
                        onOpenApp = ::openApp,
                        onDismiss = ::dismissPanel,
                        onHidden = ::finishPanel
                    )
                }
            }
        }
        return view
    }

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        window.window?.let { sessionWindow ->
            WindowCompat.setDecorFitsSystemWindows(sessionWindow, false)
            sessionWindow.setSoftInputMode(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                } else {
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }
            )
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        closeSystemDialogs()
        viewTreeOwner.resume()
        panel.show()
    }

    override fun onHide() {
        panel.reset()
        viewTreeOwner.pause()
        ScreenContextHolder.clear()
        super.onHide()
    }

    override fun onDestroy() {
        viewTreeOwner.destroy()
        ScreenContextHolder.clear()
        super.onDestroy()
    }

    override fun onBackPressed() {
        dismissPanel()
    }

    @Deprecated("Replaced by onHandleAssist(AssistState) on API 30 and above")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        if (AppGraph.settings(context).useScreenContext.value) {
            ScreenContextHolder.update(structure)
        } else {
            ScreenContextHolder.clear()
        }
    }

    private fun send(chat: ChatController, text: String, useScreenContext: Boolean) {
        if (text.isBlank()) {
            return
        }
        chat.send(text, if (useScreenContext) ScreenContextHolder.current() else null)
    }

    private fun dismissPanel() {
        panel.requestHide()
    }

    private fun finishPanel() {
        hide()
    }

    private fun showMicNotice() {
        Toast.makeText(context, R.string.mic_pending_message, Toast.LENGTH_SHORT).show()
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startAssistantActivity(intent)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to start assistant activity", error)
            context.startActivity(intent)
        }
        dismissPanel()
    }

    private companion object {
        const val TAG = "AgentBayu"
    }
}
