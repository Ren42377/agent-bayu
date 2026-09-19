package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RiskLevel
import dev.agentbayu.app.ai.resolveActiveConnection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AiProvidersRoute(
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.connections(context) }
    val catalogRepository = remember(context) { AppGraph.catalogRepository(context) }
    val catalog by catalogRepository.catalog.collectAsState()
    val credentials = remember(context) { AppGraph.credentials(context) }
    val usage = remember(context) { AppGraph.usage(context) }
    val connections by store.connections.collectAsState()
    val activeId by store.activeConnectionId.collectAsState()
    val deletedMessage = stringResource(R.string.providers_deleted)
    val catalogUpdatedMessage = stringResource(R.string.providers_catalog_refreshed)
    val catalogFailedMessage = stringResource(R.string.providers_catalog_failed)

    var catalogRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AppGraph.refreshCatalog(context)
    }

    val rows = remember(connections, activeId, catalog) {
        val active = resolveActiveConnection(connections, activeId)
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            val isOAuth = provider?.authKind?.isOAuth == true
            ProviderRowState(
                connection = connection,
                providerId = connection.providerId,
                providerLabel = provider?.label ?: connection.providerId,
                tier = provider?.tier ?: ProviderTier.API_KEY,
                authKind = provider?.authKind ?: AuthKind.API_KEY,
                risk = provider?.risk ?: RiskLevel.NONE,
                keyHint = if (isOAuth) null else credentials.hint(connection.id),
                acceptsKey = provider?.acceptsKey ?: true,
                hasCredential = credentials.hasKey(connection.id),
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
        onUpdateCatalog = {
            if (!catalogRefreshing) {
                catalogRefreshing = true
                AppGraph.refreshCatalog(context) { updated ->
                    catalogRefreshing = false
                    onMessage(if (updated) catalogUpdatedMessage else catalogFailedMessage)
                }
            }
        },
        catalogRefreshing = catalogRefreshing,
        catalogLastUpdated = catalogLastUpdatedText(),
        modifier = modifier
    )
}

@Composable
private fun catalogLastUpdatedText(): String? {
    val context = LocalContext.current
    val repository = remember(context) { AppGraph.catalogRepository(context) }
    val lastUpdated by repository.lastUpdatedMillis.collectAsState()
    val formatter = remember {
        DateTimeFormatter.ofPattern(CATALOG_TIME_PATTERN, Locale.getDefault())
    }
    val zone = remember { ZoneId.systemDefault() }
    return lastUpdated?.takeIf { it > 0L }?.let { millis ->
        formatter.format(Instant.ofEpochMilli(millis).atZone(zone))
    }
}

private const val CATALOG_TIME_PATTERN = "yyyy-MM-dd HH:mm"
