package dev.agentbayu.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
import dev.agentbayu.app.ui.theme.liftShadow

@Composable
fun PromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isResponding: Boolean = false,
    onStop: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    attachments: List<MessageAttachment> = emptyList(),
    canAttach: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (MessageAttachment) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val controlsEnabled = enabled && !isResponding
    val canSend = controlsEnabled && (value.isNotBlank() || attachments.isNotEmpty())
    val trailingActive = isResponding || canSend
    var multiline by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val expanded = multiline || attachments.isNotEmpty()
    val sendScale by animateFloatAsState(
        targetValue = if (trailingActive) 1f else 0.85f,
        animationSpec = AgentBayuMotion.snappySpring,
        label = "sendScale"
    )

    LaunchedEffect(value.isEmpty()) {
        if (value.isEmpty()) {
            multiline = false
        }
    }

    val submit = {
        if (canSend) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSend()
        }
    }

    val stop = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onStop()
    }

    val compactStart = if (canAttach) COMPACT_START_WITH_ATTACH else COMPACT_START_PLAIN
    val textStart = if (expanded) EXPANDED_TEXT_INSET else compactStart
    val textEnd = if (expanded) EXPANDED_TEXT_INSET else COMPACT_END_RESERVE
    val textVertical = if (expanded) EXPANDED_TEXT_TOP else 0.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liftShadow(ComposerShape)
            .clip(ComposerShape)
            .background(LocalAppSurfaces.current.composer)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AnimatedVisibility(
                visible = attachments.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Top) +
                    fadeIn(AgentBayuMotion.quickFade),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) +
                    fadeOut(AgentBayuMotion.quickFade)
            ) {
                AttachmentStrip(
                    attachments = attachments,
                    enabled = controlsEnabled,
                    onRemove = onRemoveAttachment
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (expanded) EXPANDED_TEXT_MIN_HEIGHT else COMPOSER_MIN_HEIGHT)
                    .padding(
                        start = textStart,
                        end = textEnd,
                        top = textVertical,
                        bottom = if (expanded) EXPANDED_TEXT_BOTTOM else 0.dp
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = controlsEnabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = scheme.onSurface
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default
                    ),
                    maxLines = MAX_INPUT_LINES,
                    onTextLayout = { layout ->
                        if (layout.lineCount > 1) {
                            multiline = true
                        } else if (multiline && value.isNotEmpty() && !value.contains('\n')) {
                            val slack = with(density) {
                                (compactStart + COMPACT_END_RESERVE -
                                    EXPANDED_TEXT_INSET - EXPANDED_TEXT_INSET +
                                    COLLAPSE_MARGIN).toPx()
                            }
                            if (layout.getLineRight(0) <= fieldWidth - slack) {
                                multiline = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size -> fieldWidth = size.width }
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(CONTROLS_ROW_HEIGHT))
            }
        }

        if (canAttach) {
            ComposerIconButton(
                iconRes = R.drawable.ic_image,
                description = stringResource(R.string.chat_attach),
                enabled = controlsEnabled,
                onClick = onAttachClick,
                modifier = Modifier
                    .align(if (expanded) Alignment.BottomStart else Alignment.CenterStart)
                    .padding(CONTROL_EDGE_PADDING)
            )
        }

        Row(
            modifier = Modifier
                .align(if (expanded) Alignment.BottomEnd else Alignment.CenterEnd)
                .padding(CONTROL_EDGE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)
        ) {
            ComposerIconButton(
                iconRes = R.drawable.ic_mic,
                description = stringResource(R.string.chat_mic),
                enabled = controlsEnabled,
                onClick = onMicClick
            )
            SendButton(
                responding = isResponding,
                active = trailingActive,
                onClick = if (isResponding) stop else submit,
                modifier = Modifier.scale(sendScale)
            )
        }
    }
}

@Composable
private fun ComposerIconButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SendButton(
    responding: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurface.copy(alpha = 0.12f),
        label = "sendContainer"
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) scheme.onPrimary else scheme.onSurface.copy(alpha = 0.4f),
        label = "sendIconColor"
    )
    Box(
        modifier = modifier
            .size(SEND_BUTTON_SIZE)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = responding,
            transitionSpec = {
                (fadeIn(AgentBayuMotion.quickFade) + scaleIn(initialScale = SEND_ICON_ENTER_SCALE)) togetherWith
                    (fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = SEND_ICON_ENTER_SCALE))
            },
            label = "sendIcon"
        ) { isResponding ->
            Icon(
                painter = painterResource(
                    if (isResponding) R.drawable.ic_stop else R.drawable.ic_send
                ),
                contentDescription = stringResource(
                    if (isResponding) R.string.chat_stop else R.string.chat_send
                ),
                tint = iconColor,
                modifier = Modifier.size(if (isResponding) 16.dp else 18.dp)
            )
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<MessageAttachment>,
    enabled: Boolean,
    onRemove: (MessageAttachment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            Box {
                AttachmentThumbnail(attachment = attachment, size = 56.dp)
                GlassButton(
                    onClick = { onRemove(attachment) },
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp),
                    shape = CircleShape,
                    contentPadding = GlassButtonDefaults.IconPadding
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.chat_attach_remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

private val ComposerShape = RoundedCornerShape(26.dp)
private val COMPOSER_MIN_HEIGHT = 52.dp
private val SEND_BUTTON_SIZE = 40.dp
private val CONTROL_EDGE_PADDING = 6.dp
private val CONTROL_GAP = 4.dp
private val CONTROLS_ROW_HEIGHT = 46.dp
private val COMPACT_START_WITH_ATTACH = 52.dp
private val COMPACT_START_PLAIN = 18.dp
private val COMPACT_END_RESERVE = 96.dp
private val EXPANDED_TEXT_INSET = 18.dp
private val EXPANDED_TEXT_TOP = 14.dp
private val EXPANDED_TEXT_BOTTOM = 4.dp
private val EXPANDED_TEXT_MIN_HEIGHT = 24.dp
private val COLLAPSE_MARGIN = 12.dp
private const val MAX_INPUT_LINES = 8
private const val SEND_ICON_ENTER_SCALE = 0.85f
