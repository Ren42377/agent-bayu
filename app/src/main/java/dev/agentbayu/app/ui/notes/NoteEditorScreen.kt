package dev.agentbayu.app.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.MarkdownMessage
import dev.agentbayu.app.ui.components.insertImageBlock
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.LocalScreenInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import dev.agentbayu.app.ui.components.GlassFab
import dev.agentbayu.app.ui.theme.LocalAppSurfaces

@Composable
fun NoteEditorScreen(
    isNew: Boolean,
    draft: NoteDraft,
    onDraftChange: (NoteDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onAddImage: () -> Unit,
    snippet: String?,
    onSnippetConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    var preview by rememberSaveable { mutableStateOf(false) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length)))
    }
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var lastEditTime by remember { mutableStateOf(0L) }
    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()
    LaunchedEffect(draft.content) {
        if (draft.content != contentValue.text) {
            contentValue = TextFieldValue(draft.content, TextRange(draft.content.length))
        }
    }
    LaunchedEffect(snippet) {
        val pending = snippet ?: return@LaunchedEffect
        val selection = contentValue.selection
        val inserted = insertImageBlock(
            text = contentValue.text,
            selectionStart = selection.min,
            selectionEnd = selection.max,
            snippet = pending
        )
        contentValue = TextFieldValue(inserted.text, TextRange(inserted.cursor))
        onDraftChange(draft.copy(content = inserted.text))
        onSnippetConsumed()
    }
    val pinTint by animateColorAsState(
        targetValue = if (draft.pinned) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "pinTint"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AiScreenHeader(
                title = stringResource(
                    if (isNew) R.string.notes_editor_new else R.string.notes_editor_title
                ),
                onBack = onBack
            ) {
                GlassIconButton(
                    onClick = onAddImage,
                    size = 38.dp
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_image),
                        contentDescription = stringResource(R.string.notes_image_add),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                GlassIconButton(
                    onClick = { onDraftChange(draft.copy(pinned = !draft.pinned)) },
                    size = 38.dp
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = stringResource(
                            if (draft.pinned) R.string.notes_unpin else R.string.notes_pin
                        ),
                        tint = pinTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                GlassIconButton(
                    onClick = { preview = !preview },
                    size = 38.dp
                ) {
                    AnimatedContent(
                        targetState = preview,
                        transitionSpec = {
                            (fadeIn(AgentBayuMotion.quickFade) + scaleIn(initialScale = EDITOR_ICON_ENTER_SCALE)) togetherWith
                                (fadeOut(AgentBayuMotion.quickFade) + scaleOut(targetScale = EDITOR_ICON_ENTER_SCALE))
                        },
                        label = "editorModeIcon"
                    ) { previewMode ->
                        Icon(
                            painter = painterResource(
                                if (previewMode) R.drawable.ic_edit else R.drawable.ic_visibility
                            ),
                            contentDescription = stringResource(
                                if (previewMode) R.string.notes_editor_write
                                else R.string.notes_editor_preview
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (!isNew) {
                    GlassIconButton(
                        onClick = onDelete,
                        size = 38.dp
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.notes_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = insets.calculateBottomPadding() + 88.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextField(
                        value = draft.title,
                        onValueChange = { onDraftChange(draft.copy(title = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.notes_editor_title_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        colors = editorFieldColors()
                    )
                    AnimatedContent(
                        targetState = preview,
                        transitionSpec = {
                            fadeIn(AgentBayuMotion.quickFade) togetherWith
                                fadeOut(AgentBayuMotion.quickFade)
                        },
                        label = "editorMode",
                        modifier = Modifier.fillMaxWidth()
                    ) { previewMode ->
                        if (previewMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                            ) {
                                MarkdownMessage(
                                    content = draft.content,
                                    modifier = Modifier.fillMaxWidth(),
                                    autoEmbedImages = true
                                )
                            }
                        } else {
                            TextField(
                                value = contentValue,
                                onValueChange = { value ->
                                    val oldText = contentValue.text
                                    contentValue = value
                                    if (value.text != draft.content) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastEditTime > 600L || undoStack.isEmpty()) {
                                            undoStack.add(oldText)
                                        }
                                        lastEditTime = now
                                        redoStack.clear()
                                        onDraftChange(draft.copy(content = value.text))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 320.dp),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.notes_editor_content_hint),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = MaterialTheme.shapes.large,
                                colors = editorFieldColors()
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = 20.dp + insets.calculateBottomPadding()
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassFab(
                onClick = {
                    if (canUndo) {
                        redoStack.add(contentValue.text)
                        val popped = undoStack.removeAt(undoStack.lastIndex)
                        contentValue = TextFieldValue(popped, TextRange(popped.length))
                        onDraftChange(draft.copy(content = popped))
                        lastEditTime = 0L
                    }
                },
                enabled = canUndo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = "Undo",
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = {
                    if (canRedo) {
                        undoStack.add(contentValue.text)
                        val popped = redoStack.removeAt(redoStack.lastIndex)
                        contentValue = TextFieldValue(popped, TextRange(popped.length))
                        onDraftChange(draft.copy(content = popped))
                        lastEditTime = 0L
                    }
                },
                enabled = canRedo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = "Redo",
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = onSave
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = stringResource(R.string.tasks_detail_save),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun editorFieldColors(): TextFieldColors {
    val container = LocalAppSurfaces.current.container
    return TextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        errorContainerColor = container,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent
    )
}


private const val EDITOR_ICON_ENTER_SCALE = 0.85f
