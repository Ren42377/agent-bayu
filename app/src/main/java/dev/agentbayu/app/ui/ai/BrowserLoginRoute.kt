package dev.agentbayu.app.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.oauth.BrowserCallbackResult
import dev.agentbayu.app.ai.oauth.BrowserLoginResult
import dev.agentbayu.app.ai.oauth.BrowserLoginStartResult
import dev.agentbayu.app.ai.oauth.GoogleCodeFlow
import dev.agentbayu.app.ai.oauth.ProjectBootstrapResult
import dev.agentbayu.app.ui.components.GlassDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

@Composable
fun AiBrowserLoginRoute(
    connectionId: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val credentials = remember(context) { AppGraph.credentials(context) }
    val codeFlow = remember(context) { AppGraph.codeFlow(context) }
    val projectBootstrap = remember(context) { AppGraph.projectBootstrap(context) }
    val clipboard = LocalClipboardManager.current

    val connection = remember(connectionId) { store.find(connectionId) }
    val provider = remember(connection) { connection?.let { catalog.find(it.providerId) } }
    val config = remember(provider) { provider?.browserLogin }

    val unsupportedMessage = stringResource(R.string.browser_unsupported)
    val successMessage = stringResource(R.string.browser_success)
    val projectFailedTemplate = stringResource(R.string.browser_project_failed)
    val linkCopiedMessage = stringResource(R.string.dialog_link_copied)

    var attempt by remember { mutableIntStateOf(0) }
    var ui by remember { mutableStateOf<BrowserLoginUiState>(BrowserLoginUiState.Starting) }
    var authorizeUrl by remember { mutableStateOf<String?>(null) }
    var blockedLink by remember { mutableStateOf<String?>(null) }
    val manualRedirect = remember { MutableStateFlow<String?>(null) }

    LaunchedEffect(connectionId, attempt) {
        ui = BrowserLoginUiState.Starting
        authorizeUrl = null
        manualRedirect.value = null
        if (config == null || provider == null) {
            ui = BrowserLoginUiState.Failed(unsupportedMessage)
            return@LaunchedEffect
        }
        val started = codeFlow.start(config)
        if (started is BrowserLoginStartResult.Failure) {
            ui = BrowserLoginUiState.Failed(started.failure.message)
            return@LaunchedEffect
        }
        val session = (started as BrowserLoginStartResult.Success).session
        authorizeUrl = started.authorizeUrl
        ui = BrowserLoginUiState.Waiting(hasAuthorizeUrl = true)
        try {
            val timeoutMillis = provider.timeoutMillis
                .coerceAtLeast(GoogleCodeFlow.DEFAULT_TIMEOUT_MILLIS)
            val listener: Flow<BrowserCallbackResult> = flow {
                emit(codeFlow.awaitCallback(session, timeoutMillis))
            }
            val pasted: Flow<BrowserCallbackResult> = manualRedirect.filterNotNull().map { value ->
                BrowserCallbackResult.Success(value)
            }
            val callback = merge(listener, pasted).first()
            if (callback is BrowserCallbackResult.Failure) {
                ui = BrowserLoginUiState.Failed(callback.failure.message)
                return@LaunchedEffect
            }
            ui = BrowserLoginUiState.Finishing
            val redirect = (callback as BrowserCallbackResult.Success).redirect
            val exchanged = codeFlow.exchange(config, session, redirect)
            if (exchanged is BrowserLoginResult.Failure) {
                ui = BrowserLoginUiState.Failed(exchanged.failure.message)
                return@LaunchedEffect
            }
            val tokens = (exchanged as BrowserLoginResult.Success).tokens
            credentials.put(connectionId, tokens)
            if (provider.needsProjectBootstrap) {
                val bootstrapped = projectBootstrap.resolve(
                    baseUrl = connection?.baseUrlOverride?.takeIf { it.isNotBlank() }
                        ?: provider.baseUrl,
                    accessToken = tokens.accessToken,
                    extraHeaders = provider.extraHeaders
                )
                when (bootstrapped) {
                    is ProjectBootstrapResult.Failure -> {
                        store.markHealth(
                            connectionId,
                            ConnectionHealth.NEEDS_ATTENTION,
                            bootstrapped.failure.message
                        )
                        ui = BrowserLoginUiState.Failed(
                            projectFailedTemplate.format(bootstrapped.failure.message)
                        )
                        return@LaunchedEffect
                    }

                    is ProjectBootstrapResult.Success ->
                        store.setProjectId(connectionId, bootstrapped.projectId)
                }
            }
            store.markHealth(connectionId, ConnectionHealth.READY, null)
            ui = BrowserLoginUiState.Done
            onMessage(successMessage)
            onBack()
        } finally {
            session.close()
        }
    }

    BrowserLoginScreen(
        providerLabel = provider?.label ?: connection?.label.orEmpty(),
        state = ui,
        actions = BrowserLoginActions(
            onOpenBrowser = {
                authorizeUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (error: ActivityNotFoundException) {
                        blockedLink = url
                    }
                }
            },
            onManualRedirect = { redirect ->
                redirect.trim().takeIf { it.contains('?') }?.let { value ->
                    manualRedirect.value = value
                }
            },
            onRetry = { attempt += 1 },
            onBack = onBack
        ),
        modifier = modifier
    )

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
