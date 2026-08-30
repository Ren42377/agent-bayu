package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.window.Dialog
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AuthKind

data class ProviderOption(
    val connectionId: String,
    val label: String,
    val providerLabel: String,
    val model: String,
    val models: List<String>,
    val authKind: AuthKind,
    val isActive: Boolean,
    val ready: Boolean
)

@Composable
fun ProviderPickerDialog(
    options: List<ProviderOption>,
    onSelect: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = MAX_PICKER_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
                if (options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                options.forEach { option ->
                    OptionRow(
                        option = option,
                        onSelect = {
                            onSelect(option.connectionId)
                            onDismiss()
                        }
                    )
                    if (option.isActive) {
                        ModelList(
                            option = option,
                            onSelectModel = { modelId ->
                                onSelectModel(option.connectionId, modelId)
                                onDismiss()
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onManage) {
                        Text(text = stringResource(R.string.picker_manage))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.picker_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(option: ProviderOption, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = option.ready, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (option.ready) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = stringResource(
                    R.string.picker_subtitle,
                    option.providerLabel,
                    option.model
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (option.ready) {
                    authKindLabel(option.authKind)
                } else {
                    stringResource(R.string.picker_not_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (option.ready) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        if (option.isActive) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = stringResource(R.string.providers_active),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ModelList(option: ProviderOption, onSelectModel: (String) -> Unit) {
    if (option.models.isEmpty()) return
    Text(
        text = stringResource(R.string.picker_model_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    option.models.forEach { modelId ->
        val selected = modelId == option.model
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectModel(modelId) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = modelId,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.picker_model_selected),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private val MAX_PICKER_HEIGHT = 480.dp
