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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.agentbayu.app.ai.AuthKind
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RiskLevel
import dev.agentbayu.app.ui.components.GlassPill
import dev.agentbayu.app.ui.theme.AppleGreenLight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.liquidGlass

data class ProviderRowState(
    val connection: Connection,
    val providerId: String,
    val providerLabel: String,
    val tier: ProviderTier,
    val authKind: AuthKind,
    val risk: RiskLevel,
    val keyHint: String?,
    val acceptsKey: Boolean,
    val hasCredential: Boolean,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(shape = GlassCardShape)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.providers_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.providers_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            AuthKind.entries.forEach { authKind ->
                val group = rows.filter { it.authKind == authKind }
                if (group.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = authKindSectionLabel(authKind).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp)
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
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.providers_add),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassCardShape)
            .clickable { onEdit(connection.id) }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = connection.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (row.isActive) {
                    GlassPill(
                        text = stringResource(R.string.providers_active),
                        containerColor = AppleGreenLight.copy(alpha = 0.18f),
                        contentColor = AppleGreenLight
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.providers_subtitle,
                    row.providerLabel,
                    connection.model
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.providers_meta,
                    tierLabel(row.tier),
                    authKindLabel(row.authKind)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            StatusLines(row = row)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = credentialSummary(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!row.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(CapsuleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { onActivate(connection.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.providers_set_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(
                    onClick = { onDelete(connection.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.providers_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun credentialSummary(row: ProviderRowState): String {
    if (row.authKind.isOAuth) {
        return if (row.hasCredential) {
            stringResource(R.string.providers_login_saved)
        } else {
            stringResource(R.string.providers_login_needed)
        }
    }
    if (!row.acceptsKey) return stringResource(R.string.providers_no_credential_needed)
    val hint = row.keyHint ?: return stringResource(R.string.providers_no_key)
    return stringResource(R.string.providers_key_hint, hint)
}

@Composable
private fun StatusLines(row: ProviderRowState) {
    val connection = row.connection
    val attention = connection.health == ConnectionHealth.NEEDS_ATTENTION
    Text(
        text = healthLabel(connection.health),
        style = MaterialTheme.typography.labelMedium,
        color = if (attention) {
            AppleRedLight
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
        Text(
            text = stringResource(notice),
            style = MaterialTheme.typography.bodySmall,
            color = AppleRedLight
        )
    }
}
