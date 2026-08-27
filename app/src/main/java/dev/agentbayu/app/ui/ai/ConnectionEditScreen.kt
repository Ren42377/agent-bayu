package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.ProviderEntry

data class ConnectionEditState(
    val providers: List<ProviderEntry>,
    val provider: ProviderEntry?,
    val label: String,
    val apiKey: String,
    val keyHint: String?,
    val model: String,
    val modelOptions: List<String>,
    val baseUrl: String,
    val priority: String,
    val weight: String,
    val enabled: Boolean,
    val isNew: Boolean,
    val testing: Boolean = false,
    val refreshing: Boolean = false
) {
    val modelEntry: ModelEntry?
        get() = provider?.model(model)
}

data class ConnectionEditActions(
    val onProviderChange: (String) -> Unit,
    val onLabelChange: (String) -> Unit,
    val onKeyChange: (String) -> Unit,
    val onModelChange: (String) -> Unit,
    val onBaseUrlChange: (String) -> Unit,
    val onPriorityChange: (String) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onRefreshModels: () -> Unit,
    val onTest: () -> Unit,
    val onSave: () -> Unit,
    val onOpenKeyUrl: (String) -> Unit,
    val onBack: () -> Unit
)

@Composable
fun ConnectionEditScreen(
    state: ConnectionEditState,
    actions: ConnectionEditActions,
    modifier: Modifier = Modifier
) {
    val title = if (state.isNew) {
        stringResource(R.string.connection_new_title)
    } else {
        stringResource(R.string.connection_edit_title)
    }
    Column(modifier = modifier.fillMaxSize()) {
        AiScreenHeader(title = title, onBack = actions.onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel(text = stringResource(R.string.connection_provider_section))
            AiDropdown(
                selectedLabel = state.provider?.label.orEmpty(),
                options = state.providers.map { it.id to it.label },
                onSelect = actions.onProviderChange
            )
            OutlinedTextField(
                value = state.label,
                onValueChange = actions.onLabelChange,
                label = { Text(text = stringResource(R.string.connection_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CredentialSection(state = state, actions = actions)
            ModelSection(state = state, actions = actions)
            AdvancedSection(state = state, actions = actions)
        }
        ActionBar(state = state, actions = actions)
    }
}

@Composable
private fun CredentialSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    SectionLabel(text = stringResource(R.string.connection_credential_section))
    if (!provider.requiresKey) {
        Text(
            text = stringResource(R.string.connection_key_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = actions.onKeyChange,
        label = { Text(text = stringResource(R.string.connection_key_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    state.keyHint?.let { hint ->
        Text(
            text = stringResource(R.string.connection_key_saved, hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.connection_key_replace),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    provider.keyUrl?.let { url ->
        OutlinedButton(onClick = { actions.onOpenKeyUrl(url) }) {
            Text(text = stringResource(R.string.connection_key_source, provider.label))
        }
    }
}

@Composable
private fun ModelSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    SectionLabel(text = stringResource(R.string.connection_model_section))
    if (state.modelOptions.isNotEmpty()) {
        AiDropdown(
            selectedLabel = state.model,
            options = state.modelOptions.map { it to it },
            onSelect = actions.onModelChange
        )
    }
    if (provider.allowCustomModel || state.modelOptions.isEmpty()) {
        OutlinedTextField(
            value = state.model,
            onValueChange = actions.onModelChange,
            label = { Text(text = stringResource(R.string.connection_custom_model_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    state.modelEntry?.let { entry ->
        Text(
            text = stringResource(R.string.connection_model_context, formatTokens(entry.contextLength)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val price = if (entry.free) {
            stringResource(R.string.connection_model_free)
        } else {
            val input = formatCost(entry.inputPricePerMillion)
            val output = formatCost(entry.outputPricePerMillion)
            if (input != null && output != null) {
                stringResource(R.string.connection_model_price, input, output)
            } else {
                null
            }
        }
        price?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (provider.modelsPath != null) {
        OutlinedButton(onClick = actions.onRefreshModels, enabled = !state.refreshing) {
            if (state.refreshing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.connection_refresh_models))
        }
    }
}

@Composable
private fun AdvancedSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    SectionLabel(text = stringResource(R.string.connection_advanced_section))
    if (provider.editableBaseUrl) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = actions.onBaseUrlChange,
            label = { Text(text = stringResource(R.string.connection_base_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.priority,
            onValueChange = actions.onPriorityChange,
            label = { Text(text = stringResource(R.string.connection_priority_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.weight,
            onValueChange = actions.onWeightChange,
            label = { Text(text = stringResource(R.string.connection_weight_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.connection_enabled),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = state.enabled, onCheckedChange = actions.onEnabledChange)
    }
}

@Composable
private fun ActionBar(state: ConnectionEditState, actions: ConnectionEditActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = actions.onTest,
            enabled = !state.testing,
            modifier = Modifier.weight(1f)
        ) {
            if (state.testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.connection_test_running))
            } else {
                Text(text = stringResource(R.string.connection_test))
            }
        }
        Button(onClick = actions.onSave, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.connection_save))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
fun AiDropdown(
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = selectedLabel, modifier = Modifier.weight(1f))
            Icon(painter = painterResource(R.drawable.ic_chevron), contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.second) },
                    onClick = {
                        expanded = false
                        onSelect(option.first)
                    }
                )
            }
        }
    }
}
