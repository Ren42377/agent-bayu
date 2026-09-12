package dev.agentbayu.app.assistant

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.MainActivity
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.AssistantPanel
import dev.agentbayu.app.ui.components.AttachmentThumbnails
import dev.agentbayu.app.ui.components.LocalAttachmentLoader
import dev.agentbayu.app.ui.theme.AgentBayuAppTheme
import kotlinx.coroutines.launch

class AssistFallbackActivity : ComponentActivity() {

    private val panel = AssistantPanelController()
    private var panelMounted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            if (panelMounted && panel.visible.value) {
                panel.requestHide()
            } else {
                finish()
            }
        }
        AppGraph.warmUp(applicationContext)
        setContent {
            val attachmentStore = remember { AppGraph.attachments(this) }
            AgentBayuAppTheme {
                CompositionLocalProvider(
                    LocalAttachmentLoader provides AttachmentThumbnails { id, edge ->
                        attachmentStore.thumbnail(id, edge)
                    }
                ) {
                    FallbackPanel()
                }
            }
        }
    }

    @Composable
    private fun FallbackPanel() {
        DisposableEffect(Unit) {
            panelMounted = true
            onDispose { panelMounted = false }
        }
        val ready by AppGraph.assistantReadiness.collectAsState()
        val visible by panel.visible.collectAsState()
        val input by panel.input.collectAsState()
        val invocationId by panel.invocationId.collectAsState()
        val invocationBaseline by panel.invocationBaseline.collectAsState()
        if (!ready) {
            LaunchedEffect(Unit) { panel.show() }
            AssistantPanel(
                visible = visible,
                invocationId = invocationId,
                manageImeInsets = true,
                messages = emptyList(),
                input = "",
                isResponding = false,
                enabled = false,
                screenshot = null,
                screenshotActive = false,
                onToggleScreenshot = {},
                onInputChange = {},
                onSend = {},
                onStop = {},
                onMicClick = ::showMicNotice,
                onOpenApp = ::openApp,
                onDismiss = panel::requestHide,
                onHidden = ::onPanelFinished
            )
            return
        }
        val chat = remember { AppGraph.chat(this) }
        val settings = remember { AppGraph.settings(this) }
        val attachmentStore = remember { AppGraph.attachments(this) }
        val useScreenContext by settings.useScreenContext.collectAsState()
        val scope = rememberCoroutineScope()
        val screenshot by ScreenShotHolder.screenshot.collectAsState()
        var screenshotActive by remember { mutableStateOf(false) }
        val messages by chat.messages.collectAsState()
        val responding by chat.isResponding.collectAsState()
        val overlayBaseline = if (invocationBaseline > 0) {
            invocationBaseline
        } else {
            (messages.size - OVERLAY_TURN_LIMIT).coerceAtLeast(0)
        }
        val overlayMessages = remember(messages, overlayBaseline) {
            if (overlayBaseline <= 0) {
                messages.takeLast(OVERLAY_TURN_LIMIT)
            } else {
                messages.drop(overlayBaseline)
            }
        }
        LaunchedEffect(Unit) {
            if (AppGraph.assistantReadiness.value) {
                panel.show(AppGraph.chat(this@AssistFallbackActivity).messages.value.size)
            } else {
                panel.show()
            }
        }
        AssistantPanel(
            visible = visible,
            invocationId = invocationId,
            manageImeInsets = true,
            messages = overlayMessages,
            input = input,
            isResponding = responding,
            enabled = true,
            screenshot = screenshot,
            screenshotActive = screenshotActive,
            onToggleScreenshot = {
                if (screenshot != null) {
                    screenshotActive = !screenshotActive
                }
            },
            onInputChange = panel::updateInput,
            onSend = {
                val text = panel.takeInput()
                val screenContext = if (useScreenContext) ScreenContextHolder.current() else null
                val shot = if (screenshotActive) screenshot else null
                screenshotActive = false
                if (shot == null) {
                    chat.send(text, screenContext)
                } else {
                    scope.launch {
                        val attachment = attachmentStore.accept(shot)
                        chat.send(text, screenContext, attachment?.let(::listOf) ?: emptyList())
                    }
                }
            },
            onStop = chat::cancel,
            onMicClick = ::showMicNotice,
            onOpenApp = ::openApp,
            onDismiss = panel::requestHide,
            onHidden = ::onPanelFinished
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (AppGraph.assistantReadiness.value) {
            panel.show(AppGraph.chat(this).messages.value.size)
        } else {
            panel.show()
        }
    }

    private fun onPanelFinished() {
        val callback = onPanelHidden
        onPanelHidden = null
        runCatching { callback?.invoke() }
        finish()
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

    companion object {
        private const val OVERLAY_TURN_LIMIT = 2
        var onPanelHidden: (() -> Unit)? = null
    }
}
