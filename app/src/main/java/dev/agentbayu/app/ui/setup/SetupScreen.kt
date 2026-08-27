package dev.agentbayu.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.components.StatusCard

@Composable
fun SetupScreen(
    isDefaultAssistant: Boolean,
    isMicrophoneGranted: Boolean,
    useScreenContext: Boolean,
    onOpenAssistantSettings: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onScreenContextChange: (Boolean) -> Unit,
    onTestPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        StatusCard(
            title = stringResource(R.string.setup_assistant_title),
            body = stringResource(R.string.setup_assistant_body),
            done = isDefaultAssistant,
            hint = stringResource(R.string.setup_assistant_hint),
            actionLabel = stringResource(R.string.setup_assistant_action),
            onAction = onOpenAssistantSettings
        )
        StatusCard(
            title = stringResource(R.string.setup_mic_title),
            body = stringResource(R.string.setup_mic_body),
            done = isMicrophoneGranted,
            actionLabel = if (isMicrophoneGranted) {
                null
            } else {
                stringResource(R.string.setup_mic_action)
            },
            onAction = if (isMicrophoneGranted) null else onRequestMicrophone
        )
        StatusCard(
            title = stringResource(R.string.setup_context_title),
            body = stringResource(R.string.setup_context_body),
            done = useScreenContext,
            checked = useScreenContext,
            onCheckedChange = onScreenContextChange
        )
        StatusCard(
            title = stringResource(R.string.setup_test_title),
            body = stringResource(R.string.setup_test_body),
            done = isDefaultAssistant,
            actionLabel = stringResource(R.string.setup_test_action),
            onAction = onTestPanel
        )
    }
}
