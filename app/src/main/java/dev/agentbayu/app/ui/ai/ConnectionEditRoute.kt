package dev.agentbayu.app.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ConnectionTestResult
import dev.agentbayu.app.ai.ModelFetchResult
import dev.agentbayu.app.ai.ProviderEntry
import kotlinx.coroutines.launch

@Composable
fun AiConnectionEditRoute(
    connectionId: String?,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val credentials = remember(context) { AppGraph.credentials(context) }
    val tester = remember(context) { AppGraph.connectionTester(context) }
    val scope = rememberCoroutineScope()
    val providers = remember(catalog) { catalog.sortedByTier() }
    val existing = remember(connectionId) { connectionId?.let { store.find(it) } }
    val id = remember(connectionId) { existing?.id ?: store.newId() }

    var provider by remember {
        mutableStateOf(existing?.let { catalog.find(it.providerId) } ?: providers.firstOrNull())
    }
    var label by remember { mutableStateOf(existing?.label ?: provider?.label.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(existing?.model ?: defaultModel(provider)) }
    var baseUrl by remember {
        mutableStateOf(existing?.baseUrlOverride ?: provider?.baseUrl.orEmpty())
    }
    var priority by remember {
        mutableStateOf((existing?.priority ?: Connection.DEFAULT_PRIORITY).toString())
    }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }
    var discovered by remember { mutableStateOf(existing?.discoveredModels ?: emptyList()) }
    var testing by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    val keyHint = remember(id, apiKey) { credentials.hint(id) }
    val savedMessage = stringResource(R.string.connection_saved)
    val errorKey = stringResource(R.string.connection_error_key)
    val errorModel = stringResource(R.string.connection_error_model)
    val errorBaseUrl = stringResource(R.string.connection_error_base_url)
    val testFailedTemplate = stringResource(R.string.connection_test_failed)
    val testSuccessTemplate = stringResource(R.string.connection_test_success)
    val modelsFailedTemplate = stringResource(R.string.connection_models_failed)
    val modelsRefreshedTemplate = stringResource(R.string.connection_models_refreshed)

    fun draft(): Connection = Connection(
        id = id,
        providerId = provider?.id.orEmpty(),
        label = label.trim().ifEmpty { provider?.label.orEmpty() },
        model = model.trim(),
        enabled = enabled,
        priority = priority.toIntOrNull() ?: Connection.DEFAULT_PRIORITY,
        baseUrlOverride = baseUrl.trim()
            .takeIf { it.isNotEmpty() && it != provider?.baseUrl },
        discoveredModels = discovered,
        createdAtMillis = existing?.createdAtMillis ?: 0L
    )

    val state = ConnectionEditState(
        providers = providers,
        provider = provider,
        label = label,
        apiKey = apiKey,
        keyHint = keyHint,
        model = model,
        modelOptions = modelOptions(provider, discovered),
        baseUrl = baseUrl,
        priority = priority,
        enabled = enabled,
        isNew = existing == null,
        testing = testing,
        refreshing = refreshing
    )

    val actions = ConnectionEditActions(
        onProviderChange = { providerId ->
            catalog.find(providerId)?.let { selected ->
                val previous = provider
                if (label.isBlank() || label == previous?.label) label = selected.label
                if (baseUrl.isBlank() || baseUrl == previous?.baseUrl) baseUrl = selected.baseUrl
                model = defaultModel(selected)
                discovered = emptyList()
                provider = selected
            }
        },
        onLabelChange = { value -> label = value },
        onKeyChange = { value -> apiKey = value },
        onModelChange = { value -> model = value },
        onBaseUrlChange = { value -> baseUrl = value },
        onPriorityChange = { value -> priority = value.filter { it.isDigit() } },
        onEnabledChange = { value -> enabled = value },
        onRefreshModels = {
            refreshing = true
            scope.launch {
                when (val result = tester.fetchModels(draft(), apiKey)) {
                    is ModelFetchResult.Success -> {
                        discovered = result.models
                        onMessage(modelsRefreshedTemplate.format(result.models.size))
                    }

                    is ModelFetchResult.Failure ->
                        onMessage(modelsFailedTemplate.format(result.failure.message))
                }
                refreshing = false
            }
        },
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
                    if (apiKey.isNotBlank()) credentials.putApiKey(id, apiKey)
                    val hasKey = credentials.hasKey(id)
                    store.upsert(
                        draft().copy(
                            keyHint = credentials.hint(id),
                            health = if (!selected.requiresKey || hasKey) {
                                ConnectionHealth.READY
                            } else {
                                ConnectionHealth.NEEDS_KEY
                            },
                            healthDetail = null
                        )
                    )
                    onMessage(savedMessage)
                    onBack()
                }
            }
        },
        onOpenKeyUrl = { url ->
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (error: ActivityNotFoundException) {
                onMessage(url)
            }
        },
        onBack = onBack
    )

    ConnectionEditScreen(state = state, actions = actions, modifier = modifier)
}

private fun defaultModel(provider: ProviderEntry?): String =
    provider?.models?.firstOrNull()?.id.orEmpty()

private fun modelOptions(provider: ProviderEntry?, discovered: List<String>): List<String> {
    val catalogModels = provider?.models?.map { it.id }.orEmpty()
    return (catalogModels + discovered).distinct()
}
