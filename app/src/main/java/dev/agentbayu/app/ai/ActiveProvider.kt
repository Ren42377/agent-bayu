package dev.agentbayu.app.ai

import kotlinx.coroutines.flow.StateFlow

interface ConnectionSource {
    val connections: StateFlow<List<Connection>>
    val activeConnectionId: StateFlow<String?>
    fun markHealth(connectionId: String, health: ConnectionHealth, detail: String? = null)
}

enum class ActiveProviderProblem {
    NO_CONNECTION,
    UNKNOWN_PROVIDER,
    MISSING_CREDENTIAL
}

sealed interface ActiveResolution {
    data class Ready(val candidate: Candidate) : ActiveResolution

    data class Unavailable(val problem: ActiveProviderProblem) : ActiveResolution
}

class ActiveProvider(
    private val connections: ConnectionSource,
    private val catalog: ProviderCatalog,
    private val keys: KeySource
) {

    fun resolve(): ActiveResolution {
        val connection = activeConnection() ?: return ActiveResolution.Unavailable(
            ActiveProviderProblem.NO_CONNECTION
        )
        val provider = catalog.find(connection.providerId) ?: return ActiveResolution.Unavailable(
            ActiveProviderProblem.UNKNOWN_PROVIDER
        )
        if (provider.requiresCredential && keys.secretFor(connection, provider) == null) {
            return ActiveResolution.Unavailable(ActiveProviderProblem.MISSING_CREDENTIAL)
        }
        return ActiveResolution.Ready(
            Candidate(connection, provider, provider.modelOrFallback(connection.model))
                .withEffortModel()
        )
    }

    fun activeConnection(): Connection? = resolveActiveConnection(
        connections.connections.value,
        connections.activeConnectionId.value
    )
}

fun resolveActiveConnection(connections: List<Connection>, activeConnectionId: String?): Connection? {
    if (connections.isEmpty()) return null
    val selected = connections.firstOrNull { it.id == activeConnectionId }
    if (selected != null) return selected
    return connections.minWithOrNull(compareBy({ it.createdAtMillis }, { it.id }))
}
