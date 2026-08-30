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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.liquidGlass

sealed interface DeviceCodeUiState {
    data object Starting : DeviceCodeUiState

    data class Waiting(
        val userCode: String,
        val remainingMillis: Long,
        val hasVerificationUrl: Boolean
    ) : DeviceCodeUiState

    data object Done : DeviceCodeUiState

    data class Failed(val message: String) : DeviceCodeUiState
}

data class DeviceCodeActions(
    val onCopy: () -> Unit,
    val onOpenBrowser: () -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit
)

@Composable
fun DeviceCodeScreen(
    providerLabel: String,
    state: DeviceCodeUiState,
    actions: DeviceCodeActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        AiScreenHeader(title = stringResource(R.string.device_title), onBack = actions.onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            when (state) {
                DeviceCodeUiState.Starting -> StartingCard()
                is DeviceCodeUiState.Waiting -> WaitingCard(state = state, actions = actions)
                DeviceCodeUiState.Done -> InfoCard(message = stringResource(R.string.device_success))
                is DeviceCodeUiState.Failed -> FailedCard(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun StartingCard() {
    GlassBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.device_starting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WaitingCard(state: DeviceCodeUiState.Waiting, actions: DeviceCodeActions) {
    GlassBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.device_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.device_code_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.userCode,
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    text = stringResource(R.string.device_copy),
                    onClick = actions.onCopy,
                    modifier = Modifier.weight(1f)
                )
                if (state.hasVerificationUrl) {
                    PillButton(
                        text = stringResource(R.string.device_open_browser),
                        onClick = actions.onOpenBrowser,
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.device_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.device_remaining,
                    state.remainingMillis / MILLIS_PER_MINUTE,
                    state.remainingMillis % MILLIS_PER_MINUTE / MILLIS_PER_SECOND
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FailedCard(state: DeviceCodeUiState.Failed, actions: DeviceCodeActions) {
    GlassBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.device_failed, state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = AppleRedLight
            )
            PillButton(
                text = stringResource(R.string.device_retry),
                onClick = actions.onRetry,
                primary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    GlassBox {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun GlassBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassCardShape)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(CapsuleShape)
            .background(
                if (primary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
