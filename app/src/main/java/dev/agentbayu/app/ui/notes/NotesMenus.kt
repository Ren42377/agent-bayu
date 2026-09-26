package dev.agentbayu.app.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteFolder
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import dev.agentbayu.app.ui.tasks.TaskAction
import dev.agentbayu.app.ui.tasks.TaskActionSheet
import dev.agentbayu.app.ui.tasks.TaskTextDialog

@Composable
internal fun NotesMenus(
    store: NoteStore,
    folders: List<NoteFolder>,
    activeFolder: NoteFolder?,
    folderMenuOpen: Boolean,
    onFolderMenuDismiss: () -> Unit,
    onNewFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    newFolderOpen: Boolean,
    onNewFolderDismiss: () -> Unit,
    renameFolderOpen: Boolean,
    onRenameFolderDismiss: () -> Unit,
    rowMenuNote: NoteItem?,
    onRowMenuDismiss: () -> Unit,
    moveTargetNote: NoteItem?,
    onMoveTargetDismiss: () -> Unit,
    onMoveToFolder: (NoteItem) -> Unit,
    onDeleted: () -> Unit
) {
    TaskActionSheet(
        visible = folderMenuOpen,
        title = activeFolder?.title ?: stringResource(R.string.notes_title),
        actions = buildList {
            add(
                TaskAction(
                    label = stringResource(R.string.notes_folder_new),
                    onClick = onNewFolder
                )
            )
            if (activeFolder != null) {
                add(
                    TaskAction(
                        label = stringResource(R.string.notes_folder_rename),
                        onClick = onRenameFolder
                    )
                )
                add(
                    TaskAction(
                        label = stringResource(R.string.notes_folder_delete),
                        destructive = true,
                        onClick = onDeleteFolder
                    )
                )
            }
        },
        onDismiss = onFolderMenuDismiss
    )

    TaskTextDialog(
        visible = newFolderOpen,
        title = stringResource(R.string.notes_folder_new),
        hint = stringResource(R.string.notes_folder_name_hint),
        initialValue = "",
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onNewFolderDismiss()
            store.setActiveFolder(store.createFolder(title))
        },
        onDismiss = onNewFolderDismiss
    )

    TaskTextDialog(
        visible = renameFolderOpen && activeFolder != null,
        title = stringResource(R.string.notes_folder_rename),
        hint = stringResource(R.string.notes_folder_name_hint),
        initialValue = activeFolder?.title.orEmpty(),
        confirmLabel = stringResource(R.string.tasks_detail_save),
        dismissLabel = stringResource(R.string.tasks_detail_cancel),
        onConfirm = { title ->
            onRenameFolderDismiss()
            activeFolder?.let { store.renameFolder(it.id, title) }
        },
        onDismiss = onRenameFolderDismiss
    )

    TaskActionSheet(
        visible = rowMenuNote != null,
        title = rowMenuNote?.title.orEmpty(),
        actions = rowMenuActions(
            note = rowMenuNote,
            folderCount = folders.size,
            onTogglePin = {
                rowMenuNote?.let { store.setPinned(it.id, !it.pinned) }
            },
            onMove = { rowMenuNote?.let(onMoveToFolder) },
            onDelete = {
                rowMenuNote?.let { store.removeNote(it.id) }
                onDeleted()
            }
        ),
        onDismiss = onRowMenuDismiss
    )

    TaskActionSheet(
        visible = moveTargetNote != null,
        title = stringResource(R.string.notes_move_to_folder),
        actions = folders
            .filter { it.id != moveTargetNote?.folderId }
            .map { target ->
                TaskAction(label = target.title) {
                    moveTargetNote?.let { store.moveToFolder(it.id, target.id) }
                }
            },
        onDismiss = onMoveTargetDismiss
    )
}

@Composable
private fun rowMenuActions(
    note: NoteItem?,
    folderCount: Int,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
): List<TaskAction> {
    if (note == null) return emptyList()
    val actions = mutableListOf(
        TaskAction(
            label = stringResource(
                if (note.pinned) R.string.notes_unpin else R.string.notes_pin
            ),
            onClick = onTogglePin
        )
    )
    if (folderCount > 1) {
        actions += TaskAction(
            label = stringResource(R.string.notes_move_to_folder),
            onClick = onMove
        )
    }
    actions += TaskAction(
        label = stringResource(R.string.notes_delete),
        destructive = true,
        onClick = onDelete
    )
    return actions
}
