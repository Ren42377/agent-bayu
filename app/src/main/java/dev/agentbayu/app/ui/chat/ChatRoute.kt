package dev.agentbayu.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.ai.channelLabel
import dev.agentbayu.app.ui.components.defaultSuggestions

@Composable
fun ChatRoute(
    onMessage: (String) -> Unit,
    onOpenRouting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chat = remember(context) { AppGraph.chat(context) }
    val router = remember(context) { AppGraph.router(context) }
    val routingStore = remember(context) { AppGraph.routingConfig(context) }
    val connectionStore = remember(context) { AppGraph.connections(context) }
    val messages by chat.messages.collectAsState()
    val isResponding by chat.isResponding.collectAsState()
    val config by routingStore.config.collectAsState()
    val connections by connectionStore.connections.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val micMessage = stringResource(R.string.mic_pending_message)
    val comboPairs = config.availableCombos().map { combo -> combo.id to combo.label }
    val channelText = channelLabel(config.channel, comboPairs, connections)
    val preview = remember(config, connections) { router.preview(config.channel) }
    val routeHint = if (preview.isEmpty()) {
        stringResource(R.string.chat_route_empty)
    } else {
        stringResource(R.string.chat_route_hint, channelText, preview.first().candidate.model.id)
    }
    ChatScreen(
        messages = messages,
        input = input,
        isResponding = isResponding,
        suggestions = defaultSuggestions(),
        routeHint = routeHint,
        onInputChange = { value -> input = value },
        onSend = {
            chat.send(input)
            input = ""
        },
        onSuggestionClick = { text -> chat.send(text) },
        onMicClick = { onMessage(micMessage) },
        onOpenRouting = onOpenRouting,
        onStop = chat::cancel,
        modifier = modifier
    )
}
