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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.agentbayu.app.ai.ReasoningEffort
import dev.agentbayu.app.domain.MessageAttachment
import dev.agentbayu.app.ui.ai.ProviderOption
import dev.agentbayu.app.ui.ai.EffortSelector
import dev.agentbayu.app.ui.ai.effortColor
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
import dev.agentbayu.app.ui.theme.liftShadow

@Composable
fun PromptBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isResponding: Boolean = false,
    onStop: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    attachments: List<MessageAttachment> = emptyList(),
    canAttach: Boolean = false,
    onAttachClick: () -> Unit = {},
    onRemoveAttachment: (MessageAttachment) -> Unit = {},
    providerOptions: List<ProviderOption> = emptyList(),
    onSelectModel: (String, String) -> Unit = { _, _ -> },
    onSelectEffort: (String, ReasoningEffort) -> Unit = { _, _ -> }
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val controlsEnabled = enabled && !isResponding
    val canSend = controlsEnabled && (value.isNotBlank() || attachments.isNotEmpty())
    val trailingActive = isResponding || canSend
    var fieldWidth by remember { mutableIntStateOf(0) }
    var showEffortSlider by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    
    val sendScale by animateFloatAsState(
        targetValue = if (trailingActive) 1f else 0.85f,
        animationSpec = AgentBayuMotion.snappySpring,
        label = "sendScale"
    )

    val activeOption = providerOptions.find { it.isActive }

    val submit = {
        if (canSend) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSend()
            showEffortSlider = false
        }
    }

    val stop = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onStop()
    }

    val compactStart = EXPANDED_TEXT_INSET
    val textStart = EXPANDED_TEXT_INSET
    val textEnd = EXPANDED_TEXT_INSET
    val textVertical = EXPANDED_TEXT_TOP

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
                    .heightIn(min = EXPANDED_TEXT_MIN_HEIGHT)
                    .padding(
                        start = textStart,
                        end = textEnd,
                        top = textVertical,
                        bottom = EXPANDED_TEXT_BOTTOM
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showEffortSlider && activeOption != null && activeOption.efforts.isNotEmpty()) {
                    EffortControlRow(
                        option = activeOption,
                        onSelectEffort = { effort ->
                            onSelectEffort(activeOption.connectionId, effort)
                        }
                    )
                } else {
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
            }
            Spacer(modifier = Modifier.height(CONTROLS_ROW_HEIGHT))
        }

        if (canAttach) {
            ComposerIconButton(
                iconRes = R.drawable.ic_image,
                description = stringResource(R.string.chat_attach),
                enabled = controlsEnabled,
                onClick = onAttachClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(CONTROL_EDGE_PADDING)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CONTROL_EDGE_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)
        ) {
            Box {
                ComposerIconButton(
                    iconRes = R.drawable.ic_package,
                    description = "Models",
                    enabled = controlsEnabled,
                    onClick = { showModelPicker = !showModelPicker }
                )
                
                DropdownMenu(
                    expanded = showModelPicker,
                    onDismissRequest = { showModelPicker = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).widthIn(max = 240.dp)
                ) {
                    providerOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    option.providerLabel, 
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                ) 
                            },
                            onClick = {},
                            enabled = false
                        )
                        option.models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onSelectModel(option.connectionId, model)
                                    showModelPicker = false
                                },
                                trailingIcon = if (option.isActive && option.model == model) {
                                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }
                                } else null
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = activeOption?.efforts?.isNotEmpty() == true) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = controlsEnabled) { 
                            showEffortSlider = !showEffortSlider 
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeOption?.effort?.label ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
private fun EffortControlRow(option: ProviderOption, onSelectEffort: (ReasoningEffort) -> Unit) {
    var previewEffort by remember(option.effort) { mutableStateOf(option.effort) }
    val currentEffort = previewEffort ?: option.effort
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.picker_effort_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            currentEffort?.let { effort ->
                Text(
                    text = effort.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = effortColor(effort)
                )
            }
        }
        EffortSelector(
            options = option.efforts,
            selected = option.effort,
            onSelect = { effort ->
                previewEffort = effort
                onSelectEffort(effort)
            },
            onPreview = { effort ->
                previewEffort = effort
            }
        )
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
                modifier = Modifier.size(if (isResponding) 24.dp else 18.dp)
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
private val COMPOSER_MIN_HEIGHT = 92.dp
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
private val EXPANDED_TEXT_MIN_HEIGHT = 40.dp
private val COLLAPSE_MARGIN = 12.dp
private const val MAX_INPUT_LINES = 8
private const val SEND_ICON_ENTER_SCALE = 0.85f
