package dev.agentbayu.app.ui.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import dev.agentbayu.app.platform.NoteImageStore
import dev.agentbayu.app.ui.components.GlassDialog
import dev.agentbayu.app.ui.components.markdownImageFor
import dev.agentbayu.app.ui.tasks.TaskAction
import dev.agentbayu.app.ui.tasks.TaskActionSheet
import dev.agentbayu.app.ui.tasks.TaskTextDialog
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
    val emptyMessage = stringResource(R.string.notes_editor_empty)
    val savedMessage = stringResource(R.string.notes_saved)
    val deletedMessage = stringResource(R.string.notes_deleted)
    val imageFailedMessage = stringResource(R.string.notes_image_failed)
    val scope = rememberCoroutineScope()
    val imageStore = remember(context) { NoteImageStore(context) }

    val existing = noteId?.let { id -> notes.firstOrNull { it.id == id } }
    var draft by remember(noteId) {
        mutableStateOf(draftOf(existing))
    }
    var deleteOpen by remember { mutableStateOf(false) }
    var imageSheetOpen by remember { mutableStateOf(false) }
    var imageLinkOpen by remember { mutableStateOf(false) }
    var pendingSnippet by remember { mutableStateOf<String?>(null) }

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
        onDraftChange = { draft = it },
        onSave = {
            val content = draft.content.trim()
            val title = draft.title.trim().ifEmpty { derivedTitle(content) }
            if (title.isEmpty() && content.isEmpty()) {
                onMessage(emptyMessage)
            } else {
                save(store, existing, draft.copy(title = title))
                onMessage(savedMessage)
                onBack()
            }
        },
        onDelete = { deleteOpen = true },
        onBack = onBack,
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

private fun draftOf(note: NoteItem?): NoteDraft = NoteDraft(
    title = note?.title.orEmpty(),
    content = note?.content.orEmpty(),
    pinned = note?.pinned ?: false
)

data class NoteDraft(
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false
)

private const val MAX_DERIVED_TITLE = 60
private const val IMAGE_ALT = "image"
