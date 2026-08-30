package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RiskLevel

data class ProviderRowState(
    val connection: Connection,
    val providerId: String,
    val providerLabel: String,
    val tier: ProviderTier,
    val authKind: AuthKind,
    val risk: RiskLevel,
    val keyHint: String?,
    val acceptsKey: Boolean,
    val isActive: Boolean
)

@Composable
fun ProvidersScreen(
    rows: List<ProviderRowState>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        AiScreenHeader(title = stringResource(R.string.providers_title), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.providers_empty_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.providers_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AuthKind.entries.forEach { authKind ->
                val group = rows.filter { it.authKind == authKind }
                if (group.isNotEmpty()) {
                    Text(
                        text = authKindSectionLabel(authKind),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    group.forEach { row ->
                        ConnectionCard(
                            row = row,
                            onEdit = onEdit,
                            onActivate = onActivate,
                            onDelete = onDelete
                        )
                    }
                }
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.providers_add))
        }
    }
}

@Composable
private fun ConnectionCard(
    row: ProviderRowState,
    onEdit: (String) -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val connection = row.connection
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(connection.id) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = connection.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.providers_subtitle,
                    row.providerLabel,
                    connection.model
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.providers_meta,
                    tierLabel(row.tier),
                    authKindLabel(row.authKind)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusLines(row = row)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = credentialSummary(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onDelete(connection.id) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.providers_delete)
                    )
                }
            }
            ActiveRow(row = row, onActivate = onActivate)
        }
    }
}

@Composable
private fun credentialSummary(row: ProviderRowState): String {
    if (!row.acceptsKey) return stringResource(R.string.providers_no_credential_needed)
    val hint = row.keyHint ?: return stringResource(R.string.providers_no_key)
    return stringResource(R.string.providers_key_hint, hint)
}

@Composable
private fun ActiveRow(row: ProviderRowState, onActivate: (String) -> Unit) {
    if (row.isActive) {
        Text(
            text = stringResource(R.string.providers_active),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        return
    }
    TextButton(onClick = { onActivate(row.connection.id) }) {
        Text(text = stringResource(R.string.providers_set_active))
    }
}

@Composable
private fun StatusLines(row: ProviderRowState) {
    val connection = row.connection
    val attention = connection.health == ConnectionHealth.NEEDS_ATTENTION
    Text(
        text = healthLabel(connection.health),
        style = MaterialTheme.typography.labelLarge,
        color = if (attention) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    )
    connection.healthDetail?.let { detail ->
        Text(
            text = stringResource(R.string.providers_detail, detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    riskNotice(row.risk)?.let { notice ->
        StatusNote(text = stringResource(notice))
    }
}

@Composable
private fun StatusNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
