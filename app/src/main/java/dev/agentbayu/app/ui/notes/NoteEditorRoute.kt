package dev.agentbayu.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.platform.NoteImageStore
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.markdownImageFor
import dev.agentbayu.app.ui.tasks.TaskAction
import dev.agentbayu.app.ui.tasks.TaskActionSheet
import dev.agentbayu.app.ui.tasks.TaskTextDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NoteEditorRoute(
    noteId: String?,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.notes(context) }
    val notes by store.notes.collectAsState()
    val deletedMessage = stringResource(R.string.notes_deleted)
    val imageFailedMessage = stringResource(R.string.notes_image_failed)
    val autoSavePending = stringResource(R.string.notes_autosave_pending)
    val autoSaveSaving = stringResource(R.string.notes_autosave_saving)
    val autoSaveSaved = stringResource(R.string.notes_autosave_saved)
    val autoSaveFailed = stringResource(R.string.notes_autosave_failed)
    val scope = rememberCoroutineScope()
    val imageStore = remember(context) { NoteImageStore(context) }

    var resolvedNoteId by remember(noteId) { mutableStateOf(noteId) }
    val existing = resolvedNoteId?.let { id -> notes.firstOrNull { it.id == id } }
    var draft by remember(noteId) {
        mutableStateOf(draftOf(existing))
    }
    var undoHistory by remember(noteId) { mutableStateOf(existing?.undoHistory.orEmpty()) }
    var redoHistory by remember(noteId) { mutableStateOf(existing?.redoHistory.orEmpty()) }
    var lastSavedSnapshot by remember(noteId) {
        mutableStateOf(existing?.let(::editorSnapshotOf))
    }
    var autoSaveStatus by remember(noteId) {
        mutableStateOf(if (existing == null) "" else autoSaveSaved)
    }
    var deleteOpen by remember { mutableStateOf(false) }
    var imageSheetOpen by remember { mutableStateOf(false) }
    var imageLinkOpen by remember { mutableStateOf(false) }
    var pendingSnippet by remember { mutableStateOf<String?>(null) }
    var noteDeleted by remember(noteId) { mutableStateOf(false) }

    fun persistSnapshot(snapshot: NoteEditorSnapshot): Boolean {
        if (noteDeleted) return true
        val title = snapshot.draft.title.ifBlank { derivedTitle(snapshot.draft.content) }
        val content = snapshot.draft.content
        val id = resolvedNoteId ?: run {
            if (title.isBlank() && content.isBlank()) return true
            store.createNote(
                title = title,
                content = content,
                pinned = snapshot.draft.pinned,
                undoHistory = snapshot.undoHistory,
                redoHistory = snapshot.redoHistory
            ).also { createdId ->
                if (createdId.isEmpty()) return false
                resolvedNoteId = createdId
            }
        }
        val base = store.find(id) ?: return false
        store.upsertNote(
            base.copy(
                title = title,
                content = content,
                undoHistory = snapshot.undoHistory,
                redoHistory = snapshot.redoHistory
            )
        )
        if (base.pinned != snapshot.draft.pinned) {
            store.setPinned(id, snapshot.draft.pinned)
        }
        lastSavedSnapshot = snapshot
        return true
    }

    fun flushCurrentSnapshot(): Boolean {
        if (noteDeleted) return true
        val snapshot = NoteEditorSnapshot(draft, undoHistory, redoHistory)
        if (snapshot == lastSavedSnapshot) return true
        if (resolvedNoteId == null && snapshot.draft.title.isBlank() && snapshot.draft.content.isBlank()) {
            autoSaveStatus = ""
            return true
        }
        autoSaveStatus = autoSaveSaving
        return try {
            if (!persistSnapshot(snapshot)) {
                autoSaveStatus = autoSaveFailed
                false
            } else {
                autoSaveStatus = autoSaveSaved
                true
            }
        } catch (_: Exception) {
            autoSaveStatus = autoSaveFailed
            false
        }
    }

    LaunchedEffect(draft, undoHistory, redoHistory, resolvedNoteId, noteDeleted) {
        if (noteDeleted) return@LaunchedEffect
        val snapshot = NoteEditorSnapshot(draft, undoHistory, redoHistory)
        if (snapshot == lastSavedSnapshot) {
            autoSaveStatus = if (resolvedNoteId == null) "" else autoSaveSaved
            return@LaunchedEffect
        }
        if (resolvedNoteId == null && draft.title.isBlank() && draft.content.isBlank()) {
            autoSaveStatus = ""
            return@LaunchedEffect
        }
        autoSaveStatus = autoSavePending
        delay(AUTO_SAVE_INTERVAL_MILLIS)
        flushCurrentSnapshot()
    }

    val latestFlush by rememberUpdatedState { flushCurrentSnapshot() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                latestFlush()
            } else if (event == Lifecycle.Event.ON_RESUME && autoSaveStatus == autoSaveFailed) {
                latestFlush()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(noteId) {
        onDispose { latestFlush() }
    }
    BackHandler {
        if (flushCurrentSnapshot()) {
            onBack()
        } else {
            onMessage(autoSaveFailed)
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            scope.launch {
                val saved = imageStore.save(picked)
                if (saved == null) {
                    onMessage(imageFailedMessage)
                } else {
                    pendingSnippet = markdownImageFor(IMAGE_ALT, saved.absolutePath)
                }
            }
        }
    }

    LaunchedEffect(store, imageStore) {
        imageStore.prune(store.notes.value.joinToString("\n") { it.content })
    }

    NoteEditorScreen(
        isNew = existing == null,
        draft = draft,
        undoHistory = undoHistory,
        redoHistory = redoHistory,
        saveStatus = autoSaveStatus,
        onDraftChange = { draft = it },
        onHistoryChange = { undo, redo ->
            undoHistory = undo
            redoHistory = redo
        },
        onDelete = { deleteOpen = true },
        onBack = {
            if (flushCurrentSnapshot()) {
                onBack()
            } else {
                onMessage(autoSaveFailed)
            }
        },
        onAddImage = { imageSheetOpen = true },
        snippet = pendingSnippet,
        onSnippetConsumed = { pendingSnippet = null },
        modifier = modifier
    )

    TaskActionSheet(
        visible = imageSheetOpen,
        title = stringResource(R.string.notes_image_add),
        actions = listOf(
            TaskAction(
                label = stringResource(R.string.notes_image_gallery),
                onClick = {
                    imageLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ),
            TaskAction(
                label = stringResource(R.string.notes_image_link),
                onClick = { imageLinkOpen = true }
            )
        ),
        onDismiss = { imageSheetOpen = false }
    )

    TaskTextDialog(
        visible = imageLinkOpen,
        title = stringResource(R.string.notes_image_link),
        hint = stringResource(R.string.notes_image_link_hint),
        initialValue = "",
        confirmLabel = stringResource(R.string.notes_image_insert),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { value ->
            imageLinkOpen = false
            pendingSnippet = markdownImageFor(IMAGE_ALT, value)
        },
        onDismiss = { imageLinkOpen = false }
    )

    GlassDialog(
        visible = deleteOpen && existing != null,
        title = stringResource(R.string.notes_delete),
        body = stringResource(R.string.notes_delete_body),
        confirmLabel = stringResource(R.string.notes_delete),
        onConfirm = {
            deleteOpen = false
            noteDeleted = true
            resolvedNoteId?.let { store.removeNote(it) }
            onMessage(deletedMessage)
            onBack()
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteOpen = false }
    )
}

private fun derivedTitle(content: String): String = content
    .lineSequence()
    .map { it.trim().trimStart('#', ' ', '-', '*', '>') }
    .firstOrNull { it.isNotEmpty() }
    ?.take(MAX_DERIVED_TITLE)
    .orEmpty()

private fun draftOf(note: NoteItem?): NoteDraft = NoteDraft(
    title = note?.title.orEmpty(),
    content = note?.content.orEmpty(),
    pinned = note?.pinned ?: false
)

private fun editorSnapshotOf(note: NoteItem): NoteEditorSnapshot = NoteEditorSnapshot(
    draft = draftOf(note),
    undoHistory = note.undoHistory,
    redoHistory = note.redoHistory
)

private data class NoteEditorSnapshot(
    val draft: NoteDraft,
    val undoHistory: List<String>,
    val redoHistory: List<String>
)

data class NoteDraft(
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false
)

private const val MAX_DERIVED_TITLE = 60
private const val IMAGE_ALT = "image"
private const val AUTO_SAVE_INTERVAL_MILLIS = 1_000L
