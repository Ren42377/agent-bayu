package dev.agentbayu.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.ai.ProviderPickerDialog
import dev.agentbayu.app.ui.ai.ReplyDetailSheet
import dev.agentbayu.app.ui.components.MessageList
import dev.agentbayu.app.ui.components.PromptBar
import dev.agentbayu.app.ui.components.SuggestionChips

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    isResponding: Boolean,
    suggestions: List<String>,
    providerHint: String,
    providerOptions: List<ProviderOption>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onMicClick: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onManageProviders: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                MessageList(
                    messages = messages,
                    isResponding = isResponding,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    onShowDetail = { message -> detailMessage = message }
                )
            }
        }
        SuggestionChips(
            suggestions = if (messages.isEmpty()) suggestions else emptyList(),
            onSelect = onSuggestionClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
        ProviderBar(
            hint = providerHint,
            isResponding = isResponding,
            onOpenPicker = { pickerVisible = true },
            onStop = onStop
        )
        PromptBar(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
            onMicClick = onMicClick,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
    detailMessage?.let { message ->
        val detail = message.detail
        if (detail == null) {
            detailMessage = null
        } else {
            ReplyDetailSheet(
                detail = detail,
                usage = message.usage,
                onDismiss = { detailMessage = null }
            )
        }
    }
    if (pickerVisible) {
        ProviderPickerDialog(
            options = providerOptions,
            onSelect = onSelectProvider,
            onSelectModel = onSelectModel,
            onManage = {
                pickerVisible = false
                onManageProviders()
            },
            onDismiss = { pickerVisible = false }
        )
    }
}

@Composable
private fun ProviderBar(
    hint: String,
    isResponding: Boolean,
    onOpenPicker: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenPicker)
                .padding(vertical = 6.dp)
        )
        if (isResponding) {
            Text(
                text = stringResource(R.string.chat_stop),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onStop)
                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
