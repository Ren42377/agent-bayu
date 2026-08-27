package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AutoChannels
import dev.agentbayu.app.ai.Combo
import dev.agentbayu.app.ai.ComboStep
import dev.agentbayu.app.ai.RoutingConfig
import dev.agentbayu.app.ai.RoutingStrategies

@Composable
fun AiRoutingRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionStore = remember(context) { AppGraph.connections(context) }
    val routingStore = remember(context) { AppGraph.routingConfig(context) }
    val router = remember(context) { AppGraph.router(context) }
    val connections by connectionStore.connections.collectAsState()
    val config by routingStore.config.collectAsState()
    val builtInLabel = stringResource(R.string.routing_combo_builtin)
    val stepsTemplate = stringResource(R.string.routing_combo_steps)
    val comboLabelTemplate = stringResource(R.string.routing_combo_new_label)
    val savedMessage = stringResource(R.string.routing_saved)

    val autoOptions = AutoChannels.all.map { channel ->
        RoutingChannelOption(
            channel = channel,
            title = stringResource(autoChannelTitle(channel)),
            subtitle = stringResource(autoChannelBody(channel))
        )
    }
    val comboOptions = config.availableCombos().map { combo ->
        RoutingChannelOption(
            channel = RoutingConfig.comboChannel(combo.id),
            title = combo.label,
            subtitle = if (combo.builtIn) builtInLabel else stepsTemplate.format(combo.steps.size)
        )
    }
    val connectionOptions = connections.map { connection ->
        RoutingChannelOption(
            channel = RoutingConfig.connectionChannel(connection.id),
            title = connection.label,
            subtitle = connection.model
        )
    }
    val preview = remember(config, connections) {
        router.preview(config.channel).map { routed ->
            RoutingPreviewRow(
                connectionLabel = routed.candidate.connection.label,
                model = routed.candidate.model.id,
                strategy = routed.strategy
            )
        }
    }

    fun editStep(comboId: String, index: Int, block: (ComboStep) -> ComboStep) {
        val combo = config.combos.firstOrNull { it.id == comboId } ?: return
        val steps = combo.steps.mapIndexed { position, step ->
            if (position == index) block(step) else step
        }
        routingStore.upsertCombo(combo.copy(steps = steps))
    }

    val actions = RoutingActions(
        onBack = onBack,
        onSelectChannel = { channel ->
            routingStore.setChannel(channel)
            onMessage(savedMessage)
        },
        onAddCombo = {
            routingStore.upsertCombo(
                Combo(
                    id = routingStore.newComboId(),
                    label = comboLabelTemplate.format(config.combos.size + 1),
                    steps = listOf(ComboStep())
                )
            )
        },
        onDeleteCombo = { comboId -> routingStore.removeCombo(comboId) },
        onAddStep = { comboId ->
            val combo = config.combos.firstOrNull { it.id == comboId }
            if (combo != null) {
                routingStore.upsertCombo(combo.copy(steps = combo.steps + ComboStep()))
            }
        },
        onRemoveStep = { comboId, index ->
            val combo = config.combos.firstOrNull { it.id == comboId }
            if (combo != null) {
                val steps = combo.steps.filterIndexed { position, _ -> position != index }
                routingStore.upsertCombo(combo.copy(steps = steps))
            }
        },
        onStepStrategy = { comboId, index, strategy ->
            editStep(comboId, index) { step -> step.copy(strategy = strategy) }
        },
        onStepTier = { comboId, index, tier ->
            editStep(comboId, index) { step -> step.copy(tier = tier) }
        },
        onToggleStepConnection = { comboId, index, connectionId ->
            editStep(comboId, index) { step ->
                val selected = if (step.connectionIds.contains(connectionId)) {
                    step.connectionIds - connectionId
                } else {
                    step.connectionIds + connectionId
                }
                step.copy(connectionIds = selected)
            }
        }
    )

    RoutingScreen(
        state = RoutingScreenState(
            activeChannel = config.channel,
            autoOptions = autoOptions,
            comboOptions = comboOptions,
            connectionOptions = connectionOptions,
            editableCombos = config.combos,
            connections = connections,
            strategies = RoutingStrategies.names,
            preview = preview
        ),
        actions = actions,
        modifier = modifier
    )
}
