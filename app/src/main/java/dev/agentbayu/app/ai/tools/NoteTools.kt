package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateNoteTool(
    private val store: () -> NoteStore,
    private val defaultFolderTitle: String
) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Create a markdown note in the owner notes. It shows up in the Notes " +
            "tab right away. The content is markdown text.",
        parameters = toolSchema(
            ToolField("title", "string", "Title of the note"),
            ToolField(
                name = "content",
                type = "string",
                description = "Markdown body of the note",
                required = false
            ),
            ToolField(
                name = "folder",
                type = "string",
                description = "Name of the folder to put the note in, created when missing",
                required = false
            ),
            ToolField(
                name = "pinned",
                type = "boolean",
                description = "Pin the note to the top of its folder",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val title = arguments.text("title").orEmpty()
        val content = arguments.raw("content").orEmpty()
        if (title.isBlank() && content.isBlank()) {
            return@withContext call.problem("A title or content is required")
        }
        val notes = store()
        val folderId = folderIdFor(notes, arguments.text("folder"), defaultFolderTitle)
        if (folderId.isEmpty()) {
            return@withContext call.problem("The note folder could not be created")
        }
        val id = notes.createNote(folderId, title, content)
        if (id.isEmpty()) return@withContext call.problem("The note was not stored")
        if (arguments.flag("pinned")) notes.setPinned(id, true)
        val folderName = notes.findFolder(folderId)?.title.orEmpty()
        call.reply("Created " + id + ": " + title.ifEmpty { "(untitled)" } + " in " + folderName)
    }

    private companion object {
        const val NAME = "create_note"
    }
}

class ListNotesTool(private val store: () -> NoteStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "List the owner markdown notes with their ids, so a note can be read " +
            "or updated later.",
        parameters = toolSchema(
            ToolField(
                name = "folder",
                type = "string",
                description = "Name of one folder to read, otherwise every folder is read",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val notes = store()
        val wanted = arguments.text("folder")
        val folders = if (wanted == null) {
            notes.folders.value
        } else {
            notes.folders.value.filter { it.title.equals(wanted, true) }
        }
        if (folders.isEmpty()) {
            return@withContext call.reply(
                if (wanted == null) "There are no notes yet" else "No folder named " + wanted
            )
        }
        val all = notes.notes.value
        val lines = ArrayList<String>()
        folders.sortedBy { it.position }.forEach { folder ->
            lines += folder.title
            val rows = all
                .filter { it.folderId == folder.id }
                .sortedWith(
                    compareByDescending<NoteItem> { it.pinned }
                        .thenByDescending { it.updatedAtMillis }
                )
            if (rows.isEmpty()) {
                lines += INDENT + "no notes"
            } else {
                rows.forEach { note -> lines += INDENT + describe(note) }
            }
        }
        call.reply(lines.joinToString("\n"))
    }

    private fun describe(note: NoteItem): String {
        val marks = ArrayList<String>()
        if (note.pinned) marks += "pinned"
        if (note.content.isNotBlank()) marks += note.content.trim().length.toString() + " chars"
        val suffix = if (marks.isEmpty()) "" else "  [" + marks.joinToString(", ") + "]"
        return note.id + "  " + note.title.ifEmpty { "(untitled)" } + suffix
    }

    private companion object {
        const val NAME = "list_notes"
        const val INDENT = "  "
    }
}

class ReadNoteTool(private val store: () -> NoteStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Read one note in full, including its markdown content. Read the ids " +
            "with list_notes first.",
        parameters = toolSchema(
            ToolField("note_id", "string", "Id of the note, as reported by list_notes")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val noteId = ToolArguments(call.arguments).text("note_id")
            ?: return@withContext call.problem("A note_id is required")
        val notes = store()
        val note = notes.find(noteId)
            ?: return@withContext call.problem("No note with id " + noteId)
        val lines = ArrayList<String>()
        lines += "Id: " + note.id
        lines += "Title: " + note.title.ifEmpty { "(untitled)" }
        lines += "Folder: " + (notes.findFolder(note.folderId)?.title ?: note.folderId)
        lines += "Pinned: " + if (note.pinned) "yes" else "no"
        lines += "Content:"
        lines += note.content.ifBlank { "(empty)" }
        call.reply(lines.joinToString("\n"))
    }

    private companion object {
        const val NAME = "read_note"
    }
}

class UpdateNoteTool(private val store: () -> NoteStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Update one note: rename its title, replace its markdown content, pin " +
            "or unpin it, or move it to another folder. Read the ids with list_notes first.",
        parameters = toolSchema(
            ToolField("note_id", "string", "Id of the note, as reported by list_notes"),
            ToolField(
                name = "title",
                type = "string",
                description = "New title for the note",
                required = false
            ),
            ToolField(
                name = "content",
                type = "string",
                description = "New markdown body, replaces the current one",
                required = false
            ),
            ToolField(
                name = "folder",
                type = "string",
                description = "Name of the folder to move the note to",
                required = false
            ),
            ToolField(
                name = "pinned",
                type = "boolean",
                description = "Pin or unpin the note",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val noteId = arguments.text("note_id")
            ?: return@withContext call.problem("A note_id is required")
        val notes = store()
        val note = notes.find(noteId)
            ?: return@withContext call.problem("No note with id " + noteId)
        var updated = note
        arguments.raw("title")?.let { title -> updated = updated.copy(title = title.trim()) }
        arguments.raw("content")?.let { content -> updated = updated.copy(content = content) }
        if (updated != note) notes.upsertNote(updated)
        arguments.text("folder")?.let { wanted ->
            val folderId = folderIdFor(notes, wanted)
            if (folderId.isEmpty()) {
                return@withContext call.problem("Cannot move the note to folder " + wanted)
            }
            notes.moveToFolder(noteId, folderId)
        }
        if (arguments.contains("pinned")) {
            notes.setPinned(noteId, arguments.flag("pinned"))
        }
        call.reply("Updated " + note.title.ifEmpty { "(untitled)" })
    }

    private companion object {
        const val NAME = "update_note"
    }
}

class DeleteNoteTool(private val store: () -> NoteStore) : ToolHandler {

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Delete one note. Read the ids with list_notes first.",
        parameters = toolSchema(
            ToolField("note_id", "string", "Id of the note, as reported by list_notes")
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val noteId = ToolArguments(call.arguments).text("note_id")
            ?: return@withContext call.problem("A note_id is required")
        val notes = store()
        val target = notes.find(noteId)
            ?: return@withContext call.problem("No note with id " + noteId)
        notes.removeNote(noteId)
        call.reply("Deleted " + target.title.ifEmpty { "(untitled)" })
    }

    private companion object {
        const val NAME = "delete_note"
    }
}

internal fun folderIdFor(
    notes: NoteStore,
    wanted: String?,
    fallbackTitle: String? = null
): String {
    if (wanted != null) {
        val match = notes.folders.value.firstOrNull { it.title.equals(wanted, true) }
        if (match != null) return match.id
        return notes.createFolder(wanted)
    }
    val active = notes.activeFolderId.value
    if (active != null && notes.findFolder(active) != null) return active
    val first = notes.folders.value.firstOrNull()
    if (first != null) return first.id
    return fallbackTitle?.let(notes::createFolder).orEmpty()
}
