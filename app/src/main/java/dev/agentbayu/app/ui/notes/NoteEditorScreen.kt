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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.Font
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
import kotlinx.coroutines.delay

@Composable
fun NoteEditorScreen(
    isNew: Boolean,
    draft: NoteDraft,
    undoHistory: List<String>,
    redoHistory: List<String>,
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
    var nextLiveEditSessionId by remember { mutableStateOf(0L) }
    var lastEditTime by remember { mutableStateOf(0L) }
    val history = NoteEditHistory(undoHistory, redoHistory)
    val canUndo = undoHistory.isNotEmpty()
    val canRedo = redoHistory.isNotEmpty()
    val scrollState = rememberScrollState()

    fun newLiveEditSessionId(): Long {
        nextLiveEditSessionId += 1
        return nextLiveEditSessionId
    }

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
                                        ),
                                        sessionId = newLiveEditSessionId()
                                    )
                                },
                                onAppendBlock = {
                                    val source = currentContent
                                    contentValue = TextFieldValue(source, TextRange(source.length))
                                    activeBlock = LiveBlockEdit(
                                        start = source.length,
                                        end = source.length,
                                        value = TextFieldValue(""),
                                        sessionId = newLiveEditSessionId(),
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
    val latestActiveBlock = rememberUpdatedState(activeBlock)
    val latestOnSelectBlock = rememberUpdatedState(onSelectBlock)
    val latestOnBlockValueChange = rememberUpdatedState(onBlockValueChange)
    val selectBlock = remember {
        { entry: LiveMarkdownEntry ->
            latestOnSelectBlock.value(entry.toSourceBlock(latestActiveBlock.value))
        }
    }
    val updateBlock = remember {
        { edit: LiveBlockEdit, value: TextFieldValue ->
            latestOnBlockValueChange.value(edit, value)
        }
    }
    val inactiveBlocks = if (activeBlock == null) {
        remember(content) { markdownSourceBlocks(content) }
    } else {
        emptyList()
    }
    val liveSession = remember(activeBlock?.sessionId) {
        activeBlock?.let { createLiveMarkdownSession(content, it) }
    }
    val activeEntries = remember(activeBlock?.sessionId) {
        activeBlock?.let { liveSession?.entries(it) }.orEmpty()
    }
    val entries = if (activeBlock == null) {
        inactiveBlocks.mapIndexed { index, block ->
            LiveMarkdownEntry(
                identity = index,
                source = block.source,
                selectionStart = block.start,
                selectionEnd = block.end,
                isEditing = false
            )
        }
    } else {
        activeEntries
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { entry ->
            key(entry.identity) {
                LiveMarkdownBlock(
                    entry = entry,
                    activeBlock = if (entry.isEditing) activeBlock else null,
                    onSelectBlock = selectBlock,
                    onBlockValueChange = updateBlock,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (activeBlock == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                GlassIconButton(
                    onClick = onAppendBlock,
                    size = 38.dp
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.notes_editor_add_block),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveMarkdownBlock(
    entry: LiveMarkdownEntry,
    activeBlock: LiveBlockEdit?,
    onSelectBlock: (LiveMarkdownEntry) -> Unit,
    onBlockValueChange: (LiveBlockEdit, TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val isEditing = entry.isEditing && activeBlock != null
    var previewContent by remember(activeBlock?.sessionId) {
        mutableStateOf(activeBlock?.value?.text.orEmpty())
    }

    LaunchedEffect(isEditing, activeBlock?.sessionId) {
        if (isEditing) {
            withFrameNanos { }
            focusRequester.requestFocus()
            bringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(activeBlock?.sessionId, activeBlock?.value?.text) {
        val latestContent = activeBlock?.value?.text.orEmpty()
        if (latestContent != previewContent) {
            delay(LIVE_PREVIEW_DEBOUNCE_MILLIS)
            previewContent = latestContent
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
        modifier = modifier
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
                        fontFamily = LiveEditorFontFamily
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            innerTextField()
                        }
                    }
                )
                if (previewContent.isNotBlank()) {
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
                            content = previewContent,
                            autoEmbedImages = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        } else {
            MarkdownMessage(
                content = entry.source,
                autoEmbedImages = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectBlock(entry) }
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

@Immutable
private data class LiveBlockEdit(
    val start: Int,
    val end: Int,
    val value: TextFieldValue,
    val sessionId: Long,
    val prefix: String = ""
)

@Immutable
private data class LiveMarkdownEntry(
    val identity: Int,
    val source: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val isEditing: Boolean,
    val selectionIsRelativeToActiveEnd: Boolean = false
) {
    fun toSourceBlock(activeBlock: LiveBlockEdit?): MarkdownSourceBlock {
        val offset = if (selectionIsRelativeToActiveEnd) activeBlock?.end ?: 0 else 0
        return MarkdownSourceBlock(
            start = selectionStart + offset,
            end = selectionEnd + offset,
            source = source
        )
    }
}

@Immutable
private data class LiveMarkdownSession(
    val beforeBlocks: List<MarkdownSourceBlock>,
    val afterBlocks: List<MarkdownSourceBlock>
) {
    fun entries(activeBlock: LiveBlockEdit): List<LiveMarkdownEntry> = buildList {
        beforeBlocks.forEachIndexed { index, block ->
            add(
                LiveMarkdownEntry(
                    identity = index,
                    source = block.source,
                    selectionStart = block.start,
                    selectionEnd = block.end,
                    isEditing = false
                )
            )
        }
        add(
            LiveMarkdownEntry(
                identity = beforeBlocks.size,
                source = activeBlock.value.text,
                selectionStart = activeBlock.start,
                selectionEnd = activeBlock.end,
                isEditing = true
            )
        )
        afterBlocks.forEachIndexed { index, block ->
            add(
                LiveMarkdownEntry(
                    identity = beforeBlocks.size + index + 1,
                    source = block.source,
                    selectionStart = block.start,
                    selectionEnd = block.end,
                    isEditing = false,
                    selectionIsRelativeToActiveEnd = true
                )
            )
        }
    }
}

private fun createLiveMarkdownSession(
    content: String,
    activeBlock: LiveBlockEdit
): LiveMarkdownSession {
    val start = activeBlock.start.coerceIn(0, content.length)
    val end = activeBlock.end.coerceIn(start, content.length)
    return LiveMarkdownSession(
        beforeBlocks = markdownSourceBlocks(content.substring(0, start)),
        afterBlocks = markdownSourceBlocks(content.substring(end))
    )
}

private enum class NoteEditorMode {
    EDIT,
    LIVE,
    PREVIEW
}

private val LiveEditorFontFamily = FontFamily(Font(R.font.jetbrains_mono_regular))

private const val LIVE_PREVIEW_DEBOUNCE_MILLIS = 180L
