package dev.agentbayu.app.domain.notes

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.platform.TaskStorage
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class NoteStore(
    private val storage: TaskStorage,
    private val clock: Clock = RealClock
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val restored = load()
    private val notesState = MutableStateFlow(restored.notes)
    private var revision = restored.revision
    private var persistedNotes = restored.persistedNotes
    private var deletedNoteIds = restored.deletedNoteIds.toSet()

    val notes: StateFlow<List<NoteItem>> = notesState.asStateFlow()

    @Synchronized
    fun find(noteId: String): NoteItem? = notesState.value.firstOrNull { it.id == noteId }

    @Synchronized
    fun createNote(title: String, content: String = ""): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() && content.isBlank()) return ""
        val now = clock.nowMillis()
        val id = newId { candidate -> notesState.value.any { it.id == candidate } }
        notesState.value = notesState.value + NoteItem(
            id = id,
            title = trimmed,
            content = content,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        deletedNoteIds -= id
        persist()
        return id
    }

    @Synchronized
    fun upsertNote(note: NoteItem) {
        if (!VALID_NOTE_ID.matches(note.id)) return
        val now = clock.nowMillis()
        val current = notesState.value
        val index = current.indexOfFirst { it.id == note.id }
        notesState.value = if (index >= 0) {
            current.toMutableList().apply {
                set(index, note.copy(updatedAtMillis = now))
            }
        } else {
            current + note.copy(createdAtMillis = now, updatedAtMillis = now)
        }
        deletedNoteIds -= note.id
        persist()
    }

    @Synchronized
    fun removeNote(noteId: String) {
        if (find(noteId) == null) return
        notesState.value = notesState.value.filterNot { it.id == noteId }
        deletedNoteIds += noteId
        persist()
    }

    @Synchronized
    fun setPinned(noteId: String, pinned: Boolean) {
        val target = find(noteId) ?: return
        if (target.pinned == pinned) return
        val now = clock.nowMillis()
        val current = notesState.value
        val index = current.indexOfFirst { it.id == noteId }
        notesState.value = current.toMutableList().apply {
            set(
                index,
                target.copy(
                    pinned = pinned,
                    pinnedAtMillis = if (pinned) now else null,
                    updatedAtMillis = now
                )
            )
        }
        persist()
    }

    private fun newId(taken: (String) -> Boolean): String {
        val stamp = NOTE_PREFIX + clock.nowMillis().toString(RADIX)
        if (!taken(stamp)) return stamp
        var suffix = 1
        while (taken(stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX))) suffix += 1
        return stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX)
    }

    private fun load(): Restored {
        val metadataRaw = storage.read(METADATA_FILE)
        val noteNames = storage.names().filter(::isNoteFileName)
        if (metadataRaw == null && noteNames.isEmpty()) {
            return Restored(NoteMetadata(), emptyList(), emptyMap())
        }
        val metadata = metadataRaw?.let(::decodeMetadata) ?: NoteMetadata()
        val allowedNoteIds = metadataRaw?.let { metadata.noteIds?.toSet() }
        val notes = noteNames.mapNotNull { name ->
            val note = storage.read(name)?.let(::decodeNote) ?: return@mapNotNull null
            if (
                !VALID_NOTE_ID.matches(note.id) ||
                noteFileName(note.id) != name ||
                (allowedNoteIds != null && note.id !in allowedNoteIds) ||
                note.id in metadata.deletedNoteIds
            ) {
                null
            } else {
                note
            }
        }.distinctBy { it.id }
        return Restored(
            metadata = metadata,
            notes = notes,
            persistedNotes = notes.associateBy { it.id }
        )
    }

    private fun decodeMetadata(raw: String): NoteMetadata? = try {
        json.decodeFromString(NoteMetadata.serializer(), raw)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun decodeNote(raw: String): NoteItem? = try {
        json.decodeFromString(NoteItem.serializer(), raw)
    } catch (error: IllegalArgumentException) {
        null
    }

    private fun persist() = synchronized(this) {
        val notes = notesState.value
        val byId = notes.associateBy { it.id }
        val nextRevision = revision + 1L
        val metadata = NoteMetadata(
            revision = nextRevision,
            noteIds = byId.keys.sorted(),
            deletedNoteIds = deletedNoteIds.sorted()
        )
        try {
            val writes = LinkedHashMap<String, String>()
            byId.forEach { (id, note) ->
                if (persistedNotes[id] != note) {
                    writes[noteFileName(id)] = json.encodeToString(NoteItem.serializer(), note)
                }
            }
            writes[METADATA_FILE] = json.encodeToString(NoteMetadata.serializer(), metadata)
            val deletes = persistedNotes.keys.filterNot(byId::containsKey)
                .mapTo(LinkedHashSet()) { id -> noteFileName(id) }
            storage.commit(writes, deletes)
        } catch (error: IOException) {
            persistedNotes = readPersistedNotes()
            throw error
        }
        revision = nextRevision
        persistedNotes = byId
    }

    private fun readPersistedNotes(): Map<String, NoteItem> = storage.names()
        .filter(::isNoteFileName)
        .mapNotNull { name ->
            val note = storage.read(name)?.let(::decodeNote) ?: return@mapNotNull null
            if (!VALID_NOTE_ID.matches(note.id) || noteFileName(note.id) != name) {
                null
            } else {
                note.id to note
            }
        }
        .toMap()

    private fun noteFileName(id: String): String {
        require(VALID_NOTE_ID.matches(id))
        return id + JSON_SUFFIX
    }

    private fun isNoteFileName(name: String): Boolean {
        if (!name.endsWith(JSON_SUFFIX) || name == METADATA_FILE) return false
        return VALID_NOTE_ID.matches(name.removeSuffix(JSON_SUFFIX))
    }

    private data class Restored(
        val metadata: NoteMetadata,
        val notes: List<NoteItem>,
        val persistedNotes: Map<String, NoteItem>
    ) {
        val revision: Long = metadata.revision
        val deletedNoteIds: List<String> = metadata.deletedNoteIds
    }

    companion object {
        const val METADATA_FILE = "metadata.json"
        private const val JSON_SUFFIX = ".json"
        private val VALID_NOTE_ID = Regex("note-[a-z0-9]+(?:-[a-z0-9]+)*")
        private const val NOTE_PREFIX = "note-"
        private const val SUFFIX_SEPARATOR = "-"
        private const val RADIX = 36
    }
}
