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
import dev.agentbayu.app.ai.oauth.DeviceCodeResult
import dev.agentbayu.app.ai.oauth.DeviceCodeStartResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AiDeviceCodeRoute(
    connectionId: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val credentials = remember(context) { AppGraph.credentials(context) }
    val deviceFlow = remember(context) { AppGraph.deviceFlow(context) }
    val clipboard = LocalClipboardManager.current

    val connection = remember(connectionId) { store.find(connectionId) }
    val provider = remember(connection) { connection?.let { catalog.find(it.providerId) } }
    val config = remember(provider) { provider?.deviceLogin }

    val unsupportedMessage = stringResource(R.string.device_unsupported)
    val successMessage = stringResource(R.string.device_success)
    val copiedMessage = stringResource(R.string.device_copied)

    var attempt by remember { mutableIntStateOf(0) }
    var ui by remember { mutableStateOf<DeviceCodeUiState>(DeviceCodeUiState.Starting) }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(connectionId, attempt) {
        ui = DeviceCodeUiState.Starting
        code = ""
        if (config == null) {
            ui = DeviceCodeUiState.Failed(unsupportedMessage)
            return@LaunchedEffect
        }
        val started = deviceFlow.start(config)
        if (started is DeviceCodeStartResult.Failure) {
            ui = DeviceCodeUiState.Failed(started.failure.message)
            return@LaunchedEffect
        }
        val start = (started as DeviceCodeStartResult.Success).start
        code = start.userCode
        ui = DeviceCodeUiState.Waiting(
            userCode = start.userCode,
            remainingMillis = remainingOf(start.expiresAtMillis),
            hasVerificationUrl = !config.verificationUrl.isNullOrBlank()
        )
        val ticker = launch {
            while (true) {
                delay(TICK_MILLIS)
                val current = ui as? DeviceCodeUiState.Waiting ?: break
                ui = current.copy(remainingMillis = remainingOf(start.expiresAtMillis))
            }
        }
        val result = deviceFlow.awaitAuthorization(config, start)
        ticker.cancel()
        when (result) {
            is DeviceCodeResult.Failure -> ui = DeviceCodeUiState.Failed(result.failure.message)
            is DeviceCodeResult.Success -> {
                credentials.put(connectionId, result.tokens)
                store.markHealth(connectionId, ConnectionHealth.READY, null)
                ui = DeviceCodeUiState.Done
                onMessage(successMessage)
                onBack()
            }
        }
    }

    DeviceCodeScreen(
        providerLabel = provider?.label ?: connection?.label.orEmpty(),
        state = ui,
        actions = DeviceCodeActions(
            onCopy = {
                if (code.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(code))
                    onMessage(copiedMessage)
                }
            },
            onOpenBrowser = {
                config?.verificationUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (error: ActivityNotFoundException) {
                        onMessage(url)
                    }
                }
            },
            onRetry = { attempt += 1 },
            onBack = onBack
        ),
        modifier = modifier
    )
}

private fun remainingOf(expiresAtMillis: Long): Long =
    (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

private const val TICK_MILLIS = 1_000L
