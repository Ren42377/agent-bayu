package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.UsageStats
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.liquidGlass

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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.usage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = GlassCardShape)
                        .padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = GlassCardShape)
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(
                                R.string.usage_total,
                                totalRequests,
                                totalTokens,
                                stringResource(R.string.cost_value, formatCost(totalCost).orEmpty())
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                rows.forEach { row -> UsageCard(row = row) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onReset)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.usage_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UsageCard(row: UsageRowState) {
    val stats = row.stats
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassCardShape)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.providers_subtitle, row.connectionLabel, row.model),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                    color = AppleRedLight
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
