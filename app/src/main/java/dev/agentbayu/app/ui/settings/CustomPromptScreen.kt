package dev.agentbayu.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.platform.CustomPromptSettings
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassFab
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
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
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var lastEditTime by remember { mutableStateOf(0L) }
    val insets = LocalScreenInsets.current

    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = insets.calculateBottomPadding() + 88.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AiScreenHeader(
                title = stringResource(R.string.custom_prompt_title),
                onBack = onCancel
            )
            Text(
                text = stringResource(R.string.custom_prompt_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val container = LocalAppSurfaces.current.container
            TextField(
                value = draft,
                onValueChange = { newValue ->
                    val text = newValue.take(CustomPromptSettings.MAX_PROMPT_CHARS)
                    if (text != draft) {
                        val now = System.currentTimeMillis()
                        if (now - lastEditTime > 600L || undoStack.isEmpty()) {
                            undoStack.add(draft)
                        }
                        lastEditTime = now
                        redoStack.clear()
                        draft = text
                    }
                },
                placeholder = { Text(stringResource(R.string.custom_prompt_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 10,
                shape = MaterialTheme.shapes.large,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = container,
                    unfocusedContainerColor = container,
                    disabledContainerColor = container,
                    errorContainerColor = container,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = 20.dp + insets.calculateBottomPadding()
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassFab(
                onClick = {
                    if (canUndo) {
                        redoStack.add(draft)
                        draft = undoStack.removeAt(undoStack.lastIndex)
                        lastEditTime = 0L
                    }
                },
                enabled = canUndo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = "Undo",
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = {
                    if (canRedo) {
                        undoStack.add(draft)
                        draft = redoStack.removeAt(redoStack.lastIndex)
                        lastEditTime = 0L
                    }
                },
                enabled = canRedo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = "Redo",
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = { onSave(draft) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.custom_prompt_save),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
