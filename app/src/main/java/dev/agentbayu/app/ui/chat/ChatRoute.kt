package dev.agentbayu.app.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.resolveActiveConnection
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.components.GlassDialog
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
    var micPrompt by remember { mutableStateOf(MicPrompt.NONE) }
    val micPendingMessage = stringResource(R.string.mic_pending_message)
    val micGrantedMessage = stringResource(R.string.mic_granted_message)
    val settingsUnavailable = stringResource(R.string.dialog_settings_unavailable)

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPrompt = if (granted) MicPrompt.NONE else MicPrompt.DENIED
        if (granted) {
            onMessage(micGrantedMessage)
        }
    }

    val active = remember(connections, activeId) { resolveActiveConnection(connections, activeId) }
    val options = remember(connections, activeId) {
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            val hasCredential = provider?.requiresCredential != true ||
                credentials.hasKey(connection.id)
            ProviderOption(
                connectionId = connection.id,
                label = connection.label,
                providerLabel = provider?.label ?: connection.providerId,
                model = connection.model,
                models = (provider?.models?.map { it.id }.orEmpty() + connection.discoveredModels)
                    .distinct(),
                authKind = provider?.authKind ?: AuthKind.API_KEY,
                isActive = connection.id == active?.id,
                ready = provider != null && hasCredential
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
        onMicClick = {
            if (hasMicrophonePermission(context)) {
                onMessage(micPendingMessage)
            } else {
                micPrompt = MicPrompt.REQUEST
            }
        },
        onSelectProvider = { connectionId -> connectionStore.setActive(connectionId) },
        onSelectModel = { connectionId, model -> connectionStore.setModel(connectionId, model) },
        onManageProviders = onOpenProviders,
        onStop = chat::cancel,
        modifier = modifier
    )

    GlassDialog(
        visible = micPrompt == MicPrompt.REQUEST,
        title = stringResource(R.string.dialog_mic_title),
        body = stringResource(R.string.dialog_mic_body),
        confirmLabel = stringResource(R.string.dialog_mic_allow),
        onConfirm = {
            micPrompt = MicPrompt.NONE
            microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        dismissLabel = stringResource(R.string.dialog_later),
        onDismiss = { micPrompt = MicPrompt.NONE }
    )

    GlassDialog(
        visible = micPrompt == MicPrompt.DENIED,
        title = stringResource(R.string.dialog_mic_denied_title),
        body = stringResource(R.string.dialog_mic_denied_body),
        confirmLabel = stringResource(R.string.dialog_open_settings),
        onConfirm = {
            micPrompt = MicPrompt.NONE
            if (!openAppSettings(context)) {
                onMessage(settingsUnavailable)
            }
        },
        dismissLabel = stringResource(R.string.dialog_close),
        onDismiss = { micPrompt = MicPrompt.NONE }
    )
}

private enum class MicPrompt { NONE, REQUEST, DENIED }

private fun hasMicrophonePermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context): Boolean {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
}
