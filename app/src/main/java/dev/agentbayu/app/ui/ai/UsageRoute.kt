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

@Composable
fun AiUsageRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember(context) { AppGraph.usage(context) }
    val store = remember(context) { AppGraph.connections(context) }
    val stats by tracker.stats.collectAsState()
    val connections by store.connections.collectAsState()
    val resetMessage = stringResource(R.string.usage_reset_done)

    val rows = stats.entries
        .sortedByDescending { it.value.requests }
        .map { entry ->
            val connection = connections.firstOrNull { it.id == entry.key }
            UsageRowState(
                connectionLabel = connection?.label ?: entry.key,
                model = connection?.model.orEmpty(),
                stats = entry.value
            )
        }

    UsageScreen(
        rows = rows,
        totalRequests = rows.sumOf { it.stats.requests },
        totalTokens = rows.sumOf { it.stats.totalTokens },
        totalCost = rows.sumOf { it.stats.costUsd },
        onBack = onBack,
        onReset = {
            tracker.reset()
            onMessage(resetMessage)
        },
        modifier = modifier
    )
}
