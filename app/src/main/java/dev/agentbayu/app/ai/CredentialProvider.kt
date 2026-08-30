package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import dev.agentbayu.app.ai.oauth.TokenRefreshResult
import dev.agentbayu.app.ai.oauth.TokenRefresher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WireCredential(
    val token: String? = null,
    val headers: Map<String, String> = emptyMap()
) {
    companion object {
        val EMPTY = WireCredential()
    }
}

interface CredentialProvider {
    suspend fun resolve(candidate: Candidate): WireCredential
}

class KeySourceCredentials(private val keys: KeySource) : CredentialProvider {

    override suspend fun resolve(candidate: Candidate): WireCredential =
        WireCredential(token = keys.secretFor(candidate))
}

class StoredCredentials(
    private val store: CredentialStore,
    private val refresher: TokenRefresher,
    private val connections: ConnectionSource,
    private val clock: Clock = RealClock
) : CredentialProvider {

    private val locks = HashMap<String, Mutex>()

    override suspend fun resolve(candidate: Candidate): WireCredential {
        val connectionId = candidate.connection.id
        val stored = store.credential(connectionId)
        val config = candidate.provider.oauth
        if (stored is Credential.OAuthTokens && config != null) {
            val tokens = ensureFresh(connectionId, config, stored)
            return WireCredential(
                token = tokens.accessToken.takeIf { it.isNotBlank() },
                headers = config.headersFor(tokens.extras)
            )
        }
        return WireCredential(token = store.secretFor(candidate))
    }

    private suspend fun ensureFresh(
        connectionId: String,
        config: OAuthConfig,
        tokens: Credential.OAuthTokens
    ): Credential.OAuthTokens {
        if (!tokens.isExpired(clock.nowMillis())) return tokens
        return lockFor(connectionId).withLock {
            val current = store.credential(connectionId) as? Credential.OAuthTokens ?: tokens
            if (!current.isExpired(clock.nowMillis())) {
                current
            } else {
                when (val result = refresher.refresh(config, current)) {
                    is TokenRefreshResult.Success -> {
                        store.put(connectionId, result.tokens)
                        result.tokens
                    }

                    is TokenRefreshResult.Failure -> {
                        if (result.failure.kind == FailureKind.TERMINAL) {
                            connections.markHealth(
                                connectionId,
                                ConnectionHealth.NEEDS_KEY,
                                result.failure.logLabel
                            )
                        }
                        current
                    }
                }
            }
        }
    }

    private fun lockFor(connectionId: String): Mutex = synchronized(locks) {
        locks.getOrPut(connectionId) { Mutex() }
    }
}
