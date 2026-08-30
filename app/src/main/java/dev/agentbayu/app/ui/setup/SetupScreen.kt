package dev.agentbayu.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import dev.agentbayu.app.ui.theme.LocalScreenInsets

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
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp + insets.calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
