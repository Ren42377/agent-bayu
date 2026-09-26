package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.ui.components.GlassDialog

@Composable
fun NotesRoute(
    onMessage: (String) -> Unit,
    onOpenNote: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember(context) { AppGraph.notes(context) }
    val folders by store.folders.collectAsState()
    val notes by store.notes.collectAsState()
    val activeId by store.activeFolderId.collectAsState()
    val defaultFolderTitle = stringResource(R.string.notes_folder_default)
    val deletedMessage = stringResource(R.string.notes_deleted)

    var query by rememberSaveable { mutableStateOf("") }
    var folderMenuOpen by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    var renameFolderOpen by remember { mutableStateOf(false) }
    var deleteFolderOpen by remember { mutableStateOf(false) }
    var rowMenuNote by remember { mutableStateOf<NoteItem?>(null) }
    var moveTargetNote by remember { mutableStateOf<NoteItem?>(null) }

    LaunchedEffect(folders.isEmpty()) {
        if (folders.isEmpty()) {
            store.setActiveFolder(store.createFolder(defaultFolderTitle))
        }
    }

    val activeFolder = folders.firstOrNull { it.id == activeId } ?: folders.firstOrNull()
    val visibleNotes = remember(notes, activeFolder?.id, query) {
        val folderId = activeFolder?.id ?: return@remember emptyList()
        val trimmed = query.trim()
        notes
            .filter { note ->
                note.folderId == folderId &&
                    (
                        trimmed.isEmpty() ||
                            note.title.contains(trimmed, ignoreCase = true) ||
                            note.content.contains(trimmed, ignoreCase = true)
                        )
            }
            .sortedWith(
                compareByDescending<NoteItem> { it.pinned }
                    .thenByDescending { it.updatedAtMillis }
            )
    }

    NotesScreen(
        folders = folders,
        activeFolder = activeFolder,
        notes = visibleNotes,
        query = query,
        onQueryChange = { query = it },
        onSelectFolder = { store.setActiveFolder(it) },
        onNewFolder = { newFolderOpen = true },
        onFolderMenu = { folderMenuOpen = true },
        onAddNote = {
            val folderId = activeFolder?.id
                ?: store.createFolder(defaultFolderTitle).also(store::setActiveFolder)
            onOpenNote(null, folderId)
        },
        onOpenNote = { note -> onOpenNote(note.id, note.folderId) },
        onNoteMenu = { note -> rowMenuNote = note },
        modifier = modifier
    )

    NotesMenus(
        store = store,
        folders = folders,
        activeFolder = activeFolder,
        folderMenuOpen = folderMenuOpen,
        onFolderMenuDismiss = { folderMenuOpen = false },
        onNewFolder = { newFolderOpen = true },
        onRenameFolder = { renameFolderOpen = true },
        onDeleteFolder = { deleteFolderOpen = true },
        newFolderOpen = newFolderOpen,
        onNewFolderDismiss = { newFolderOpen = false },
        renameFolderOpen = renameFolderOpen,
        onRenameFolderDismiss = { renameFolderOpen = false },
        rowMenuNote = rowMenuNote,
        onRowMenuDismiss = { rowMenuNote = null },
        moveTargetNote = moveTargetNote,
        onMoveTargetDismiss = { moveTargetNote = null },
        onMoveToFolder = { note -> moveTargetNote = note },
        onDeleted = { onMessage(deletedMessage) }
    )

    GlassDialog(
        visible = deleteFolderOpen && activeFolder != null,
        title = stringResource(R.string.notes_folder_delete),
        body = stringResource(R.string.notes_folder_delete_body),
        confirmLabel = stringResource(R.string.notes_folder_delete),
        onConfirm = {
            deleteFolderOpen = false
            activeFolder?.let { store.removeFolder(it.id) }
        },
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onDismiss = { deleteFolderOpen = false }
    )
}
