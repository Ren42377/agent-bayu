package dev.agentbayu.app.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.animateContentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.agentbayu.app.R
import dev.agentbayu.app.ui.ai.AiScreenHeader
import dev.agentbayu.app.ui.components.GlassFab
import dev.agentbayu.app.ui.components.GlassIconButton
import dev.agentbayu.app.ui.components.MarkdownMessage
import dev.agentbayu.app.ui.components.insertImageBlock
import dev.agentbayu.app.ui.theme.AgentBayuMotion
import dev.agentbayu.app.ui.theme.LocalAppSurfaces
import dev.agentbayu.app.ui.theme.LocalScreenInsets

@Composable
fun NoteEditorScreen(
    isNew: Boolean,
    draft: NoteDraft,
    undoHistory: List<String>,
    redoHistory: List<String>,
    saveStatus: String,
    onDraftChange: (NoteDraft) -> Unit,
    onHistoryChange: (List<String>, List<String>) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onAddImage: () -> Unit,
    snippet: String?,
    onSnippetConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val insets = LocalScreenInsets.current
    val focusManager = LocalFocusManager.current
    var modeIndex by rememberSaveable { mutableIntStateOf(NoteEditorMode.EDIT.ordinal) }
    val mode = NoteEditorMode.values()[modeIndex.coerceIn(0, NoteEditorMode.values().lastIndex)]
    var currentContent by remember { mutableStateOf(draft.content) }
    var contentValue by remember {
        mutableStateOf(TextFieldValue(draft.content, TextRange(draft.content.length)))
    }
    var activeBlock by remember { mutableStateOf<LiveBlockEdit?>(null) }
    var lastEditTime by remember { mutableStateOf(0L) }
    val history = NoteEditHistory(undoHistory, redoHistory)
    val canUndo = undoHistory.isNotEmpty()
    val canRedo = redoHistory.isNotEmpty()
    val scrollState = rememberScrollState()

    fun updateContent(content: String, selection: TextRange? = null) {
        if (content == currentContent) return
        val now = System.currentTimeMillis()
        val nextHistory = recordNoteEdit(currentContent, history, now, lastEditTime)
        onHistoryChange(nextHistory.undo, nextHistory.redo)
        lastEditTime = now
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
            start = currentEdit.start + currentEdit.prefix.length,
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
            .pointerInput(mode, activeBlock != null) {
                if (mode == NoteEditorMode.LIVE && activeBlock != null) {
                    detectTapGestures {
                        activeBlock = null
                        focusManager.clearFocus()
                    }
                }
            }
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
                    onClick = {
                        activeBlock = null
                        focusManager.clearFocus()
                        modeIndex = (modeIndex + 1) % NoteEditorMode.values().size
                    },
                    size = 38.dp
                ) {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "editorModeIcon"
                    ) { currentMode ->
                        Icon(
                            painter = painterResource(
                                when (currentMode) {
                                    NoteEditorMode.EDIT -> R.drawable.ic_edit
                                    NoteEditorMode.LIVE -> R.drawable.ic_code
                                    NoteEditorMode.PREVIEW -> R.drawable.ic_visibility
                                }
                            ),
                            contentDescription = stringResource(
                                when (currentMode) {
                                    NoteEditorMode.EDIT -> R.string.notes_editor_mode_edit
                                    NoteEditorMode.LIVE -> R.string.notes_editor_mode_live
                                    NoteEditorMode.PREVIEW -> R.string.notes_editor_mode_preview
                                }
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
                    if (saveStatus.isNotEmpty()) {
                        Text(
                            text = saveStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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
                        val transition = undoNoteEdit(currentContent, history) ?: return@GlassFab
                        onHistoryChange(transition.history.undo, transition.history.redo)
                        activeBlock = null
                        currentContent = transition.content
                        contentValue = TextFieldValue(
                            transition.content,
                            TextRange(transition.content.length)
                        )
                        onDraftChange(draft.copy(content = transition.content))
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
                        val transition = redoNoteEdit(currentContent, history) ?: return@GlassFab
                        onHistoryChange(transition.history.undo, transition.history.redo)
                        activeBlock = null
                        currentContent = transition.content
                        contentValue = TextFieldValue(
                            transition.content,
                            TextRange(transition.content.length)
                        )
                        onDraftChange(draft.copy(content = transition.content))
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
        }
    }
}

@Composable
private fun LiveMarkdownEditor(
    content: String,
    activeBlock: LiveBlockEdit?,
    onSelectBlock: (MarkdownSourceBlock) -> Unit,
    onAppendBlock: () -> Unit,
    onBlockValueChange: (LiveBlockEdit, TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = activeBlock != null
    val start = activeBlock?.start?.coerceIn(0, content.length) ?: 0
    val end = activeBlock?.end?.coerceIn(start, content.length) ?: 0
    val before = if (isEditing) content.substring(0, start) else ""
    val after = if (isEditing) content.substring(end) else ""
    val inactiveBlocks = remember(content, isEditing) {
        if (isEditing) emptyList() else markdownSourceBlocks(content)
    }
    val beforeBlocks = remember(isEditing, before) {
        if (isEditing) markdownSourceBlocks(before) else emptyList()
    }
    val afterBlocks = remember(isEditing, after) {
        if (isEditing) markdownSourceBlocks(after) else emptyList()
    }
    val entries = if (activeBlock == null) {
        inactiveBlocks.mapIndexed { index, block ->
            LiveMarkdownEntry(index, block, isEditing = false)
        }
    } else {
        buildList {
            beforeBlocks.forEachIndexed { index, block ->
                add(LiveMarkdownEntry(index, block, isEditing = false))
            }
            val activeSource = content.substring(start, end)
            add(
                LiveMarkdownEntry(
                    identity = beforeBlocks.size,
                    block = MarkdownSourceBlock(start, end, activeSource),
                    isEditing = true
                )
            )
            afterBlocks.forEachIndexed { index, block ->
                add(
                    LiveMarkdownEntry(
                        identity = beforeBlocks.size + index + 1,
                        block = block.copy(start = block.start + end, end = block.end + end),
                        isEditing = false
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.notes_editor_live_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            entries.forEach { entry ->
                key(entry.identity) {
                    LiveMarkdownBlock(
                        entry = entry,
                        activeBlock = if (entry.isEditing) activeBlock else null,
                        onSelectBlock = onSelectBlock,
                        onBlockValueChange = onBlockValueChange,
                        modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun LiveMarkdownBlock(
    entry: LiveMarkdownEntry,
    activeBlock: LiveBlockEdit?,
    onSelectBlock: (MarkdownSourceBlock) -> Unit,
    onBlockValueChange: (LiveBlockEdit, TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val isEditing = entry.isEditing && activeBlock != null

    LaunchedEffect(isEditing, activeBlock?.start) {
        if (isEditing) {
            withFrameNanos { }
            focusRequester.requestFocus()
            bringIntoViewRequester.bringIntoView()
        }
    }

    AnimatedContent(
        targetState = isEditing,
        transitionSpec = {
            (
                fadeIn(AgentBayuMotion.quickFade) +
                    expandVertically(expandFrom = Alignment.Top)
                ) togetherWith (
                fadeOut(AgentBayuMotion.quickFade) +
                    shrinkVertically(shrinkTowards = Alignment.Top)
                )
        },
        contentAlignment = Alignment.TopStart,
        label = "liveMarkdownBlock",
        modifier = modifier.animateContentSize()
    ) { editing ->
        if (editing && activeBlock != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicTextField(
                    value = activeBlock.value,
                    onValueChange = { onBlockValueChange(activeBlock, it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            if (activeBlock.value.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.notes_editor_content_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (activeBlock.value.text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(LocalAppSurfaces.current.container)
                            .pointerInput(Unit) {
                                detectTapGestures { }
                            }
                            .padding(8.dp)
                    ) {
                        MarkdownMessage(
                            content = activeBlock.value.text,
                            autoEmbedImages = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            MarkdownMessage(
                content = entry.block.source,
                autoEmbedImages = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectBlock(entry.block) }
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

private data class LiveMarkdownEntry(
    val identity: Int,
    val block: MarkdownSourceBlock,
    val isEditing: Boolean
)

private enum class NoteEditorMode {
    EDIT,
    LIVE,
    PREVIEW
}
