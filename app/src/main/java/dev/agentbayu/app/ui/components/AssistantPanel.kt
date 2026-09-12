package dev.agentbayu.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.emptyBackdrop
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.ChatMessage
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.GlassTileShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.ScrimBlack
import dev.agentbayu.app.ui.theme.liquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AssistantPanel(
    visible: Boolean,
    invocationId: Long,
    manageImeInsets: Boolean,
    messages: List<ChatMessage>,
    input: String,
    isResponding: Boolean,
    enabled: Boolean,
    screenshot: Bitmap?,
    screenshotActive: Boolean,
    onToggleScreenshot: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onHidden: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    var entryOffsetPx by remember { mutableFloatStateOf(INITIAL_ENTRY_OFFSET_PX) }
    LaunchedEffect(visible, invocationId) {
        if (visible) {
            progress.snapTo(0f)
            progress.animateTo(1f, AgentBayuMotion.assistantPanelSpring)
        } else if (progress.value > 0f) {
            progress.animateTo(0f, AgentBayuMotion.assistantPanelSpring)
            onHidden()
        }
    }
    val backdrop = remember { emptyBackdrop() }
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
                .fillMaxSize()
                .then(if (manageImeInsets) Modifier.imePadding() else Modifier)
        ) {
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { translationY = (1f - progress.value) * entryOffsetPx }
                        .onSizeChanged { entryOffsetPx = it.height.toFloat() }
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .pointerInput(Unit) { detectTapGestures { } },
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty()) {
                        Text(
                            text = stringResource(R.string.chat_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        ResponseCard(
                            messages = messages,
                            isResponding = isResponding,
                            onDismiss = onDismiss
                        )
                    }
                    ScreenContextRow(
                        screenshot = screenshot,
                        active = screenshotActive,
                        onToggle = onToggleScreenshot
                    )
                    DragPill(onOpenApp = onOpenApp, onDismiss = onDismiss)
                    AssistantInputBar(
                        value = input,
                        onValueChange = onInputChange,
                        onSend = onSend,
                        onStop = onStop,
                        isResponding = isResponding,
                        onMicClick = onMicClick,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseCard(
    messages: List<ChatMessage>,
    isResponding: Boolean,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(shape = GlassCardShape)
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
        Box(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
            AssistantCircleButton(
                icon = R.drawable.ic_close,
                description = R.string.overlay_close,
                onClick = onDismiss,
                buttonSize = 36.dp,
                iconSize = 18.dp
            )
        }
    }
}

@Composable
private fun ScreenContextRow(
    screenshot: Bitmap?,
    active: Boolean,
    onToggle: () -> Unit
) {
    AnimatedVisibility(
        visible = screenshot != null,
        enter = fadeIn(AgentBayuMotion.quickFade) + scaleIn(
            initialScale = 0.85f,
            animationSpec = AgentBayuMotion.snappySpring
        ),
        exit = fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = 0.85f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .liquidGlass(
                        shape = CapsuleShape,
                        tint = if (active) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_image),
                    contentDescription = null,
                    tint = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.overlay_screen_context),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            AnimatedVisibility(
                visible = active && screenshot != null,
                enter = fadeIn(AgentBayuMotion.quickFade) + scaleIn(
                    initialScale = 0.7f,
                    animationSpec = AgentBayuMotion.snappySpring
                ),
                exit = fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = 0.7f)
            ) {
                val image = remember(screenshot) { screenshot?.asImageBitmap() }
                val ratio = screenshot?.let { shot ->
                    if (shot.height == 0) 1f else shot.width.toFloat() / shot.height.toFloat()
                } ?: 1f
                Image(
                    bitmap = image!!,
                    contentDescription = stringResource(R.string.overlay_screenshot),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(56.dp)
                        .aspectRatio(ratio)
                        .clip(GlassTileShape)
                )
            }
        }
    }
}

@Composable
private fun DragPill(
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { DRAG_THRESHOLD.toPx() }
    val maxDrag = with(density) { DRAG_MAX.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    val description = stringResource(R.string.overlay_handle)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        offset = (offset + amount).coerceIn(-maxDrag, maxDrag)
                    },
                    onDragEnd = {
                        val dragged = offset
                        when {
                            dragged <= -threshold -> onOpenApp()
                            dragged >= threshold -> onDismiss()
                            else -> settle(offset, scope) { value -> offset = value }
                        }
                    },
                    onDragCancel = {
                        settle(offset, scope) { value -> offset = value }
                    }
                )
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = offset }
                .size(width = 44.dp, height = 5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        )
    }
}

private fun settle(
    from: Float,
    scope: CoroutineScope,
    onValue: (Float) -> Unit
) {
    scope.launch {
        animate(
            initialValue = from,
            targetValue = 0f,
            animationSpec = AgentBayuMotion.snappySpring
        ) { value, _ -> onValue(value) }
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
            .liquidGlass(shape = CapsuleShape)
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
private val DRAG_THRESHOLD = 72.dp
private val DRAG_MAX = 140.dp
private const val INITIAL_ENTRY_OFFSET_PX = 3000f
