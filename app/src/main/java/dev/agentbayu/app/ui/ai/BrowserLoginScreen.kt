package dev.agentbayu.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.AppleRedLight
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.glassSurface

sealed interface BrowserLoginUiState {
    data object Starting : BrowserLoginUiState

    data class Waiting(val hasAuthorizeUrl: Boolean) : BrowserLoginUiState

    data object Finishing : BrowserLoginUiState

    data object Done : BrowserLoginUiState

    data class Failed(val message: String) : BrowserLoginUiState
}

data class BrowserLoginActions(
    val onOpenBrowser: () -> Unit,
    val onManualRedirect: (String) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit
)

@Composable
fun BrowserLoginScreen(
    providerLabel: String,
    state: BrowserLoginUiState,
    actions: BrowserLoginActions,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(title = stringResource(R.string.browser_title), onBack = actions.onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp + insets.calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = providerLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            when (state) {
                BrowserLoginUiState.Starting -> ProgressCard(
                    message = stringResource(R.string.browser_starting)
                )

                is BrowserLoginUiState.Waiting -> WaitingCard(state = state, actions = actions)

                BrowserLoginUiState.Finishing -> ProgressCard(
                    message = stringResource(R.string.browser_finishing)
                )

                BrowserLoginUiState.Done -> InfoCard(
                    message = stringResource(R.string.browser_success)
                )

                is BrowserLoginUiState.Failed -> FailedCard(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun WaitingCard(state: BrowserLoginUiState.Waiting, actions: BrowserLoginActions) {
    var redirect by remember { mutableStateOf("") }
    GlassBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.browser_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.hasAuthorizeUrl) {
                GlassButton(
                    onClick = actions.onOpenBrowser,
                    modifier = Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.browser_open),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.browser_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.browser_manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            OutlinedTextField(
                value = redirect,
                onValueChange = { value -> redirect = value },
                label = { Text(text = stringResource(R.string.browser_manual_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            GlassButton(
                onClick = { actions.onManualRedirect(redirect) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.browser_manual_submit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FailedCard(state: BrowserLoginUiState.Failed, actions: BrowserLoginActions) {
    GlassBox {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.browser_failed, state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = AppleRedLight
            )
            GlassButton(
                onClick = actions.onRetry,
                modifier = Modifier.fillMaxWidth(),
                tint = MaterialTheme.colorScheme.primary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.browser_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(message: String) {
    GlassBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
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
            .glassSurface(shape = GlassCardShape)
            .padding(16.dp)
    ) {
        content()
    }
}
