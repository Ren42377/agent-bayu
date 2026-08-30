package dev.agentbayu.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.liquidGlass

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
        ProviderCapsule(
            hint = providerHint,
            isResponding = isResponding,
            onOpenPicker = { pickerVisible = true },
            onStop = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty()) {
                EmptyState(
                    suggestions = suggestions,
                    onSuggestionClick = onSuggestionClick,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                MessageList(
                    messages = messages,
                    isResponding = isResponding,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    onShowDetail = { message -> detailMessage = message }
                )
            }
        }

        if (messages.isNotEmpty()) {
            SuggestionChips(
                suggestions = emptyList(),
                onSelect = onSuggestionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        PromptBar(
            value = input,
            onValueChange = onInputChange,
            onSend = onSend,
            onMicClick = onMicClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
private fun ProviderCapsule(
    hint: String,
    isResponding: Boolean,
    onOpenPicker: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .liquidGlass(shape = CapsuleShape)
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isResponding) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isResponding,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onStop)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_stop),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_spark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        SuggestionChips(
            suggestions = suggestions,
            onSelect = onSuggestionClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
