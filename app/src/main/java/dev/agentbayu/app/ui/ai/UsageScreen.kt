package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.UsageStats

data class UsageRowState(
    val connectionLabel: String,
    val model: String,
    val stats: UsageStats
)

@Composable
fun UsageScreen(
    rows: List<UsageRowState>,
    totalRequests: Int,
    totalTokens: Long,
    totalCost: Double,
    onBack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        AiScreenHeader(title = stringResource(R.string.usage_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.usage_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.usage_total,
                        totalRequests,
                        totalTokens,
                        stringResource(R.string.cost_value, formatCost(totalCost).orEmpty())
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                rows.forEach { row -> UsageCard(row = row) }
            }
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(text = stringResource(R.string.usage_reset))
        }
    }
}

@Composable
private fun UsageCard(row: UsageRowState) {
    val stats = row.stats
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringResource(R.string.providers_subtitle, row.connectionLabel, row.model),
                style = MaterialTheme.typography.titleMedium
            )
            UsageLine(
                text = stringResource(
                    R.string.usage_requests,
                    stats.requests,
                    stats.successes,
                    stats.failures
                )
            )
            UsageLine(
                text = stringResource(R.string.usage_tokens, stats.inputTokens, stats.outputTokens)
            )
            UsageLine(
                text = stringResource(
                    R.string.usage_cost,
                    stringResource(R.string.cost_value, formatCost(stats.costUsd).orEmpty())
                )
            )
            UsageLine(
                text = if (stats.p95FirstTokenMillis > 0L) {
                    stringResource(
                        R.string.usage_latency,
                        stats.firstTokenEwmaMillis.toLong(),
                        stats.p95FirstTokenMillis
                    )
                } else {
                    stringResource(R.string.usage_latency_empty)
                }
            )
            stats.lastFailure?.let { failure ->
                Text(
                    text = stringResource(R.string.usage_last_failure, failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun UsageLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
