package dev.agentbayu.app.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.ai.ProviderPickerDialog
import dev.agentbayu.app.ui.ai.ReplyDetailSheet
import dev.agentbayu.app.ui.components.GlassBadge
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.MessageList
import dev.agentbayu.app.ui.components.PromptBar
import dev.agentbayu.app.ui.components.SuggestionChips
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalScreenInsets

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
    onSelectEffort: (String, ReasoningEffort) -> Unit,
    onManageProviders: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    attachments: List<MessageAttachment> = emptyList(),
    canAttach: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (MessageAttachment) -> Unit = {}
) {
    var detailMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var footerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val insets = LocalScreenInsets.current
    val messagesBackdrop = rememberLayerBackdrop()
    val overlayBackdrop = rememberCombinedBackdrop(LocalGlassBackdrop.current, messagesBackdrop)

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            EmptyState(
                suggestions = suggestions,
                onSuggestionClick = onSuggestionClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = headerHeight, bottom = footerHeight)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(messagesBackdrop)
            ) {
                MessageList(
                    messages = messages,
                    isResponding = isResponding,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = headerHeight + 12.dp,
                        bottom = footerHeight + 12.dp
                    ),
                    onShowDetail = { message -> detailMessage = message }
                )
            }
        }

        val headerModifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .onSizeChanged { size ->
                headerHeight = with(density) { size.height.toDp() }
            }
            .padding(top = insets.calculateTopPadding())
            .padding(horizontal = 16.dp, vertical = 6.dp)

        val footerModifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { size ->
                footerHeight = with(density) { size.height.toDp() }
            }
            .padding(bottom = insets.calculateBottomPadding())

        CompositionLocalProvider(LocalGlassBackdrop provides overlayBackdrop) {
            ProviderCapsule(
                hint = providerHint,
                isResponding = isResponding,
                onOpenPicker = { pickerVisible = true },
                modifier = headerModifier
            )

            Column(modifier = footerModifier) {
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
                    isResponding = isResponding,
                    onStop = onStop,
                    attachments = attachments,
                    canAttach = canAttach,
                    onAttachClick = onAttachClick,
                    onRemoveAttachment = onRemoveAttachment,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
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
            onSelectEffort = onSelectEffort,
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        GlassButton(
            onClick = onOpenPicker,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
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
        GlassBadge(
            icon = painterResource(R.drawable.ic_spark),
            containerColor = MaterialTheme.colorScheme.primary,
            size = 56.dp,
            iconSize = 28.dp,
            shape = CircleShape
        )
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
