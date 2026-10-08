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
    private val groupsState = MutableStateFlow(restored.groups)
    private val activeGroupState = MutableStateFlow(restored.activeGroupId)
    private var revision = restored.revision
    private var persistedNotes = restored.persistedNotes
    private var deletedNoteIds = restored.deletedNoteIds.toSet()
    private var deletedGroupIds = restored.deletedGroupIds.toSet()

    val notes: StateFlow<List<NoteItem>> = notesState.asStateFlow()
    val groups: StateFlow<List<NoteGroup>> = groupsState.asStateFlow()
    val activeGroupId: StateFlow<String?> = activeGroupState.asStateFlow()

    @Synchronized
    fun find(noteId: String): NoteItem? = notesState.value.firstOrNull { it.id == noteId }

    @Synchronized
    fun findGroup(groupId: String): NoteGroup? =
        groupsState.value.firstOrNull { it.id == groupId }

    @Synchronized
    fun createGroup(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return ""
        val now = clock.nowMillis()
        val id = newGroupId { candidate -> groupsState.value.any { it.id == candidate } }
        val position = groupsState.value.maxOfOrNull { it.position }?.plus(1) ?: 0
        groupsState.value = groupsState.value + NoteGroup(
            id = id,
            title = trimmed,
            position = position,
            createdAtMillis = now
        )
        deletedGroupIds -= id
        if (activeGroupState.value == null) {
            activeGroupState.value = id
        }
        persist()
        return id
    }

    @Synchronized
    fun renameGroup(groupId: String, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val current = groupsState.value
        val index = current.indexOfFirst { it.id == groupId }
        if (index < 0) return
        groupsState.value = current.toMutableList().apply {
            set(index, get(index).copy(title = trimmed))
        }
        persist()
    }

    @Synchronized
    fun removeGroup(groupId: String) {
        val current = groupsState.value
        if (current.none { it.id == groupId }) return
        val removedNoteIds = notesState.value
            .filter { it.groupId == groupId }
            .map { it.id }
        notesState.value = notesState.value.filterNot { it.groupId == groupId }
        deletedNoteIds += removedNoteIds
        groupsState.value = current.filterNot { it.id == groupId }
        deletedGroupIds += groupId
        if (activeGroupState.value == groupId) {
            activeGroupState.value = groupsState.value.firstOrNull()?.id
        }
        persist()
    }

    @Synchronized
    fun setActiveGroup(groupId: String) {
        activeGroupState.value = groupId
        persist()
    }

    @Synchronized
    fun moveToGroup(noteId: String, groupId: String) {
        val target = find(noteId) ?: return
        if (target.groupId == groupId) return
        val now = clock.nowMillis()
        val current = notesState.value
        val index = current.indexOfFirst { it.id == noteId }
        notesState.value = current.toMutableList().apply {
            set(index, target.copy(groupId = groupId, updatedAtMillis = now))
        }
        persist()
    }

    @Synchronized
    fun createNote(
        title: String,
        content: String = "",
        groupId: String? = null,
        pinned: Boolean = false,
        undoHistory: List<String> = emptyList(),
        redoHistory: List<String> = emptyList()
    ): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() && content.isBlank()) return ""
        val now = clock.nowMillis()
        val id = newNoteId { candidate -> notesState.value.any { it.id == candidate } }
        val resolvedGroupId = groupId ?: activeGroupState.value.orEmpty()
        notesState.value = notesState.value + NoteItem(
            id = id,
            groupId = resolvedGroupId,
            title = trimmed,
            content = content,
            pinned = pinned,
            pinnedAtMillis = if (pinned) now else null,
            createdAtMillis = now,
            updatedAtMillis = now,
            undoHistory = undoHistory.takeLast(MAX_NOTE_HISTORY_ENTRIES),
            redoHistory = redoHistory.takeLast(MAX_NOTE_HISTORY_ENTRIES)
        )
        deletedNoteIds -= id
        persist()
        return id
    }

    @Synchronized
    fun upsertNote(note: NoteItem) {
        if (!VALID_NOTE_ID.matches(note.id)) return
        val boundedNote = note.copy(
            undoHistory = note.undoHistory.takeLast(MAX_NOTE_HISTORY_ENTRIES),
            redoHistory = note.redoHistory.takeLast(MAX_NOTE_HISTORY_ENTRIES)
        )
        val now = clock.nowMillis()
        val current = notesState.value
        val index = current.indexOfFirst { it.id == boundedNote.id }
        notesState.value = if (index >= 0) {
            current.toMutableList().apply {
                set(index, boundedNote.copy(updatedAtMillis = now))
            }
        } else {
            current + boundedNote.copy(createdAtMillis = now, updatedAtMillis = now)
        }
        deletedNoteIds -= boundedNote.id
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

    private fun newNoteId(taken: (String) -> Boolean): String {
        val stamp = NOTE_PREFIX + clock.nowMillis().toString(RADIX)
        if (!taken(stamp)) return stamp
        var suffix = 1
        while (taken(stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX))) suffix += 1
        return stamp + SUFFIX_SEPARATOR + suffix.toString(RADIX)
    }

    private fun newGroupId(taken: (String) -> Boolean): String {
        val stamp = GROUP_PREFIX + clock.nowMillis().toString(RADIX)
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
        val groups = recoverGroups(metadata.groups, notes)
        return Restored(
            metadata = metadata,
            notes = notes,
            persistedNotes = notes.associateBy { it.id },
            groups = groups
        )
    }

    private fun recoverGroups(
        stored: List<NoteGroup>,
        notes: List<NoteItem>
    ): List<NoteGroup> {
        val knownIds = stored.map { it.id }.toSet()
        val orphanGroupIds = notes
            .map { it.groupId }
            .filter { it.isNotEmpty() && it !in knownIds }
            .distinct()
        if (orphanGroupIds.isEmpty()) return stored
        val recovered = orphanGroupIds.mapIndexed { index, id ->
            NoteGroup(
                id = id,
                title = RECOVERED_GROUP_TITLE,
                position = stored.size + index
            )
        }
        return stored + recovered
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
            groups = groupsState.value,
            activeGroupId = activeGroupState.value,
            deletedGroupIds = deletedGroupIds.sorted(),
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
        val persistedNotes: Map<String, NoteItem>,
        val groups: List<NoteGroup> = metadata.groups
    ) {
        val revision: Long = metadata.revision
        val deletedNoteIds: List<String> = metadata.deletedNoteIds
        val deletedGroupIds: List<String> = metadata.deletedGroupIds
        val activeGroupId: String? = metadata.activeGroupId
    }

    companion object {
        const val METADATA_FILE = "metadata.json"
        private const val JSON_SUFFIX = ".json"
        private val VALID_NOTE_ID = Regex("note-[a-z0-9]+(?:-[a-z0-9]+)*")
        private const val NOTE_PREFIX = "note-"
        private const val GROUP_PREFIX = "group-"
        private const val SUFFIX_SEPARATOR = "-"
        private const val RADIX = 36
        private const val RECOVERED_GROUP_TITLE = "Recovered"
    }
}
