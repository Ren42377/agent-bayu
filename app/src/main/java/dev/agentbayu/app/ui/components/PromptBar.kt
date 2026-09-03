package dev.agentbayu.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.CapsuleShape
import dev.agentbayu.app.ui.theme.GlassCardShape
import dev.agentbayu.app.ui.theme.LocalGlassBackdrop
import dev.agentbayu.app.ui.theme.liquidGlass

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
    attachments: List<MessageAttachment> = emptyList(),
    canAttach: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (MessageAttachment) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val barBackdrop = rememberLayerBackdrop()
    val buttonBackdrop = rememberCombinedBackdrop(LocalGlassBackdrop.current, barBackdrop)
    val canSend = enabled && (value.isNotBlank() || attachments.isNotEmpty())
    val trailingActive = isResponding || canSend
    val barShape = if (attachments.isEmpty()) CapsuleShape else GlassCardShape
    val sendScale by animateFloatAsState(
        targetValue = if (trailingActive) 1f else 0.85f,
        animationSpec = AgentBayuMotion.snappySpring,
        label = "sendScale"
    )

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

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .liquidGlass(shape = barShape, exportedBackdrop = barBackdrop)
        )
        CompositionLocalProvider(LocalGlassBackdrop provides buttonBackdrop) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (attachments.isNotEmpty()) {
                    AttachmentStrip(
                        attachments = attachments,
                        onRemove = onRemoveAttachment
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassButton(
                        onClick = onMicClick,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        contentPadding = GlassButtonDefaults.IconPadding
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic),
                            contentDescription = stringResource(R.string.chat_mic),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (canAttach) {
                        Spacer(modifier = Modifier.width(6.dp))
                        GlassButton(
                            onClick = onAttachClick,
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            contentPadding = GlassButtonDefaults.IconPadding
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_image),
                                contentDescription = stringResource(R.string.chat_attach),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
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
                            enabled = enabled,
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

                    Spacer(modifier = Modifier.width(6.dp))

                    GlassButton(
                        onClick = if (isResponding) stop else submit,
                        modifier = Modifier
                            .size(40.dp)
                            .scale(sendScale),
                        enabled = trailingActive,
                        tint = when {
                            isResponding -> MaterialTheme.colorScheme.error
                            canSend -> MaterialTheme.colorScheme.primary
                            else -> Color.Unspecified
                        },
                        shape = CircleShape,
                        contentPadding = GlassButtonDefaults.IconPadding
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isResponding) R.drawable.ic_stop else R.drawable.ic_send
                            ),
                            contentDescription = stringResource(
                                if (isResponding) R.string.chat_stop else R.string.chat_send
                            ),
                            tint = when {
                                isResponding -> Color.White
                                canSend -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(if (isResponding) 16.dp else 18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<MessageAttachment>,
    onRemove: (MessageAttachment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachments.forEach { attachment ->
            Box {
                AttachmentThumbnail(attachment = attachment, size = 56.dp)
                GlassButton(
                    onClick = { onRemove(attachment) },
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
