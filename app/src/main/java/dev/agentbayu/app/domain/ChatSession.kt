package dev.agentbayu.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class ChatSessionMeta(
    val id: String,
    val title: String = "",
    val preview: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Serializable
data class SessionIndexFile(
    val version: Int = 1,
    val activeSessionId: String? = null,
    val sessions: List<ChatSessionMeta> = emptyList()
)
