package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ProviderTier
import dev.agentbayu.app.ui.components.GlassIconButton
import java.util.Locale

@Composable
fun AiScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.nav_back),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        action()
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

fun formatCost(value: Double?): String? {
    if (value == null) return null
    val pattern = if (value > 0.0 && value < SMALL_COST) SMALL_COST_PATTERN else COST_PATTERN
    return String.format(Locale.US, pattern, value)
}

fun formatTokens(value: Int): String = String.format(Locale.US, TOKEN_PATTERN, value)

private const val SMALL_COST = 0.01
private const val SMALL_COST_PATTERN = "%.5f"
private const val COST_PATTERN = "%.4f"
private const val TOKEN_PATTERN = "%,d"
