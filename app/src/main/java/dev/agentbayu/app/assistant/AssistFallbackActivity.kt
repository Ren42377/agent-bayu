package dev.agentbayu.app.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.MainActivity
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.AssistantPanel
import dev.agentbayu.app.ui.components.defaultSuggestions
import dev.agentbayu.app.ui.theme.AgentBayuAppTheme

class AssistFallbackActivity : ComponentActivity() {

    private val panel = AssistantPanelController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) { panel.requestHide() }
        AppGraph.warmUp(applicationContext)
        setContent {
            val ready by AppGraph.readiness.collectAsState()
            AgentBayuAppTheme {
                if (ready) {
                    FallbackPanel()
                }
            }
        }
    }

    @Composable
    private fun FallbackPanel() {
        val chat = remember { AppGraph.chat(this) }
        val visible by panel.visible.collectAsState()
        val input by panel.input.collectAsState()
        val messages by chat.messages.collectAsState()
        val responding by chat.isResponding.collectAsState()
        LaunchedEffect(Unit) { panel.show() }
        AssistantPanel(
            visible = visible,
            messages = messages,
            input = input,
            isResponding = responding,
            suggestions = defaultSuggestions(),
            onInputChange = panel::updateInput,
            onSend = { chat.send(panel.takeInput()) },
            onSuggestionClick = { text -> chat.send(text) },
            onMicClick = ::showMicNotice,
            onOpenApp = ::openApp,
            onDismiss = panel::requestHide,
            onHidden = ::finish
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        panel.show()
    }

    private fun showMicNotice() {
        Toast.makeText(this, R.string.mic_pending_message, Toast.LENGTH_SHORT).show()
    }

    private fun openApp() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        panel.requestHide()
    }
}
