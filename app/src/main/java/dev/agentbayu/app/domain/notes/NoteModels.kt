package dev.agentbayu.app.domain.notes

import kotlinx.serialization.Serializable

@Serializable
data class NoteGroup(
    val id: String,
    val title: String,
    val position: Int = 0,
    val createdAtMillis: Long = 0L
)

@Serializable
data class NoteItem(
    val id: String,
    val groupId: String = "",
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val pinnedAtMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val undoHistory: List<String> = emptyList(),
    val redoHistory: List<String> = emptyList()
)

const val MAX_NOTE_HISTORY_ENTRIES = 10

@Serializable
data class NoteMetadata(
    val version: Int = 1,
    val revision: Long = 0L,
    val groups: List<NoteGroup> = emptyList(),
    val activeGroupId: String? = null,
    val deletedGroupIds: List<String> = emptyList(),
    val noteIds: List<String>? = null,
    val deletedNoteIds: List<String> = emptyList()
)
