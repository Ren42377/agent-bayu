package dev.agentbayu.app.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassFab
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.GlassSegmentedSelector
import dev.agentbayu.app.ui.components.MarkdownMessage
import dev.agentbayu.app.ui.components.insertImageBlock
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
import dev.agentbayu.app.ui.theme.LocalScreenInsets

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
    val focusManager = LocalFocusManager.current
    val activeBlockFocusRequester = remember { FocusRequester() }
    var modeIndex by rememberSaveable { mutableIntStateOf(NoteEditorMode.EDIT.ordinal) }
    val mode = NoteEditorMode.values()[modeIndex.coerceIn(0, NoteEditorMode.values().lastIndex)]
    var currentContent by remember { mutableStateOf(draft.content) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length)))
    }
    var activeBlock by remember { mutableStateOf<LiveBlockEdit?>(null) }
    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }
    var lastEditTime by remember { mutableStateOf(0L) }
    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()
    val scrollState = rememberScrollState()

    fun updateContent(content: String, selection: TextRange? = null) {
        if (content == currentContent) return
        val now = System.currentTimeMillis()
        if (now - lastEditTime > EDIT_GROUP_WINDOW_MILLIS || undoStack.isEmpty()) {
            undoStack.add(currentContent)
        }
        lastEditTime = now
        redoStack.clear()
        currentContent = content
        val safeSelection = selection?.let {
            TextRange(
                it.start.coerceIn(0, content.length),
                it.end.coerceIn(0, content.length)
            )
        } ?: TextRange(content.length)
        contentValue = TextFieldValue(content, safeSelection)
        onDraftChange(draft.copy(content = content))
    }

    fun updateLiveBlock(edit: LiveBlockEdit, value: TextFieldValue) {
        val currentEdit = activeBlock?.takeIf { it.start == edit.start } ?: edit
        if (value.text == currentEdit.value.text) {
            activeBlock = currentEdit.copy(value = value)
            val selectionStart = currentEdit.start + currentEdit.prefix.length + value.selection.start
            val selectionEnd = currentEdit.start + currentEdit.prefix.length + value.selection.end
            contentValue = TextFieldValue(
                currentContent,
                TextRange(selectionStart, selectionEnd)
            )
            return
        }
        val replacement = currentEdit.prefix + value.text
        val updated = currentContent.replaceRange(currentEdit.start, currentEdit.end, replacement)
        val selectionStart = currentEdit.start + currentEdit.prefix.length + value.selection.start
        val selectionEnd = currentEdit.start + currentEdit.prefix.length + value.selection.end
        activeBlock = currentEdit.copy(
            end = currentEdit.start + replacement.length,
            value = value,
            prefix = ""
        )
        updateContent(updated, TextRange(selectionStart, selectionEnd))
    }

    LaunchedEffect(draft.content) {
        currentContent = draft.content
        if (draft.content != contentValue.text) {
            contentValue = TextFieldValue(draft.content, TextRange(draft.content.length))
        }
    }

    LaunchedEffect(activeBlock?.start) {
        if (activeBlock != null) {
            runCatching { activeBlockFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(snippet) {
        val pending = snippet ?: return@LaunchedEffect
        when {
            mode == NoteEditorMode.EDIT -> {
                val selection = contentValue.selection
                val inserted = insertImageBlock(
                    text = contentValue.text,
                    selectionStart = selection.min,
                    selectionEnd = selection.max,
                    snippet = pending
                )
                updateContent(inserted.text, TextRange(inserted.cursor))
            }

            mode == NoteEditorMode.LIVE && activeBlock != null -> {
                val edit = activeBlock ?: return@LaunchedEffect
                val selection = edit.value.selection
                val inserted = insertImageBlock(
                    text = edit.value.text,
                    selectionStart = selection.min,
                    selectionEnd = selection.max,
                    snippet = pending
                )
                updateLiveBlock(
                    edit,
                    TextFieldValue(inserted.text, TextRange(inserted.cursor))
                )
            }

            else -> {
                val text = currentContent
                val inserted = insertImageBlock(
                    text = text,
                    selectionStart = text.length,
                    selectionEnd = text.length,
                    snippet = pending
                )
                updateContent(inserted.text, TextRange(inserted.cursor))
            }
        }
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
                        .verticalScroll(scrollState)
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
                    GlassSegmentedSelector(
                        labels = listOf(
                            stringResource(R.string.notes_editor_write),
                            stringResource(R.string.notes_editor_live),
                            stringResource(R.string.notes_editor_preview)
                        ),
                        selectedIndex = mode.ordinal,
                        onSelect = { selected ->
                            if (selected != mode.ordinal) {
                                activeBlock = null
                                focusManager.clearFocus()
                                modeIndex = selected
                            }
                        }
                    )
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "editorMode",
                        modifier = Modifier.fillMaxWidth()
                    ) { targetMode ->
                        when (targetMode) {
                            NoteEditorMode.EDIT -> TextField(
                                value = contentValue,
                                onValueChange = { value ->
                                    contentValue = value
                                    updateContent(value.text, value.selection)
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

                            NoteEditorMode.LIVE -> LiveMarkdownEditor(
                                content = currentContent,
                                activeBlock = activeBlock,
                                focusRequester = activeBlockFocusRequester,
                                onSelectBlock = { block ->
                                    val cursor = block.start + block.source.length
                                    contentValue = TextFieldValue(currentContent, TextRange(cursor))
                                    activeBlock = LiveBlockEdit(
                                        start = block.start,
                                        end = block.end,
                                        value = TextFieldValue(
                                            block.source,
                                            TextRange(block.source.length)
                                        )
                                    )
                                },
                                onAppendBlock = {
                                    val source = currentContent
                                    contentValue = TextFieldValue(source, TextRange(source.length))
                                    activeBlock = LiveBlockEdit(
                                        start = source.length,
                                        end = source.length,
                                        value = TextFieldValue(""),
                                        prefix = appendSeparator(source)
                                    )
                                },
                                onBlockValueChange = { edit, value -> updateLiveBlock(edit, value) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            NoteEditorMode.PREVIEW -> MarkdownMessage(
                                content = currentContent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                autoEmbedImages = true
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
                        focusManager.clearFocus()
                        redoStack.add(currentContent)
                        val restored = undoStack.removeAt(undoStack.lastIndex)
                        activeBlock = null
                        currentContent = restored
                        contentValue = TextFieldValue(restored, TextRange(restored.length))
                        onDraftChange(draft.copy(content = restored))
                        lastEditTime = 0L
                    }
                },
                enabled = canUndo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.notes_editor_undo),
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = {
                    if (canRedo) {
                        focusManager.clearFocus()
                        undoStack.add(currentContent)
                        val restored = redoStack.removeAt(redoStack.lastIndex)
                        activeBlock = null
                        currentContent = restored
                        contentValue = TextFieldValue(restored, TextRange(restored.length))
                        onDraftChange(draft.copy(content = restored))
                        lastEditTime = 0L
                    }
                },
                enabled = canRedo
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.notes_editor_redo),
                    modifier = Modifier.size(24.dp)
                )
            }
            GlassFab(
                onClick = {
                    activeBlock = null
                    focusManager.clearFocus()
                    onSave()
                }
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
private fun LiveMarkdownEditor(
    content: String,
    activeBlock: LiveBlockEdit?,
    focusRequester: FocusRequester,
    onSelectBlock: (MarkdownSourceBlock) -> Unit,
    onAppendBlock: () -> Unit,
    onBlockValueChange: (LiveBlockEdit, TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.notes_editor_live_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        if (activeBlock == null) {
            val blocks = remember(content) { markdownSourceBlocks(content) }
            blocks.forEach { block ->
                MarkdownMessage(
                    content = block.source,
                    autoEmbedImages = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBlock(block) }
                )
            }
        } else {
            val start = activeBlock.start.coerceIn(0, content.length)
            val end = activeBlock.end.coerceIn(start, content.length)
            val before = content.substring(0, start)
            val after = content.substring(end)
            val beforeBlocks = remember(before) { markdownSourceBlocks(before) }
            val afterBlocks = remember(after) { markdownSourceBlocks(after) }
            beforeBlocks.forEach { block ->
                MarkdownMessage(
                    content = block.source,
                    autoEmbedImages = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBlock(block) }
                )
            }
            TextField(
                value = activeBlock.value,
                onValueChange = { onBlockValueChange(activeBlock, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .focusRequester(focusRequester),
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
            afterBlocks.forEach { block ->
                val absoluteBlock = block.copy(
                    start = block.start + end,
                    end = block.end + end
                )
                MarkdownMessage(
                    content = block.source,
                    autoEmbedImages = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBlock(absoluteBlock) }
                )
            }
        }
        if (activeBlock == null) {
            Text(
                text = stringResource(R.string.notes_editor_add_block),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAppendBlock)
                    .padding(vertical = 12.dp)
            )
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

private fun appendSeparator(source: String): String = when {
    source.isEmpty() || source.endsWith("\n\n") -> ""
    source.endsWith('\n') -> "\n"
    else -> "\n\n"
}

private data class LiveBlockEdit(
    val start: Int,
    val end: Int,
    val value: TextFieldValue,
    val prefix: String = ""
)

private enum class NoteEditorMode {
    EDIT,
    LIVE,
    PREVIEW
}

private const val EDIT_GROUP_WINDOW_MILLIS = 600L
