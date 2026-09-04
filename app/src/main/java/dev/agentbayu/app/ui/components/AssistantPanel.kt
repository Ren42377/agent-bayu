package dev.agentbayu.app.ui.components

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.LocalGlassStyle
import dev.agentbayu.app.ui.theme.PanelShape
import dev.agentbayu.app.ui.theme.ScrimBlack
import dev.agentbayu.app.ui.theme.liquidGlass
import dev.agentbayu.app.ui.theme.solidGlassStyle

@Composable
fun AssistantPanel(
    visible: Boolean,
    messages: List<ChatMessage>,
    input: String,
    isResponding: Boolean,
    suggestions: List<String>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onMicClick: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var rendered by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val metrics = remember { PanelMetrics() }
    val panelBackdrop = remember { emptyBackdrop() }
    val entryOffset = with(LocalDensity.current) { ENTRY_OFFSET.toPx() }
    LaunchedEffect(visible) {
        if (visible) {
            dragOffset = 0f
            rendered = true
            progress.animateTo(1f, AgentBayuMotion.assistantPanelSpring)
        } else if (rendered) {
            progress.animateTo(0f, AgentBayuMotion.assistantPanelSpring)
            rendered = false
            onHidden()
        }
    }
    LaunchedEffect(dragging, visible) {
        if (!dragging && visible && dragOffset != 0f) {
            animate(
                initialValue = dragOffset,
                targetValue = 0f,
                animationSpec = AgentBayuMotion.snappySpring
            ) { value, _ -> dragOffset = value }
        }
    }
    if (!rendered) {
        return
    }
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { inputFocus.requestFocus() }
        keyboard?.show()
    }
    CompositionLocalProvider(
        LocalGlassBackdrop provides panelBackdrop,
        LocalGlassStyle provides solidGlassStyle()
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(ScrimBlack.copy(alpha = AgentBayuMotion.ScrimAlpha))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size -> metrics.height = size.height }
                    .graphicsLayer {
                        val value = progress.value
                        alpha = value
                        translationY = (1f - value) * entryOffset + dragOffset
                    }
                    .liquidGlass(shape = PanelShape)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier.windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                                )
                            }
                        )
                ) {
                    DragHandle(
                        onDragStart = { dragging = true },
                        onDrag = { amount ->
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragStopped = {
                            dragging = false
                            val threshold = metrics.height * AgentBayuMotion.PanelDismissFraction
                            if (dragOffset > threshold) {
                                onDismiss()
                            }
                        }
                    )
                    PanelHeader(
                        isResponding = isResponding,
                        detailLabel = messages.lastOrNull { message ->
                            message.author == MessageAuthor.AGENT
                        }?.detail?.label,
                        onDismiss = onDismiss
                    )
                    Box(modifier = Modifier.animateContentSize(AgentBayuMotion.panelSizeSpec)) {
                        if (messages.isEmpty()) {
                            PanelGreeting(
                                suggestions = suggestions,
                                onSuggestionClick = onSuggestionClick
                            )
                        } else {
                            MessageList(
                                messages = messages,
                                isResponding = isResponding,
                                modifier = Modifier.heightIn(max = MESSAGE_LIST_MAX_HEIGHT),
                                contentPadding = PaddingValues(
                                    horizontal = 20.dp,
                                    vertical = 8.dp
                                )
                            )
                        }
                    }
                    PromptBar(
                        value = input,
                        onValueChange = onInputChange,
                        onSend = onSend,
                        onMicClick = onMicClick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        focusRequester = inputFocus
                    )
                    Row(
                        modifier = Modifier
                            .padding(start = 12.dp, bottom = 8.dp)
                            .clip(CapsuleShape)
                            .clickable(onClick = onOpenApp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_app),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.overlay_open_app),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private class PanelMetrics {
    var height: Int = 0
}

private val ENTRY_OFFSET = 220.dp
private val MESSAGE_LIST_MAX_HEIGHT = 320.dp

@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStopped: () -> Unit
) {
    val handleDescription = stringResource(R.string.overlay_handle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = handleDescription }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragStopped,
                    onDragCancel = onDragStopped,
                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) }
                )
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun PanelHeader(isResponding: Boolean, detailLabel: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassBadge(
            icon = painterResource(R.drawable.ic_spark),
            containerColor = MaterialTheme.colorScheme.primary,
            size = 36.dp,
            iconSize = 20.dp,
            shape = CircleShape
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.overlay_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (!isResponding && detailLabel != null) {
                    detailLabel
                } else {
                    stringResource(
                        if (isResponding) R.string.chat_thinking else R.string.overlay_subtitle
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        GlassIconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.overlay_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PanelGreeting(suggestions: List<String>, onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.chat_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        SuggestionChips(
            suggestions = suggestions,
            onSelect = onSuggestionClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
