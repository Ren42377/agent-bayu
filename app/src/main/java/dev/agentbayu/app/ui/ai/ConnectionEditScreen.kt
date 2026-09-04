package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ModelEntry
import dev.agentbayu.app.ai.ProviderEntry
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassDropdownMenuHost
import dev.agentbayu.app.ui.components.GlassDropdownMenuItem
import dev.agentbayu.app.ui.components.InteractiveHighlight
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface
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
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
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
                    onSelect = actions.onProviderChange,
                    selectedId = state.provider?.id
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
        ActionBar(
            state = state,
            actions = actions,
            bottomInset = insets.calculateBottomPadding()
        )
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
                .glassSurface(shape = GlassCardShape)
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
            provider.browserLogin != null -> LoginFields(
                state = state,
                actions = actions,
                loginLabel = stringResource(R.string.connection_login_browser),
                pendingLabel = stringResource(R.string.connection_login_browser_needed)
            )

            provider.deviceLogin != null -> LoginFields(
                state = state,
                actions = actions,
                loginLabel = stringResource(R.string.connection_login),
                pendingLabel = stringResource(R.string.connection_login_needed)
            )

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
private fun LoginFields(
    state: ConnectionEditState,
    actions: ConnectionEditActions,
    loginLabel: String,
    pendingLabel: String
) {
    Text(
        text = if (state.loggedIn) {
            stringResource(R.string.connection_login_saved)
        } else {
            pendingLabel
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GlassButton(
        onClick = actions.onLogin,
        tint = MaterialTheme.colorScheme.primary,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (state.loggedIn) {
                stringResource(R.string.connection_login_again)
            } else {
                loginLabel
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary
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
        GlassButton(
            onClick = { actions.onOpenKeyUrl(url) },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.connection_key_source, provider.label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
                onSelect = actions.onModelChange,
                selectedId = state.model
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
                GlassButton(
                    onClick = actions.onRefreshModels,
                    enabled = !state.refreshing,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.connection_refresh_models),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                GlassButton(
                    onClick = actions.onProbeModels,
                    enabled = !state.probing && state.modelOptions.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        text = stringResource(R.string.connection_probe_models),
                        style = MaterialTheme.typography.labelMedium
                    )
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
private fun ActionBar(
    state: ConnectionEditState,
    actions: ConnectionEditActions,
    bottomInset: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp + bottomInset
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassButton(
            onClick = actions.onTest,
            modifier = Modifier.weight(1f),
            enabled = !state.testing,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (state.testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
        GlassButton(
            onClick = actions.onSave,
            modifier = Modifier.weight(1f),
            tint = MaterialTheme.colorScheme.primary,
            contentPadding = PaddingValues(vertical = 12.dp)
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
    modifier: Modifier = Modifier,
    selectedId: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope, claimDrag = false)
    }
    GlassDropdownMenuHost(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
        trigger = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(shape = GlassTileShape)
                    .clip(GlassTileShape)
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = { expanded = true }
                    )
                    .then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier)
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
        },
        menuContent = {
            options.forEach { option ->
                GlassDropdownMenuItem(
                    label = option.second,
                    selected = option.first == selectedId,
                    onClick = {
                        expanded = false
                        onSelect(option.first)
                    }
                )
            }
        }
    )
}
