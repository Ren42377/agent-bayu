package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.AutoChannels
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ai.RoutingConfig
import dev.agentbayu.app.ai.SkipReason
import java.util.Locale

@Composable
fun AiScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.nav_back)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            action()
        }
    }
}

@Composable
fun tierLabel(tier: ProviderTier): String = stringResource(
    when (tier) {
        ProviderTier.SUBSCRIPTION -> R.string.tier_subscription
        ProviderTier.API_KEY -> R.string.tier_api_key
        ProviderTier.CHEAP -> R.string.tier_cheap
        ProviderTier.FREE -> R.string.tier_free
    }
)

@Composable
fun healthLabel(health: ConnectionHealth): String = stringResource(
    when (health) {
        ConnectionHealth.READY -> R.string.providers_health_ready
        ConnectionHealth.NEEDS_KEY -> R.string.providers_health_needs_key
        ConnectionHealth.NEEDS_ATTENTION -> R.string.providers_health_attention
    }
)

@Composable
fun skipReasonLabel(reason: SkipReason): String = stringResource(
    when (reason) {
        SkipReason.BREAKER_OPEN -> R.string.skip_breaker_open
        SkipReason.COOLDOWN -> R.string.skip_cooldown
        SkipReason.MODEL_LOCKED -> R.string.skip_model_locked
        SkipReason.MISSING_KEY -> R.string.skip_missing_key
        SkipReason.CONTEXT_TOO_SMALL -> R.string.skip_context_too_small
        SkipReason.FAILED -> R.string.skip_failed
    }
)

@Composable
fun channelLabel(channel: String, combos: List<Pair<String, String>>, connections: List<Connection>): String {
    val comboId = RoutingConfig.comboIdOf(channel)
    if (comboId != null) {
        return combos.firstOrNull { it.first == comboId }?.second ?: comboId
    }
    val connectionId = RoutingConfig.connectionIdOf(channel)
    if (connectionId != null) {
        return connections.firstOrNull { it.id == connectionId }?.label ?: connectionId
    }
    return channel
}

@Composable
fun durationLabel(millis: Long): String {
    if (millis >= MINUTE_MILLIS) {
        return stringResource(R.string.duration_minutes, ((millis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).toInt())
    }
    return stringResource(R.string.duration_seconds, ((millis + 999L) / 1000L).toInt())
}

fun formatCost(value: Double?): String? {
    if (value == null) return null
    val pattern = if (value > 0.0 && value < SMALL_COST) SMALL_COST_PATTERN else COST_PATTERN
    return String.format(Locale.US, pattern, value)
}

fun formatTokens(value: Int): String = String.format(Locale.US, TOKEN_PATTERN, value)

fun autoChannelBody(channel: String): Int = when (channel) {
    AutoChannels.FAST -> R.string.routing_channel_fast_body
    AutoChannels.CHEAP -> R.string.routing_channel_cheap_body
    AutoChannels.FREE -> R.string.routing_channel_free_body
    else -> R.string.routing_channel_auto_body
}

fun autoChannelTitle(channel: String): Int = when (channel) {
    AutoChannels.FAST -> R.string.routing_channel_fast
    AutoChannels.CHEAP -> R.string.routing_channel_cheap
    AutoChannels.FREE -> R.string.routing_channel_free
    else -> R.string.routing_channel_auto
}

private const val MINUTE_MILLIS = 60_000L
private const val SMALL_COST = 0.01
private const val SMALL_COST_PATTERN = "%.5f"
private const val COST_PATTERN = "%.4f"
private const val TOKEN_PATTERN = "%,d"
