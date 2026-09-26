package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import dev.agentbayu.app.ui.components.GlassDialog

@Composable
fun NoteEditorRoute(
    noteId: String?,
    folderId: String,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.notes(context) }
    val folders by store.folders.collectAsState()
    val notes by store.notes.collectAsState()
    val emptyMessage = stringResource(R.string.notes_editor_empty)
    val savedMessage = stringResource(R.string.notes_saved)
    val deletedMessage = stringResource(R.string.notes_deleted)

    val existing = noteId?.let { id -> notes.firstOrNull { it.id == id } }
    var draft by remember(noteId) {
        mutableStateOf(draftOf(existing, folderId))
    }
    var deleteOpen by remember { mutableStateOf(false) }

    NoteEditorScreen(
        isNew = existing == null,
        draft = draft,
        folders = folders,
        onDraftChange = { draft = it },
        onSave = {
            val content = draft.content.trim()
            val title = draft.title.trim().ifEmpty { derivedTitle(content) }
            if (title.isEmpty() && content.trim().isEmpty()) {
                onMessage(emptyMessage)
            } else {
                save(store, existing, draft.copy(title = title))
                onMessage(savedMessage)
                onBack()
            }
        },
        onDelete = { deleteOpen = true },
        onBack = onBack,
        modifier = modifier
    )

    GlassDialog(
        visible = deleteOpen && existing != null,
        title = stringResource(R.string.notes_delete),
        body = stringResource(R.string.notes_delete_body),
        confirmLabel = stringResource(R.string.notes_delete),
        onConfirm = {
            deleteOpen = false
            existing?.let { store.removeNote(it.id) }
            onMessage(deletedMessage)
            onBack()
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteOpen = false }
    )
}

private fun save(store: NoteStore, existing: NoteItem?, draft: NoteDraft) {
    val id = existing?.id ?: store.createNote(
        folderId = draft.folderId,
        title = draft.title,
        content = draft.content.trim()
    )
    if (id.isEmpty()) return
    val base = store.find(id) ?: return
    store.upsertNote(
        base.copy(
            title = draft.title,
            content = draft.content.trim()
        )
    )
    if (base.folderId != draft.folderId) {
        store.moveToFolder(id, draft.folderId)
    }
    if (base.pinned != draft.pinned) {
        store.setPinned(id, draft.pinned)
    }
}

private fun derivedTitle(content: String): String = content
    .lineSequence()
    .map { it.trim().trimStart('#', ' ', '-', '*', '>') }
    .firstOrNull { it.isNotEmpty() }
    ?.take(MAX_DERIVED_TITLE)
    .orEmpty()

private fun draftOf(note: NoteItem?, folderId: String): NoteDraft = NoteDraft(
    title = note?.title.orEmpty(),
    content = note?.content.orEmpty(),
    folderId = note?.folderId ?: folderId,
    pinned = note?.pinned ?: false
)

data class NoteDraft(
    val title: String = "",
    val content: String = "",
    val folderId: String = "",
    val pinned: Boolean = false
)

private const val MAX_DERIVED_TITLE = 60
