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
    private val foldersState = MutableStateFlow(restored.folders)
    private val notesState = MutableStateFlow(restored.notes)
    private val activeState = MutableStateFlow(
        restored.activeFolderId ?: restored.folders.firstOrNull()?.id
    )
    private var revision = restored.revision
    private var persistedNotes = restored.persistedNotes
    private var deletedNoteIds = restored.deletedNoteIds.toSet()
    private var deletedFolderIds = restored.deletedFolderIds.toSet()

    val folders: StateFlow<List<NoteFolder>> = foldersState.asStateFlow()
    val notes: StateFlow<List<NoteItem>> = notesState.asStateFlow()
    val activeFolderId: StateFlow<String?> = activeState.asStateFlow()

    @Synchronized
    fun find(noteId: String): NoteItem? = notesState.value.firstOrNull { it.id == noteId }

    @Synchronized
    fun findFolder(folderId: String): NoteFolder? =
        foldersState.value.firstOrNull { it.id == folderId }

    @Synchronized
    fun createFolder(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return ""
        val id = newId(FOLDER_PREFIX) { candidate -> foldersState.value.any { it.id == candidate } }
        val position = foldersState.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        foldersState.value = foldersState.value + NoteFolder(
            id = id,
            title = trimmed,
            position = position,
            createdAtMillis = clock.nowMillis()
        )
        deletedFolderIds -= id
        if (activeState.value == null) activeState.value = id
        persist()
        return id
    }

    @Synchronized
    fun renameFolder(folderId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val current = foldersState.value
        val index = current.indexOfFirst { it.id == folderId }
        if (index < 0 || current[index].title == trimmed) return
        foldersState.value = current.toMutableList().apply {
            set(index, current[index].copy(title = trimmed))
        }
        persist()
    }

    @Synchronized
    fun removeFolder(folderId: String) {
        val remaining = foldersState.value.filterNot { it.id == folderId }
        if (remaining.size == foldersState.value.size) return
        val removedNoteIds = notesState.value
            .filter { it.folderId == folderId }
            .mapTo(HashSet()) { it.id }
        foldersState.value = remaining
        notesState.value = notesState.value.filterNot { it.folderId == folderId }
        deletedFolderIds += folderId
        deletedNoteIds += removedNoteIds
        if (activeState.value == folderId) activeState.value = remaining.firstOrNull()?.id
        persist()
    }

    @Synchronized
    fun setActiveFolder(folderId: String) {
        if (activeState.value == folderId || findFolder(folderId) == null) return
        activeState.value = folderId
        persist()
    }

    @Synchronized
    fun createNote(folderId: String, title: String, content: String = ""): String {
        if (findFolder(folderId) == null) return ""
        if (title.isBlank() && content.isBlank()) return ""
        val now = clock.nowMillis()
        val id = newId(NOTE_PREFIX) { candidate -> notesState.value.any { it.id == candidate } }
        notesState.value = notesState.value + NoteItem(
            id = id,
            folderId = folderId,
            title = title.trim(),
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
        if (!VALID_NOTE_ID.matches(note.id) || findFolder(note.folderId) == null) return
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
        replace(
            target.copy(
                pinned = pinned,
                pinnedAtMillis = if (pinned) now else null,
                updatedAtMillis = now
            )
        )
    }

    @Synchronized
    fun moveToFolder(noteId: String, folderId: String) {
        val target = find(noteId) ?: return
        if (target.folderId == folderId || findFolder(folderId) == null) return
        replace(
            target.copy(folderId = folderId, updatedAtMillis = clock.nowMillis())
        )
    }

    private fun replace(note: NoteItem) {
        val current = notesState.value
        val index = current.indexOfFirst { it.id == note.id }
        if (index < 0) return
        notesState.value = current.toMutableList().apply { set(index, note) }
        persist()
    }

    private fun newId(prefix: String, taken: (String) -> Boolean): String {
        val stamp = prefix + clock.nowMillis().toString(RADIX)
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
        }
        val folders = recoverFolders(
            metadata.folders.filterNot { it.id in metadata.deletedFolderIds },
            notes
        )
        val folderIds = folders.mapTo(HashSet()) { it.id }
        val sanitized = notes.distinctBy { it.id }
            .filter { it.folderId in folderIds && VALID_NOTE_ID.matches(it.id) }
        val active = metadata.activeFolderId?.takeIf { id -> folders.any { it.id == id } }
            ?: folders.firstOrNull()?.id
        return Restored(
            metadata = metadata.copy(folders = folders, activeFolderId = active),
            notes = sanitized,
            persistedNotes = sanitized.associateBy { it.id }
        )
    }

    private fun recoverFolders(stored: List<NoteFolder>, notes: List<NoteItem>): List<NoteFolder> {
        val recovered = stored.distinctBy { it.id }.toMutableList()
        val existing = recovered.mapTo(HashSet()) { it.id }
        notes.map { it.folderId }.distinct().sorted().forEach { folderId ->
            if (folderId !in existing) {
                recovered += NoteFolder(
                    id = folderId,
                    title = RECOVERED_FOLDER_TITLE,
                    position = recovered.size
                )
                existing += folderId
            }
        }
        return recovered
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
            folders = foldersState.value,
            noteIds = byId.keys.sorted(),
            deletedNoteIds = deletedNoteIds.sorted(),
            deletedFolderIds = deletedFolderIds.sorted(),
            activeFolderId = activeState.value
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
        val folders: List<NoteFolder> = metadata.folders
        val revision: Long = metadata.revision
        val activeFolderId: String? = metadata.activeFolderId
        val deletedNoteIds: List<String> = metadata.deletedNoteIds
        val deletedFolderIds: List<String> = metadata.deletedFolderIds
    }

    companion object {
        const val METADATA_FILE = "metadata.json"
        private const val JSON_SUFFIX = ".json"
        private const val RECOVERED_FOLDER_TITLE = "Recovered"
        private val VALID_NOTE_ID = Regex("note-[a-z0-9]+(?:-[a-z0-9]+)*")
        private const val FOLDER_PREFIX = "folder-"
        private const val NOTE_PREFIX = "note-"
        private const val SUFFIX_SEPARATOR = "-"
        private const val RADIX = 36
    }
}
