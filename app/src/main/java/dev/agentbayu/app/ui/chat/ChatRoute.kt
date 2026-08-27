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
import dev.agentbayu.app.ui.components.defaultSuggestions

@Composable
fun ChatRoute(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val chat = remember(context) { AppGraph.chat(context) }
    val messages by chat.messages.collectAsState()
    val isResponding by chat.isResponding.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val micMessage = stringResource(R.string.mic_pending_message)
    ChatScreen(
        messages = messages,
        input = input,
        isResponding = isResponding,
        suggestions = defaultSuggestions(),
        onInputChange = { value -> input = value },
        onSend = {
            chat.send(input)
            input = ""
        },
        onSuggestionClick = { text -> chat.send(text) },
        onMicClick = { onMessage(micMessage) },
        modifier = modifier
    )
}
