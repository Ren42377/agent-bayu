package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.RouteDecision
import dev.agentbayu.app.ai.SkipReason
import dev.agentbayu.app.ai.SkippedCandidate
import dev.agentbayu.app.ai.TokenUsage

@Composable
fun RouteDetailSheet(
    decision: RouteDecision,
    usage: TokenUsage?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = MAX_SHEET_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.route_title),
                    style = MaterialTheme.typography.titleLarge
                )
                DetailRow(label = stringResource(R.string.route_channel), value = decision.channel)
                DetailRow(label = stringResource(R.string.route_strategy), value = decision.strategy)
                DetailRow(
                    label = stringResource(R.string.route_provider),
                    value = decision.providerLabel
                )
                DetailRow(label = stringResource(R.string.route_model), value = decision.model)
                DetailRow(
                    label = stringResource(R.string.route_connection),
                    value = decision.connectionLabel
                )
                DetailRow(
                    label = stringResource(R.string.route_tier),
                    value = tierLabel(decision.tier)
                )
                Text(
                    text = stringResource(
                        R.string.route_attempt,
                        decision.attempt,
                        decision.candidatesConsidered
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DetailRow(
                    label = stringResource(R.string.route_first_token),
                    value = stringResource(R.string.route_millis, decision.firstTokenMillis)
                )
                DetailRow(
                    label = stringResource(R.string.route_total_time),
                    value = stringResource(R.string.route_millis, decision.totalMillis)
                )
                UsageRows(usage = usage)
                if (decision.degraded) {
                    Text(
                        text = stringResource(R.string.route_degraded),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                SkippedRows(skipped = decision.skipped)
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.route_close))
                }
            }
        }
    }
}

@Composable
private fun UsageRows(usage: TokenUsage?) {
    if (usage == null) return
    DetailRow(
        label = stringResource(R.string.route_tokens),
        value = stringResource(
            R.string.route_tokens_value,
            usage.inputTokens,
            usage.outputTokens
        )
    )
    val cost = formatCost(usage.estimatedCostUsd)
    DetailRow(
        label = stringResource(R.string.route_cost),
        value = if (cost == null) {
            stringResource(R.string.route_cost_unknown)
        } else {
            stringResource(R.string.cost_value, cost)
        }
    )
}

@Composable
private fun SkippedRows(skipped: List<SkippedCandidate>) {
    if (skipped.isEmpty()) return
    Text(
        text = stringResource(R.string.route_skipped),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
    skipped.forEach { candidate ->
        Text(
            text = stringResource(
                R.string.route_skipped_row,
                candidate.connectionLabel,
                candidate.model,
                skipDetailLabel(candidate)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun skipDetailLabel(candidate: SkippedCandidate): String {
    val reason = skipReasonLabel(candidate.reason)
    val detail = candidate.detail ?: return reason
    val template = when (candidate.reason) {
        SkipReason.BREAKER_OPEN, SkipReason.COOLDOWN, SkipReason.MODEL_LOCKED ->
            R.string.skip_detail

        else -> R.string.skip_detail_status
    }
    return stringResource(template, reason, detail)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.4f)
        )
    }
}

private val MAX_SHEET_HEIGHT = 520.dp
