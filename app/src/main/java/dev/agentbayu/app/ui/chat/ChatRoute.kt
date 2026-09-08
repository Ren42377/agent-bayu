package dev.agentbayu.app.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.availableEfforts
import dev.agentbayu.app.ai.resolveActiveConnection
import dev.agentbayu.app.ai.resolveEffort
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.components.AttachmentThumbnails
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.LocalAttachmentLoader
import dev.agentbayu.app.ui.components.defaultSuggestions
import dev.agentbayu.app.ui.history.HistoryDrawerState
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    onMessage: (String) -> Unit,
    onOpenProviders: () -> Unit,
    drawer: HistoryDrawerState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val chat = remember(context) { AppGraph.chat(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val credentials = remember(context) { AppGraph.credentials(context) }
    val connectionStore = remember(context) { AppGraph.connections(context) }
    val attachmentStore = remember(context) { AppGraph.attachments(context) }
    val sessionManager = remember(context) { AppGraph.sessions(context) }
    val thumbnails = remember(attachmentStore) {
        AttachmentThumbnails { id, edge -> attachmentStore.thumbnail(id, edge) }
    }
    val scope = rememberCoroutineScope()
    val messages by chat.messages.collectAsState()
    val isResponding by chat.isResponding.collectAsState()
    val activeSessionId by sessionManager.activeSessionId.collectAsState()
    val incognito by sessionManager.incognito.collectAsState()
    val incognitoToken by sessionManager.incognitoToken.collectAsState()
    val switching by sessionManager.switching.collectAsState()
    val conversationKey = if (incognito) {
        "incognito-" + incognitoToken
    } else {
        activeSessionId.orEmpty()
    }
    val connections by connectionStore.connections.collectAsState()
    val activeId by connectionStore.activeConnectionId.collectAsState()
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<MessageAttachment>>(emptyList()) }
    var disposableAttachmentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var attachmentRequestKey by remember { mutableStateOf<String?>(null) }
    var attachmentRequestRoom by remember { mutableStateOf(0) }
    var micPrompt by remember { mutableStateOf(MicPrompt.NONE) }
    val currentConversationKey by rememberUpdatedState(conversationKey)
    LaunchedEffect(conversationKey) {
        pending
            .filter { attachment -> attachment.id in disposableAttachmentIds }
            .forEach { attachment -> attachmentStore.discard(attachment.id) }
        input = ""
        pending = emptyList()
        disposableAttachmentIds = emptySet()
        attachmentRequestKey = null
        attachmentRequestRoom = 0
        editMessage = null
    }
    val micPendingMessage = stringResource(R.string.mic_pending_message)
    val micGrantedMessage = stringResource(R.string.mic_granted_message)
    val settingsUnavailable = stringResource(R.string.dialog_settings_unavailable)
    val attachFailed = stringResource(R.string.chat_attach_failed)
    val attachLimit = stringResource(R.string.chat_attach_limit, MAX_ATTACHMENTS)
    val copiedMessage = stringResource(R.string.chat_copied)

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micPrompt = if (granted) MicPrompt.NONE else MicPrompt.DENIED
        if (granted) {
            onMessage(micGrantedMessage)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)
    ) { picked ->
        val requestedFor = attachmentRequestKey
        val requestedRoom = attachmentRequestRoom
        attachmentRequestKey = null
        attachmentRequestRoom = 0
        if (picked.isEmpty() || requestedFor == null) {
            return@rememberLauncherForActivityResult
        }
        val room = requestedRoom
        if (room <= 0) {
            onMessage(attachLimit)
            return@rememberLauncherForActivityResult
        }
        if (picked.size > room) onMessage(attachLimit)
        scope.launch {
            picked.take(room).forEach { uri ->
                val accepted = attachmentStore.accept(uri)
                when {
                    accepted == null -> onMessage(attachFailed)
                    currentConversationKey != requestedFor -> attachmentStore.discard(accepted.id)
                    else -> {
                        pending = pending + accepted
                        disposableAttachmentIds = disposableAttachmentIds + accepted.id
                    }
                }
            }
        }
    }

    val active = remember(connections, activeId) { resolveActiveConnection(connections, activeId) }
    val canAttach = remember(active) {
        val connection = active ?: return@remember false
        val provider = catalog.find(connection.providerId) ?: return@remember false
        Candidate(connection, provider, provider.modelOrFallback(connection.model)).supportsVision
    }
    val options = remember(connections, activeId) {
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            val hasCredential = provider?.requiresCredential != true ||
                credentials.hasKey(connection.id)
            val efforts = provider
                ?.let { availableEfforts(it, connection.model, connection.discoveredModels) }
                .orEmpty()
            ProviderOption(
                connectionId = connection.id,
                label = connection.label,
                providerLabel = provider?.label ?: connection.providerId,
                model = connection.model,
                models = provider
                    ?.pickerModelIds(connection.discoveredModels, connection.model)
                    .orEmpty(),
                efforts = efforts,
                effort = resolveEffort(efforts, connection.effort, connection.model),
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

    CompositionLocalProvider(LocalAttachmentLoader provides thumbnails) {
        ChatScreen(
            messages = messages,
            input = input,
            isResponding = isResponding,
            suggestions = defaultSuggestions(),
            providerHint = providerHint,
            providerOptions = options,
            onInputChange = { value -> input = value },
            onSend = {
                if (!switching) {
                    val editing = editMessage
                    val sentAttachments = pending
                    val accepted = if (editing == null) {
                        chat.send(input, attachments = sentAttachments)
                    } else {
                        chat.restartFrom(editing, input, sentAttachments)
                    }
                    if (accepted) {
                        input = ""
                        pending = emptyList()
                        disposableAttachmentIds = emptySet()
                        editMessage = null
                    }
                }
            },
            onSuggestionClick = { text -> if (!switching) chat.send(text) },
            onMicClick = {
                if (hasMicrophonePermission(context)) {
                    onMessage(micPendingMessage)
                } else {
                    micPrompt = MicPrompt.REQUEST
                }
            },
            onSelectProvider = { connectionId -> connectionStore.setActive(connectionId) },
            onSelectModel = { connectionId, model ->
                connectionStore.setModel(connectionId, model)
            },
            onSelectEffort = { connectionId, effort ->
                connectionStore.setEffort(connectionId, effort)
            },
            onManageProviders = onOpenProviders,
            onStop = chat::cancel,
            incognito = incognito,
            sessionActionEnabled = !switching,
            onSessionAction = {
                if (!switching) {
                    when {
                        messages.isNotEmpty() -> sessionManager.newSession()
                        incognito -> sessionManager.stopIncognito()
                        else -> sessionManager.startIncognito()
                    }
                }
            },
            onCopy = { message ->
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("Agent Bayu", message.text))
                    )
                    onMessage(copiedMessage)
                }
            },
            onRegenerate = { message ->
                if (!switching) {
                    val index = messages.indexOfFirst { it.id == message.id }
                    if (index > 0 && messages[index - 1].author == MessageAuthor.USER) {
                        chat.regenerate(message, messages[index - 1])
                    }
                }
            },
            onEdit = { message ->
                if (!switching) {
                    input = message.text
                    pending = message.attachments
                    disposableAttachmentIds = emptySet()
                    editMessage = message
                }
            },
            drawer = drawer,
            modifier = modifier,
            sessionKey = conversationKey,
            attachments = pending,
            canAttach = canAttach,
            composerEnabled = !switching,
            onAttachClick = {
                if (!switching) {
                    attachmentRequestKey = conversationKey
                    attachmentRequestRoom = MAX_ATTACHMENTS - pending.size
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onRemoveAttachment = { attachment ->
                pending = pending - attachment
                if (attachment.id in disposableAttachmentIds) {
                    disposableAttachmentIds = disposableAttachmentIds - attachment.id
                    attachmentStore.discard(attachment.id)
                }
            }
        )
    }

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

private const val MAX_ATTACHMENTS = 4

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
