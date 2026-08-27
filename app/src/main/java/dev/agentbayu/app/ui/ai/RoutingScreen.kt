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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.Combo
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ProviderTier

data class RoutingChannelOption(
    val channel: String,
    val title: String,
    val subtitle: String? = null
)

data class RoutingPreviewRow(
    val connectionLabel: String,
    val model: String,
    val strategy: String
)

data class RoutingScreenState(
    val activeChannel: String,
    val autoOptions: List<RoutingChannelOption>,
    val comboOptions: List<RoutingChannelOption>,
    val connectionOptions: List<RoutingChannelOption>,
    val editableCombos: List<Combo>,
    val connections: List<Connection>,
    val strategies: List<String>,
    val preview: List<RoutingPreviewRow>
)

data class RoutingActions(
    val onBack: () -> Unit,
    val onSelectChannel: (String) -> Unit,
    val onAddCombo: () -> Unit,
    val onDeleteCombo: (String) -> Unit,
    val onAddStep: (String) -> Unit,
    val onRemoveStep: (String, Int) -> Unit,
    val onStepStrategy: (String, Int, String) -> Unit,
    val onStepTier: (String, Int, ProviderTier?) -> Unit,
    val onToggleStepConnection: (String, Int, String) -> Unit
)

@Composable
fun RoutingScreen(
    state: RoutingScreenState,
    actions: RoutingActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        AiScreenHeader(title = stringResource(R.string.routing_title), onBack = actions.onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RoutingSectionTitle(text = stringResource(R.string.routing_auto_section))
            state.autoOptions.forEach { option ->
                ChannelRow(
                    option = option,
                    selected = option.channel == state.activeChannel,
                    onSelect = actions.onSelectChannel
                )
            }

            RoutingSectionTitle(text = stringResource(R.string.routing_combo_section))
            state.comboOptions.forEach { option ->
                ChannelRow(
                    option = option,
                    selected = option.channel == state.activeChannel,
                    onSelect = actions.onSelectChannel
                )
            }
            state.editableCombos.forEach { combo ->
                ComboEditor(
                    combo = combo,
                    connections = state.connections,
                    strategies = state.strategies,
                    actions = actions
                )
            }
            OutlinedButton(onClick = actions.onAddCombo) {
                Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.routing_combo_add))
            }

            RoutingSectionTitle(text = stringResource(R.string.routing_connection_section))
            if (state.connectionOptions.isEmpty()) {
                Text(
                    text = stringResource(R.string.routing_no_connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.connectionOptions.forEach { option ->
                ChannelRow(
                    option = option,
                    selected = option.channel == state.activeChannel,
                    onSelect = actions.onSelectChannel
                )
            }

            PreviewSection(preview = state.preview)
        }
    }
}

@Composable
private fun ChannelRow(
    option: RoutingChannelOption,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(option.channel) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = { onSelect(option.channel) })
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = option.title, style = MaterialTheme.typography.bodyLarge)
            option.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewSection(preview: List<RoutingPreviewRow>) {
    RoutingSectionTitle(text = stringResource(R.string.routing_preview_section))
    if (preview.isEmpty()) {
        Text(
            text = stringResource(R.string.routing_preview_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    preview.forEachIndexed { index, row ->
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(
                text = stringResource(
                    R.string.routing_preview_row,
                    index + 1,
                    row.connectionLabel,
                    row.model
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.routing_preview_strategy, row.strategy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComboEditor(
    combo: Combo,
    connections: List<Connection>,
    strategies: List<String>,
    actions: RoutingActions
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = combo.label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { actions.onDeleteCombo(combo.id) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.routing_combo_delete)
                    )
                }
            }
            combo.steps.forEachIndexed { index, step ->
                Text(
                    text = stringResource(R.string.routing_step_title, index + 1),
                    style = MaterialTheme.typography.labelLarge
                )
                StepLabel(text = stringResource(R.string.routing_step_strategy))
                AiDropdown(
                    selectedLabel = step.strategy,
                    options = strategies.map { it to it },
                    onSelect = { value -> actions.onStepStrategy(combo.id, index, value) }
                )
                StepLabel(text = stringResource(R.string.routing_step_tier))
                TierDropdown(
                    tier = step.tier,
                    onSelect = { value -> actions.onStepTier(combo.id, index, value) }
                )
                Text(
                    text = if (step.connectionIds.isEmpty()) {
                        stringResource(R.string.routing_step_connections_any)
                    } else {
                        stringResource(R.string.routing_step_connections)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                connections.forEach { connection ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                actions.onToggleStepConnection(combo.id, index, connection.id)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = step.connectionIds.contains(connection.id),
                            onCheckedChange = {
                                actions.onToggleStepConnection(combo.id, index, connection.id)
                            }
                        )
                        Text(text = connection.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(onClick = { actions.onRemoveStep(combo.id, index) }) {
                    Text(text = stringResource(R.string.routing_step_remove))
                }
            }
            OutlinedButton(onClick = { actions.onAddStep(combo.id) }) {
                Text(text = stringResource(R.string.routing_step_add))
            }
        }
    }
}

@Composable
private fun TierDropdown(tier: ProviderTier?, onSelect: (ProviderTier?) -> Unit) {
    val anyLabel = stringResource(R.string.routing_step_tier_any)
    val options = listOf(ANY_TIER to anyLabel) + ProviderTier.entries.map { it.name to tierLabel(it) }
    AiDropdown(
        selectedLabel = tier?.let { tierLabel(it) } ?: anyLabel,
        options = options,
        onSelect = { key ->
            onSelect(ProviderTier.entries.firstOrNull { it.name == key })
        }
    )
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RoutingSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
    )
}

private const val ANY_TIER = "any"
