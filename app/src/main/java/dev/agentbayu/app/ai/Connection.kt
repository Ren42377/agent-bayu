package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionHealth {
    READY,
    NEEDS_KEY,
    NEEDS_ATTENTION
}

@Serializable
data class Connection(
    val id: String,
    val providerId: String,
    val label: String,
    val model: String,
    val enabled: Boolean = true,
    val priority: Int = DEFAULT_PRIORITY,
    val weight: Int = DEFAULT_WEIGHT,
    val baseUrlOverride: String? = null,
    val discoveredModels: List<String> = emptyList(),
    val health: ConnectionHealth = ConnectionHealth.READY,
    val healthDetail: String? = null,
    val keyHint: String? = null,
    val createdAtMillis: Long = 0L
) {
    companion object {
        const val DEFAULT_PRIORITY = 100
        const val DEFAULT_WEIGHT = 1
    }
}

@Serializable
data class ConnectionFile(
    val version: Int = 1,
    val connections: List<Connection> = emptyList()
)
