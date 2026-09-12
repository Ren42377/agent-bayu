package dev.agentbayu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.platform.CustomPromptSettings
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CustomPromptRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = remember(context) { AppGraph.settings(context) }
    val customPrompt by settings.customPrompt.collectAsState()
    val scope = rememberCoroutineScope()
    val saveFailed = stringResource(R.string.custom_prompt_save_failed)
    var saving by remember { mutableStateOf(false) }
    CustomPromptScreen(
        initialValue = customPrompt,
        onSave = { value ->
            if (!saving) {
                saving = true
                scope.launch {
                    val saved = runCatching {
                        withContext(Dispatchers.IO) { settings.setCustomPrompt(value) }
                    }.isSuccess
                    if (saved) onBack() else {
                        saving = false
                        onMessage(saveFailed)
                    }
                }
            }
        },
        onClear = {
            if (!saving) {
                saving = true
                scope.launch {
                    val cleared = runCatching {
                        withContext(Dispatchers.IO) { settings.setCustomPrompt("") }
                    }.isSuccess
                    if (cleared) onBack() else {
                        saving = false
                        onMessage(saveFailed)
                    }
                }
            }
        },
        onCancel = onBack,
        modifier = modifier
    )
}

@Composable
fun CustomPromptScreen(
    initialValue: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf(initialValue) }
    val insets = LocalScreenInsets.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        AiScreenHeader(
            title = stringResource(R.string.custom_prompt_title),
            onBack = onCancel
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.custom_prompt_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(CustomPromptSettings.MAX_PROMPT_CHARS) },
                label = { Text(stringResource(R.string.custom_prompt_hint)) },
                minLines = 10,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
            )
        }
        CustomPromptActions(
            onCancel = onCancel,
            onClear = onClear,
            onSave = { onSave(draft) },
            bottomPadding = insets.calculateBottomPadding()
        )
    }
}

@Composable
private fun CustomPromptActions(
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPadding + 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PromptAction(
            label = stringResource(R.string.custom_prompt_cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        )
        PromptAction(
            label = stringResource(R.string.custom_prompt_clear),
            onClick = onClear,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error
        )
        PromptAction(
            label = stringResource(R.string.custom_prompt_save),
            onClick = onSave,
            modifier = Modifier.weight(1f),
            tint = MaterialTheme.colorScheme.primary,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun PromptAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        tint = tint,
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
