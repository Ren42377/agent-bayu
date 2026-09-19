package dev.agentbayu.app.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import dev.agentbayu.app.ui.components.GlassDialog
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
    var contextLength by remember {
        mutableStateOf(existing?.contextLengthOverride?.toString().orEmpty())
    }
    var testing by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var blockedLink by remember { mutableStateOf<String?>(null) }

    val keyHint = remember(id, apiKey) { credentials.hint(id) }
    val loggedIn = remember(id) { credentials.credential(id) is Credential.OAuthTokens }
    val savedMessage = stringResource(R.string.connection_saved)
    val errorKey = stringResource(R.string.connection_error_key)
    val errorModel = stringResource(R.string.connection_error_model)
    val errorBaseUrl = stringResource(R.string.connection_error_base_url)
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
        contextLengthOverride = contextLength.trim().toIntOrNull()?.takeIf { it > 0 },
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
        contextLength = contextLength,
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
        onAddCustomModel = {
            val value = model.trim()
            if (value.isNotEmpty() && value !in customModels) customModels = customModels + value
        },
        onContextLengthChange = { value -> contextLength = value.filter { it.isDigit() } },
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
