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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.ProviderEntry
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.liquidGlass

data class ConnectionEditState(
    val providers: List<ProviderEntry>,
    val provider: ProviderEntry?,
    val label: String,
    val apiKey: String,
    val keyHint: String?,
    val model: String,
    val modelOptions: List<String>,
    val modelProbes: Map<String, String>,
    val baseUrl: String,
    val isNew: Boolean,
    val loggedIn: Boolean = false,
    val testing: Boolean = false,
    val refreshing: Boolean = false,
    val probing: Boolean = false
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
    val onRefreshModels: () -> Unit,
    val onProbeModels: () -> Unit,
    val onTest: () -> Unit,
    val onSave: () -> Unit,
    val onLogin: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormSection(title = stringResource(R.string.connection_provider_section)) {
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
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CredentialSection(state = state, actions = actions)
            ModelSection(state = state, actions = actions)
            AdvancedSection(state = state, actions = actions)
        }
        ActionBar(state = state, actions = actions)
    }
}

@Composable
private fun FormSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape = GlassCardShape)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun CredentialSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    FormSection(title = stringResource(R.string.connection_credential_section)) {
        ProviderNotes(provider = provider)
        when {
            provider.deviceLogin != null -> DeviceLoginFields(state = state, actions = actions)
            !provider.acceptsKey -> Text(
                text = stringResource(R.string.connection_key_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> ApiKeyFields(state = state, actions = actions, provider = provider)
        }
    }
}

@Composable
private fun DeviceLoginFields(state: ConnectionEditState, actions: ConnectionEditActions) {
    Text(
        text = if (state.loggedIn) {
            stringResource(R.string.connection_login_saved)
        } else {
            stringResource(R.string.connection_login_needed)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(
        modifier = Modifier
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = actions.onLogin)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (state.loggedIn) {
                stringResource(R.string.connection_login_again)
            } else {
                stringResource(R.string.connection_login)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ApiKeyFields(
    state: ConnectionEditState,
    actions: ConnectionEditActions,
    provider: ProviderEntry
) {
    if (!provider.requiresKey) {
        Text(
            text = stringResource(R.string.connection_key_optional),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutlinedTextField(
        value = state.apiKey,
        onValueChange = actions.onKeyChange,
        label = { Text(text = stringResource(R.string.connection_key_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
    provider.keyUrl?.let { url ->
        Box(
            modifier = Modifier
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { actions.onOpenKeyUrl(url) }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.connection_key_source, provider.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ProviderNotes(provider: ProviderEntry) {
    Text(
        text = stringResource(
            R.string.connection_auth_kind,
            authKindLabel(provider.authKind)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    providerHint(provider.id)?.let { hint ->
        Text(
            text = stringResource(hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    riskNotice(provider.risk)?.let { notice ->
        Text(
            text = stringResource(notice),
            style = MaterialTheme.typography.bodySmall,
            color = AppleRedLight
        )
    }
}

@Composable
private fun ModelSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    FormSection(title = stringResource(R.string.connection_model_section)) {
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
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = !state.refreshing, onClick = actions.onRefreshModels)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(R.string.connection_refresh_models),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CapsuleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            enabled = !state.probing && state.modelOptions.isNotEmpty(),
                            onClick = actions.onProbeModels
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.probing) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(R.string.connection_probe_models),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        ModelProbeList(state = state)
    }
}

@Composable
private fun ModelProbeList(state: ConnectionEditState) {
    if (state.modelProbes.isEmpty()) return
    Text(
        text = stringResource(R.string.connection_probe_result_title),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    state.modelOptions.forEach { modelId ->
        val status = state.modelProbes[modelId] ?: return@forEach
        Text(
            text = stringResource(R.string.connection_probe_result_line, modelId, status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdvancedSection(state: ConnectionEditState, actions: ConnectionEditActions) {
    val provider = state.provider ?: return
    if (!provider.editableBaseUrl) return
    FormSection(title = stringResource(R.string.connection_advanced_section)) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = actions.onBaseUrlChange,
            label = { Text(text = stringResource(R.string.connection_base_url_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActionBar(state: ConnectionEditState, actions: ConnectionEditActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = !state.testing, onClick = actions.onTest)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.connection_test_running),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = stringResource(R.string.connection_test),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = actions.onSave)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.connection_save),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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
