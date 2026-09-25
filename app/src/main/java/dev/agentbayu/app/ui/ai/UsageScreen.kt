package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.QuotaParser
import dev.agentbayu.app.ai.QuotaSnapshot
import dev.agentbayu.app.ai.QuotaWindow
import dev.agentbayu.app.ai.UsageStats
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

data class UsageRowState(
    val connectionId: String,
    val label: String,
    val providerLabel: String,
    val model: String,
    val plan: String?,
    val email: String?,
    val quota: QuotaSnapshot?,
    val stats: UsageStats
)

@Composable
fun UsageScreen(
    rows: List<UsageRowState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    updatedText: (Long) -> String,
    resetText: (Long) -> String,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(title = stringResource(R.string.usage_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassSurface(shape = GlassCardShape)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            rows.forEach { row -> UsageCard(row = row, updatedText = updatedText, resetText = resetText) }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp + insets.calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.usage_probe_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            GlassButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = !refreshing,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = stringResource(
                        if (refreshing) R.string.usage_refreshing else R.string.usage_refresh
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun UsageCard(
    row: UsageRowState,
    updatedText: (Long) -> String,
    resetText: (Long) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = GlassCardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.usage_subtitle, row.providerLabel, row.model),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        row.email?.let { email ->
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        row.plan?.let { plan ->
            Text(
                text = stringResource(R.string.usage_plan, plan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val quota = row.quota
        if (quota == null) {
            Text(
                text = stringResource(R.string.usage_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            quota.windows.forEach { window ->
                QuotaBar(window = window, resetText = resetText)
            }
            if (quota.updatedAtMillis > 0L) {
                Text(
                    text = stringResource(R.string.usage_updated, updatedText(quota.updatedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        LocalUsageBlock(stats = row.stats)
    }
}

@Composable
private fun QuotaBar(window: QuotaWindow, resetText: (Long) -> String) {
    val percent = window.percentUsed
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = windowLabel(window.id),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (percent != null) {
                    stringResource(R.string.usage_percent, percent.toInt())
                } else {
                    stringResource(R.string.usage_window_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        ) {
            if (percent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(((percent / 100.0).coerceIn(0.0, 1.0)).toFloat())
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        window.resetAtMillis?.let { reset ->
            Text(
                text = resetText(reset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun LocalUsageBlock(stats: UsageStats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.usage_local_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = stringResource(
                R.string.usage_local_requests,
                stats.requests,
                stats.failures
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(
                R.string.usage_local_tokens,
                formatTokens(stats.inputTokens.toInt()),
                formatTokens(stats.outputTokens.toInt())
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        formatCost(stats.costUsd)?.let { cost ->
            Text(
                text = stringResource(R.string.usage_local_cost, cost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun windowLabel(id: String): String = stringResource(
    when (id) {
        QuotaParser.WINDOW_7D -> R.string.usage_window_7d
        else -> R.string.usage_window_5h
    }
)
