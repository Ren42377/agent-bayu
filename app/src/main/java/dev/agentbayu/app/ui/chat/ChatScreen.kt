package dev.agentbayu.app.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
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
import dev.agentbayu.app.ui.components.ChatSuggestion
import dev.agentbayu.app.ui.components.LocalAttachmentLoader
import dev.agentbayu.app.ui.components.PREVIEW_EDGE
import dev.agentbayu.app.ui.components.GlassButton
import dev.agentbayu.app.ui.components.GlassButtonDefaults
import dev.agentbayu.app.ui.components.GlassOverlay
import dev.agentbayu.app.ui.components.GlassOverlayPresentation
import dev.agentbayu.app.ui.components.MessageList
import dev.agentbayu.app.ui.components.PromptBar
import dev.agentbayu.app.ui.components.SuggestionRows
import dev.agentbayu.app.ui.history.HistoryDrawerState
import dev.agentbayu.app.ui.history.historyDrawerEdge
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalGlassStyle
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import dev.agentbayu.app.ui.theme.chromeGlassStyle

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    isResponding: Boolean,
    suggestions: List<ChatSuggestion>,
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
    incognito: Boolean = false,
    sessionActionEnabled: Boolean = true,
    onSessionAction: () -> Unit = {},
    onCopy: (ChatMessage) -> Unit = {},
    onRegenerate: (ChatMessage) -> Unit = {},
    onEdit: (ChatMessage) -> Unit = {},
    drawer: HistoryDrawerState,
    modifier: Modifier = Modifier,
    sessionKey: String = "",
    attachments: List<MessageAttachment> = emptyList(),
    canAttach: Boolean = false,
    composerEnabled: Boolean = true,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (MessageAttachment) -> Unit = {}
) {
    var detailMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var previewAttachment by remember { mutableStateOf<MessageAttachment?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var headerHeight by remember { mutableStateOf(0.dp) }
    var footerHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val insets = LocalScreenInsets.current
    val imeInsets = WindowInsets.ime
    val bottomReserve = insets.calculateBottomPadding()
    val bottomReservePx = with(density) { bottomReserve.roundToPx() }
    val messagesBackdrop = rememberLayerBackdrop()
    val overlayBackdrop = rememberCombinedBackdrop(LocalGlassBackdrop.current, messagesBackdrop)
    val hasConversation = messages.isNotEmpty()
    val sessionActionTint = if (incognito && !hasConversation) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Unspecified
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .historyDrawerEdge(drawer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(messagesBackdrop)
        ) {
            MessageList(
                messages = messages,
                isResponding = isResponding,
                modifier = Modifier.fillMaxSize(),
                sessionKey = sessionKey,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = headerHeight + 12.dp,
                    bottom = footerHeight + 12.dp
                ),
                onShowDetail = { message -> detailMessage = message },
                onCopy = onCopy,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onOpenAttachment = { attachment -> previewAttachment = attachment }
            )
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
            .layout { measurable, constraints ->
                val reserve = (bottomReservePx - imeInsets.getBottom(this)).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.offset(vertical = -reserve))
                layout(placeable.width, placeable.height + reserve) {
                    placeable.place(0, 0)
                }
            }

        CompositionLocalProvider(
            LocalGlassBackdrop provides overlayBackdrop,
            LocalGlassStyle provides chromeGlassStyle()
        ) {
            Box(modifier = headerModifier) {
                ProviderCapsule(
                    hint = providerHint,
                    isResponding = isResponding,
                    onOpenPicker = { pickerVisible = true },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = HEADER_ACTION_RESERVE)
                )
                GlassButton(
                    onClick = drawer::open,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp),
                    shape = CircleShape,
                    contentPadding = GlassButtonDefaults.IconPadding
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_history),
                        contentDescription = stringResource(R.string.history_open),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                GlassButton(
                    onClick = onSessionAction,
                    enabled = sessionActionEnabled,
                    tint = sessionActionTint,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp),
                    shape = CircleShape,
                    contentPadding = GlassButtonDefaults.IconPadding
                ) {
                    Icon(
                        painter = painterResource(
                            if (hasConversation) R.drawable.ic_add else R.drawable.ic_incognito
                        ),
                        contentDescription = stringResource(
                            when {
                                hasConversation -> R.string.chat_new_session
                                incognito -> R.string.chat_incognito_exit
                                else -> R.string.chat_incognito_enter
                            }
                        ),
                        tint = if (incognito && !hasConversation) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = footerModifier) {
                if (messages.isEmpty()) {
                    SuggestionRows(
                        suggestions = suggestions,
                        onSelect = onSuggestionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                    )
                }
                PromptBar(
                    value = input,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    onMicClick = onMicClick,
                    isResponding = isResponding,
                    enabled = composerEnabled,
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

    AttachmentPreview(
        attachment = previewAttachment,
        onDismiss = { previewAttachment = null }
    )

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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
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
private fun AttachmentPreview(
    attachment: MessageAttachment?,
    onDismiss: () -> Unit
) {
    val loader = LocalAttachmentLoader.current
    var image by remember(attachment?.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(attachment?.id, loader) {
        image = attachment?.let { item -> loader?.thumbnail(item.id, PREVIEW_EDGE) }
    }
    GlassOverlay(
        visible = attachment != null,
        presentation = GlassOverlayPresentation.SHEET,
        onDismiss = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            image?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.chat_attachment_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = with(LocalDensity.current) {
                            (LocalWindowInfo.current.containerSize.height * PREVIEW_HEIGHT_RATIO).toDp()
                        })
                )
            }
        }
    }
}

private val HEADER_ACTION_RESERVE = 48.dp
private const val PREVIEW_HEIGHT_RATIO = 0.8f
