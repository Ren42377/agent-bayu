package dev.agentbayu.app.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ConnectionTestResult
import dev.agentbayu.app.ai.Credential
import dev.agentbayu.app.ai.ModelFetchResult
import dev.agentbayu.app.ai.ProviderEntry
import dev.agentbayu.app.ai.isModelAccessible
import dev.agentbayu.app.ai.planTypeOf
import dev.agentbayu.app.ai.pickerModelIds
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.GlassOverlay
import kotlinx.coroutines.launch

@Composable
fun AiConnectionEditRoute(
    connectionId: String?,
    onBack: () -> Unit,
    onStartLogin: (String) -> Unit,
    onStartBrowserLogin: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalogRepository = remember(context) { AppGraph.catalogRepository(context) }
    val catalog by catalogRepository.catalog.collectAsState()
    val credentials = remember(context) { AppGraph.credentials(context) }
    val tester = remember(context) { AppGraph.connectionTester(context) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val providers = remember(catalog) { catalog.sortedByTier() }
    val existing = remember(connectionId) { connectionId?.let { store.find(it) } }
    val id = remember(connectionId) { existing?.id ?: store.newId() }

    var provider by remember {
        mutableStateOf(existing?.let { catalog.find(it.providerId) } ?: providers.firstOrNull())
    }
    LaunchedEffect(catalog) {
        val currentId = provider?.id
        if (currentId != null) {
            catalog.find(currentId)?.let { provider = it }
        }
    }
    var label by remember { mutableStateOf(existing?.label ?: provider?.label.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember {
        mutableStateOf(
            existing?.model
                ?: defaultModel(provider, planTypeOf(credentials.credential(id), provider))
        )
    }
    var baseUrl by remember {
        mutableStateOf(existing?.baseUrlOverride ?: provider?.baseUrl.orEmpty())
    }
    var discovered by remember { mutableStateOf(existing?.discoveredModels ?: emptyList()) }
    var customModels by remember { mutableStateOf(existing?.customModels ?: emptyList()) }
    var contextStop by remember { mutableStateOf(contextWindowStopOf(existing?.contextLengthOverride)) }
    var testing by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var blockedLink by remember { mutableStateOf<String?>(null) }
    var showAddModelDialog by remember { mutableStateOf(false) }
    var newModelName by remember { mutableStateOf("") }

    val keyHint = remember(id, apiKey) { credentials.hint(id) }
    val loggedIn = remember(id) { credentials.credential(id) is Credential.OAuthTokens }
    val savedMessage = stringResource(R.string.connection_saved)
    val errorKey = stringResource(R.string.connection_error_key)
    val errorModel = stringResource(R.string.connection_error_model)
    val errorBaseUrl = stringResource(R.string.connection_error_base_url)
    val accountExistsMessage = stringResource(R.string.connection_account_exists)
    val testFailedTemplate = stringResource(R.string.connection_test_failed)
    val testSuccessTemplate = stringResource(R.string.connection_test_success)
    val modelsFailedTemplate = stringResource(R.string.connection_models_failed)
    val modelsRefreshedTemplate = stringResource(R.string.connection_models_refreshed)
    val linkCopiedMessage = stringResource(R.string.dialog_link_copied)

    fun draft(): Connection = Connection(
        id = id,
        providerId = provider?.id.orEmpty(),
        label = label.trim().ifEmpty { provider?.label.orEmpty() },
        model = model.trim(),
        baseUrlOverride = baseUrl.trim()
            .takeIf { it.isNotEmpty() && it != provider?.baseUrl },
        discoveredModels = discovered,
        projectId = existing?.projectId,
        effort = existing?.effort,
        contextLengthOverride = contextStop.takeIf { it >= 0 }?.let { CONTEXT_WINDOW_STOPS[it] },
        customModels = customModels,
        createdAtMillis = existing?.createdAtMillis ?: 0L
    )

    fun persist(selected: ProviderEntry) {
        if (apiKey.isNotBlank()) credentials.putApiKey(id, apiKey)
        val hasCredential = credentials.hasKey(id)
        store.upsert(
            draft().copy(
                keyHint = if (selected.authKind.isOAuth) null else credentials.hint(id),
                health = if (!selected.requiresCredential || hasCredential) {
                    ConnectionHealth.READY
                } else {
                    ConnectionHealth.NEEDS_KEY
                },
                healthDetail = null
            )
        )
    }

    fun accountTaken(providerId: String): Boolean =
        store.connections.value.any { it.providerId == providerId && it.id != id }

    fun refreshModels() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            when (val result = tester.fetchModels(draft(), apiKey)) {
                is ModelFetchResult.Success -> {
                    discovered = result.models
                    existing?.let { store.setDiscoveredModels(it.id, result.models) }
                    onMessage(modelsRefreshedTemplate.format(result.models.size))
                }

                is ModelFetchResult.Failure ->
                    onMessage(modelsFailedTemplate.format(result.failure.message))
            }
            refreshing = false
        }
    }

    LaunchedEffect(provider?.id, loggedIn) {
        val entry = provider ?: return@LaunchedEffect
        if (entry.modelsPath == null) return@LaunchedEffect
        if (existing == null) return@LaunchedEffect
        if (discovered.isNotEmpty()) return@LaunchedEffect
        refreshing = true
        when (val result = tester.fetchModels(draft(), apiKey)) {
            is ModelFetchResult.Success -> {
                discovered = result.models
                store.setDiscoveredModels(existing.id, result.models)
            }

            is ModelFetchResult.Failure -> Unit
        }
        refreshing = false
    }

    val planType = planTypeOf(credentials.credential(id), provider)

    val state = ConnectionEditState(
        providers = providers,
        provider = provider,
        label = label,
        apiKey = apiKey,
        keyHint = keyHint,
        model = model,
        modelOptions = pickerModelIds(provider, draft(), planType),
        contextStop = contextStop,
        baseUrl = baseUrl,
        isNew = existing == null,
        loggedIn = loggedIn,
        testing = testing,
        refreshing = refreshing
    )

    val actions = ConnectionEditActions(
        onProviderChange = { providerId ->
            catalog.find(providerId)?.let { selected ->
                val previous = provider
                if (label.isBlank() || label == previous?.label) label = selected.label
                if (baseUrl.isBlank() || baseUrl == previous?.baseUrl) baseUrl = selected.baseUrl
                model = defaultModel(selected, planTypeOf(credentials.credential(id), selected))
                discovered = emptyList()
                customModels = emptyList()
                provider = selected
            }
        },
        onLabelChange = { value -> label = value },
        onKeyChange = { value -> apiKey = value },
        onModelChange = { value -> model = value },
        onRequestAddCustomModel = { showAddModelDialog = true },
        onContextStopSelected = { index -> contextStop = index },
        onContextDefault = { contextStop = -1 },
        onBaseUrlChange = { value -> baseUrl = value },
        onRefreshModels = { refreshModels() },
        onTest = {
            testing = true
            scope.launch {
                when (val result = tester.test(draft(), apiKey)) {
                    is ConnectionTestResult.Success ->
                        onMessage(testSuccessTemplate.format(result.model, result.latencyMillis))

                    is ConnectionTestResult.Failure ->
                        onMessage(testFailedTemplate.format(result.failure.message))
                }
                testing = false
            }
        },
        onSave = {
            val selected = provider
            when {
                selected == null -> onMessage(errorModel)
                model.isBlank() -> onMessage(errorModel)
                selected.editableBaseUrl && baseUrl.isBlank() -> onMessage(errorBaseUrl)
                selected.requiresKey && apiKey.isBlank() && keyHint == null -> onMessage(errorKey)
                selected.authKind.isOAuth && accountTaken(selected.id) ->
                    onMessage(accountExistsMessage)

                else -> {
                    persist(selected)
                    onMessage(savedMessage)
                    onBack()
                }
            }
        },
        onLogin = {
            val selected = provider
            when {
                selected == null -> onMessage(errorModel)
                model.isBlank() -> onMessage(errorModel)
                selected.authKind.isOAuth && accountTaken(selected.id) ->
                    onMessage(accountExistsMessage)

                else -> {
                    persist(selected)
                    if (selected.browserLogin != null) {
                        onStartBrowserLogin(id)
                    } else {
                        onStartLogin(id)
                    }
                }
            }
        },
        onOpenKeyUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (error: ActivityNotFoundException) {
                blockedLink = url
            }
        },
        onBack = onBack
    )

    ConnectionEditScreen(state = state, actions = actions, modifier = modifier)

    GlassOverlay(
        visible = showAddModelDialog,
        onDismiss = {
            showAddModelDialog = false
            newModelName = ""
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.connection_model_add_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = newModelName,
                onValueChange = { value -> newModelName = value },
                label = { Text(text = stringResource(R.string.connection_custom_model_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    onClick = {
                        showAddModelDialog = false
                        newModelName = ""
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_close),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GlassButton(
                    onClick = {
                        val name = newModelName.trim()
                        if (name.isNotEmpty()) {
                            if (name !in customModels) customModels = customModels + name
                            model = name
                            showAddModelDialog = false
                            newModelName = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.primary,
                    enabled = newModelName.isNotBlank(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.connection_model_add),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    val pendingLink = blockedLink
    GlassDialog(
        visible = pendingLink != null,
        title = stringResource(R.string.dialog_link_title),
        body = stringResource(R.string.dialog_link_body, pendingLink.orEmpty()),
        confirmLabel = stringResource(R.string.dialog_link_copy),
        onConfirm = {
            pendingLink?.let { clipboard.setText(AnnotatedString(it)) }
            blockedLink = null
            onMessage(linkCopiedMessage)
        },
        dismissLabel = stringResource(R.string.dialog_close),
        onDismiss = { blockedLink = null }
    )
}

private fun defaultModel(provider: ProviderEntry?, planType: String? = null): String =
    provider?.selectableModels
        ?.firstOrNull { isModelAccessible(it, planType) }
        ?.id
        .orEmpty()
