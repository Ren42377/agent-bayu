package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RiskLevel
import dev.agentbayu.app.ai.resolveActiveConnection

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
    val credentials = remember(context) { AppGraph.credentials(context) }
    val usage = remember(context) { AppGraph.usage(context) }
    val connections by store.connections.collectAsState()
    val activeId by store.activeConnectionId.collectAsState()
    val deletedMessage = stringResource(R.string.providers_deleted)

    val rows = remember(connections, activeId) {
        val active = resolveActiveConnection(connections, activeId)
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            ProviderRowState(
                connection = connection,
                providerId = connection.providerId,
                providerLabel = provider?.label ?: connection.providerId,
                tier = provider?.tier ?: ProviderTier.API_KEY,
                authKind = provider?.authKind ?: AuthKind.API_KEY,
                risk = provider?.risk ?: RiskLevel.NONE,
                keyHint = credentials.hint(connection.id),
                acceptsKey = provider?.acceptsKey ?: true,
                isActive = connection.id == active?.id
            )
        }
    }

    ProvidersScreen(
        rows = rows,
        onBack = onBack,
        onAdd = { onEdit(null) },
        onEdit = { id -> onEdit(id) },
        onActivate = { id -> store.setActive(id) },
        onDelete = { id ->
            store.remove(id)
            credentials.remove(id)
            usage.forget(id)
            onMessage(deletedMessage)
        },
        modifier = modifier
    )
}
