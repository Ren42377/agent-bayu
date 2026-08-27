package dev.agentbayu.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.domain.MessageAuthor
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.PanelShape
import dev.agentbayu.app.ui.theme.ScrimBlack

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
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AgentBayuMotion.panelSpring,
        label = "assistantPanel",
        finishedListener = { value -> if (value < HIDDEN_THRESHOLD) onHidden() }
    )
    if (!visible && progress < HIDDEN_THRESHOLD) {
        return
    }
    val panelHeight = remember { mutableStateOf(0) }
    val dragOffset = remember { mutableStateOf(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            dragOffset.value = 0f
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress }
                .background(ScrimBlack.copy(alpha = AgentBayuMotion.ScrimAlpha))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { size -> panelHeight.value = size.height }
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * size.height + dragOffset.value
                }
                .pointerInput(Unit) { detectTapGestures { } },
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            ) {
                DragHandle(
                    onDrag = { amount ->
                        dragOffset.value = (dragOffset.value + amount).coerceAtLeast(0f)
                    },
                    onDragStopped = {
                        val threshold = panelHeight.value * AgentBayuMotion.PanelDismissFraction
                        if (dragOffset.value > threshold) {
                            onDismiss()
                        } else {
                            dragOffset.value = 0f
                        }
                    }
                )
                PanelHeader(
                    isResponding = isResponding,
                    routeLabel = messages.lastOrNull { message ->
                        message.author == MessageAuthor.AGENT
                    }?.route?.label,
                    onDismiss = onDismiss
                )
                if (messages.isEmpty()) {
                    PanelGreeting(
                        suggestions = suggestions,
                        onSuggestionClick = onSuggestionClick
                    )
                } else {
                    MessageList(
                        messages = messages,
                        isResponding = isResponding,
                        modifier = Modifier.heightIn(max = 320.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                PromptBar(
                    value = input,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    onMicClick = onMicClick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                TextButton(
                    onClick = onOpenApp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_open_in_app),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.overlay_open_app))
                }
            }
        }
    }
}

private const val HIDDEN_THRESHOLD = 0.01f

@Composable
private fun DragHandle(onDrag: (Float) -> Unit, onDragStopped: () -> Unit) {
    val handleDescription = stringResource(R.string.overlay_handle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = handleDescription }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
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
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
private fun PanelHeader(isResponding: Boolean, routeLabel: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_spark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.overlay_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (!isResponding && routeLabel != null) {
                    routeLabel
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
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.overlay_close)
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
            style = MaterialTheme.typography.titleLarge
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
