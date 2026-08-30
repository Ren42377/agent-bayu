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
import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage

@Composable
fun ReplyDetailSheet(
    detail: ReplyDetail,
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
                DetailRow(
                    label = stringResource(R.string.route_provider),
                    value = detail.providerLabel
                )
                DetailRow(label = stringResource(R.string.route_model), value = detail.model)
                DetailRow(
                    label = stringResource(R.string.route_connection),
                    value = detail.connectionLabel
                )
                DetailRow(
                    label = stringResource(R.string.route_auth),
                    value = authKindLabel(detail.authKind)
                )
                DetailRow(
                    label = stringResource(R.string.route_first_token),
                    value = stringResource(R.string.route_millis, detail.firstTokenMillis)
                )
                DetailRow(
                    label = stringResource(R.string.route_total_time),
                    value = stringResource(R.string.route_millis, detail.totalMillis)
                )
                UsageRows(usage = usage)
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
        value = if (usage.estimated) {
            stringResource(
                R.string.route_tokens_estimated,
                usage.inputTokens,
                usage.outputTokens
            )
        } else {
            stringResource(
                R.string.route_tokens_value,
                usage.inputTokens,
                usage.outputTokens
            )
        }
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
