package dev.agentbayu.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.GlassSolidDark
import dev.agentbayu.app.ui.theme.GlassSolidLight
import dev.agentbayu.app.ui.theme.LocalDarkTheme
import dev.agentbayu.app.ui.theme.ScrimBlack

@Composable
fun AssistantPanel(
    visible: Boolean,
    invocationId: Long,
    manageImeInsets: Boolean,
    messages: List<ChatMessage>,
    input: String,
    isResponding: Boolean,
    suggestions: List<ChatSuggestion>,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onMicClick: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(visible, invocationId) {
        if (visible) {
            progress.snapTo(0f)
            progress.animateTo(1f, AgentBayuMotion.quickFade)
        } else if (progress.value > 0f) {
            progress.animateTo(0f, AgentBayuMotion.quickFade)
            onHidden()
        }
    }
    val surfaceColor = if (LocalDarkTheme.current) GlassSolidDark else GlassSolidLight
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimBlack.copy(alpha = AgentBayuMotion.ScrimAlpha))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (manageImeInsets) Modifier.imePadding() else Modifier)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    GreetingSection(
                        suggestions = suggestions,
                        surfaceColor = surfaceColor,
                        onSuggestionClick = onSuggestionClick
                    )
                    OpenAppChip(
                        onOpenApp = onOpenApp,
                        filled = true,
                        surfaceColor = surfaceColor
                    )
                } else {
                    ResponseCard(
                        messages = messages,
                        isResponding = isResponding,
                        surfaceColor = surfaceColor,
                        onOpenApp = onOpenApp,
                        onDismiss = onDismiss
                    )
                }
                AssistantInputBar(
                    value = input,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    onStop = onStop,
                    isResponding = isResponding,
                    onMicClick = onMicClick,
                    enabled = enabled,
                    surfaceColor = surfaceColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GreetingSection(
    suggestions: List<ChatSuggestion>,
    surfaceColor: Color,
    onSuggestionClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CapsuleShape)
                            .background(surfaceColor)
                            .clickable { onSuggestionClick(suggestion.label) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            painter = painterResource(suggestion.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = suggestion.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseCard(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    surfaceColor: Color,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassCardShape)
            .background(surfaceColor)
    ) {
        MessageList(
            messages = messages,
            isResponding = isResponding,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = MESSAGE_LIST_MAX_HEIGHT),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        )
        Text(
            text = stringResource(R.string.overlay_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistantCircleButton(
                icon = R.drawable.ic_close,
                description = R.string.overlay_close,
                onClick = onDismiss,
                buttonSize = 36.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.weight(1f))
            OpenAppChip(
                onOpenApp = onOpenApp,
                filled = false,
                surfaceColor = surfaceColor
            )
        }
    }
}

@Composable
private fun OpenAppChip(
    onOpenApp: () -> Unit,
    filled: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val chipModifier = if (filled) {
        Modifier
            .clip(CapsuleShape)
            .background(surfaceColor)
            .clickable(onClick = onOpenApp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        Modifier
            .clip(CapsuleShape)
            .clickable(onClick = onOpenApp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    }
    Row(
        modifier = modifier.then(chipModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_open_in_app),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.overlay_open_app),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isResponding: Boolean,
    onMicClick: () -> Unit,
    enabled: Boolean,
    surfaceColor: Color,
    modifier: Modifier = Modifier
) {
    val canSend = enabled && !isResponding && value.isNotBlank()
    val submit = {
        if (canSend) {
            onSend()
        }
    }
    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(surfaceColor)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantCircleButton(
            icon = R.drawable.ic_mic,
            description = R.string.chat_mic,
            onClick = onMicClick,
            enabled = enabled && !isResponding
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_input_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled && !isResponding,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isResponding) {
            AssistantCircleButton(
                icon = R.drawable.ic_stop,
                description = R.string.chat_stop,
                onClick = onStop,
                container = MaterialTheme.colorScheme.error,
                iconTint = Color.White,
                iconSize = 16.dp
            )
        } else {
            AssistantCircleButton(
                icon = R.drawable.ic_send,
                description = R.string.chat_send,
                onClick = { submit() },
                container = if (canSend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                iconTint = if (canSend) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                iconSize = 18.dp
            )
        }
    }
}

@Composable
private fun AssistantCircleButton(
    icon: Int,
    description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 40.dp,
    container: Color = Color.Transparent,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(description),
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

private val MESSAGE_LIST_MAX_HEIGHT = 320.dp
