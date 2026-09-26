package dev.agentbayu.app.domain.notes

import kotlinx.serialization.Serializable

@Serializable
data class NoteItem(
    val id: String,
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    val pinnedAtMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Serializable
data class NoteMetadata(
    val version: Int = 1,
    val revision: Long = 0L,
    val noteIds: List<String>? = null,
    val deletedNoteIds: List<String> = emptyList()
)
