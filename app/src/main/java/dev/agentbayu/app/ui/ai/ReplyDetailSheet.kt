package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.backdrops.emptyBackdrop
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ReplyDetail
import dev.agentbayu.app.ai.TokenUsage
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalGlassStyle
import dev.agentbayu.app.ui.theme.liquidGlass
import dev.agentbayu.app.ui.theme.solidGlassStyle

@Composable
fun ReplyDetailSheet(
    detail: ReplyDetail,
    usage: TokenUsage?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(
            LocalGlassBackdrop provides emptyBackdrop(),
            LocalGlassStyle provides solidGlassStyle()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = GlassCardShape)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = MAX_SHEET_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.route_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
                    Spacer(modifier = Modifier.height(6.dp))
                    GlassButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.route_close),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRows(usage: TokenUsage?) {
    if (usage == null) return
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f)
        )
    }
}

private val MAX_SHEET_HEIGHT = 520.dp
