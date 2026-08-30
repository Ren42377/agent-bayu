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
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.resolveActiveConnection
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.components.defaultSuggestions

@Composable
fun ChatRoute(
    onMessage: (String) -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chat = remember(context) { AppGraph.chat(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val credentials = remember(context) { AppGraph.credentials(context) }
    val connectionStore = remember(context) { AppGraph.connections(context) }
    val messages by chat.messages.collectAsState()
    val isResponding by chat.isResponding.collectAsState()
    val connections by connectionStore.connections.collectAsState()
    val activeId by connectionStore.activeConnectionId.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val micMessage = stringResource(R.string.mic_pending_message)

    val active = remember(connections, activeId) { resolveActiveConnection(connections, activeId) }
    val options = remember(connections, activeId) {
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            val hasKey = provider?.requiresKey != true || credentials.hasKey(connection.id)
            ProviderOption(
                connectionId = connection.id,
                label = connection.label,
                providerLabel = provider?.label ?: connection.providerId,
                model = connection.model,
                models = (provider?.models?.map { it.id }.orEmpty() + connection.discoveredModels)
                    .distinct(),
                authKind = provider?.authKind ?: AuthKind.API_KEY,
                isActive = connection.id == active?.id,
                ready = provider != null && hasKey
            )
        }
    }
    val providerHint = if (active == null) {
        stringResource(R.string.chat_provider_empty)
    } else {
        stringResource(R.string.chat_provider_hint, active.label, active.model)
    }

    ChatScreen(
        messages = messages,
        input = input,
        isResponding = isResponding,
        suggestions = defaultSuggestions(),
        providerHint = providerHint,
        providerOptions = options,
        onInputChange = { value -> input = value },
        onSend = {
            chat.send(input)
            input = ""
        },
        onSuggestionClick = { text -> chat.send(text) },
        onMicClick = { onMessage(micMessage) },
        onSelectProvider = { connectionId -> connectionStore.setActive(connectionId) },
        onSelectModel = { connectionId, model -> connectionStore.setModel(connectionId, model) },
        onManageProviders = onOpenProviders,
        onStop = chat::cancel,
        modifier = modifier
    )
}
