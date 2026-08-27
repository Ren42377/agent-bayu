package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.Candidate
import dev.agentbayu.app.ai.ProviderTier
import kotlinx.coroutines.delay

@Composable
fun AiProvidersRoute(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalog = remember(context) { AppGraph.catalog(context) }
    val vault = remember(context) { AppGraph.credentials(context) }
    val router = remember(context) { AppGraph.router(context) }
    val usage = remember(context) { AppGraph.usage(context) }
    val connections by store.connections.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    val deletedMessage = stringResource(R.string.providers_deleted)

    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            tick += 1
        }
    }

    val rows = remember(connections, tick) {
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            val health = provider?.let {
                router.health(Candidate(connection, it, it.modelOrFallback(connection.model)))
            }
            ProviderRowState(
                connection = connection,
                providerLabel = provider?.label ?: connection.providerId,
                tier = provider?.tier ?: ProviderTier.API_KEY,
                keyHint = vault.hint(connection.id),
                cooldownRemainingMillis = health?.cooldownRemainingMillis ?: 0L,
                breakerRemainingMillis = health?.breakerOpenRemainingMillis ?: 0L,
                modelLockRemainingMillis = health?.modelLockRemainingMillis ?: 0L
            )
        }
    }

    ProvidersScreen(
        rows = rows,
        onBack = onBack,
        onAdd = { onEdit(null) },
        onEdit = { id -> onEdit(id) },
        onToggle = { id, enabled -> store.setEnabled(id, enabled) },
        onDelete = { id ->
            store.remove(id)
            vault.remove(id)
            usage.forget(id)
            onMessage(deletedMessage)
        },
        modifier = modifier
    )
}

private const val TICK_MILLIS = 1_000L
