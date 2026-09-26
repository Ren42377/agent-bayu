package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.notes.NoteItem
import dev.agentbayu.app.domain.notes.NoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CreateNoteTool(
    private val store: () -> NoteStore
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
                name = "pinned",
                type = "boolean",
                description = "Pin the note to the top of the list",
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
        val id = notes.createNote(title, content)
        if (id.isEmpty()) return@withContext call.problem("The note was not stored")
        if (arguments.flag("pinned")) notes.setPinned(id, true)
        call.reply("Created " + id + ": " + title.ifEmpty { "(untitled)" })
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
        parameters = toolSchema()
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val notes = store()
        val all = notes.notes.value
        if (all.isEmpty()) {
            return@withContext call.reply("There are no notes yet")
        }
        val lines = all
            .sortedWith(
                compareByDescending<NoteItem> { it.pinned }
                    .thenByDescending { it.updatedAtMillis }
            )
            .map { note -> describe(note) }
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
        val note = store().find(noteId)
            ?: return@withContext call.problem("No note with id " + noteId)
        val lines = ArrayList<String>()
        lines += "Id: " + note.id
        lines += "Title: " + note.title.ifEmpty { "(untitled)" }
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
        description = "Update one note: rename its title, replace its markdown content, or " +
            "pin and unpin it. Read the ids with list_notes first.",
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
