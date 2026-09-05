package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.ProjectBootstrap
import dev.agentbayu.app.ai.oauth.ProjectBootstrapResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ProjectResolution {
    data class Ready(val candidate: Candidate) : ProjectResolution

    data class Failed(val failure: RouteFailure) : ProjectResolution
}

interface ProjectResolver {
    suspend fun resolve(candidate: Candidate, credential: WireCredential): ProjectResolution
}

interface ProjectIdSink {
    fun setProjectId(connectionId: String, projectId: String?)
}

object ReadyProjectResolver : ProjectResolver {
    override suspend fun resolve(
        candidate: Candidate,
        credential: WireCredential
    ): ProjectResolution = ProjectResolution.Ready(candidate)
}

class AntigravityProjectResolver(
    private val bootstrap: ProjectBootstrap,
    private val sink: ProjectIdSink,
    private val clock: Clock = RealClock,
    private val retryAfterMillis: Long = RETRY_AFTER_MILLIS
) : ProjectResolver {

    private val guard = Any()
    private val locks = HashMap<String, Mutex>()
    private val resolved = HashMap<String, String>()
    private val blocked = HashMap<String, Blocked>()

    override suspend fun resolve(
        candidate: Candidate,
        credential: WireCredential
    ): ProjectResolution {
        if (!candidate.provider.needsProjectBootstrap) return ProjectResolution.Ready(candidate)
        if (!candidate.connection.projectId.isNullOrBlank()) {
            return ProjectResolution.Ready(candidate)
        }
        val connectionId = candidate.connection.id
        cached(connectionId)?.let { return ProjectResolution.Ready(candidate.withProject(it)) }
        val token = credential.token?.takeIf { it.isNotBlank() }
            ?: return ProjectResolution.Failed(signedOut())

        return lockFor(connectionId).withLock {
            cached(connectionId)?.let {
                return@withLock ProjectResolution.Ready(candidate.withProject(it))
            }
            waiting(connectionId)?.let { return@withLock ProjectResolution.Failed(it) }
            val outcome = bootstrap.resolve(
                baseUrl = candidate.controlBaseUrl,
                accessToken = token,
                extraHeaders = candidate.provider.extraHeaders
            )
            when (outcome) {
                is ProjectBootstrapResult.Success -> {
                    remember(connectionId, outcome.projectId)
                    sink.setProjectId(connectionId, outcome.projectId)
                    ProjectResolution.Ready(candidate.withProject(outcome.projectId))
                }

                is ProjectBootstrapResult.Failure -> {
                    val failure = outcome.failure.copy(needsSetup = true)
                    block(connectionId, failure)
                    ProjectResolution.Failed(failure)
                }
            }
        }
    }

    private fun cached(connectionId: String): String? = synchronized(guard) {
        resolved[connectionId]
    }

    private fun remember(connectionId: String, projectId: String) = synchronized(guard) {
        resolved[connectionId] = projectId
        blocked.remove(connectionId)
    }

    private fun block(connectionId: String, failure: RouteFailure) = synchronized(guard) {
        blocked[connectionId] = Blocked(clock.nowMillis() + retryAfterMillis, failure)
    }

    private fun waiting(connectionId: String): RouteFailure? = synchronized(guard) {
        val entry = blocked[connectionId]
        when {
            entry == null -> null
            clock.nowMillis() < entry.untilMillis -> entry.failure
            else -> {
                blocked.remove(connectionId)
                null
            }
        }
    }

    private fun lockFor(connectionId: String): Mutex = synchronized(guard) {
        locks.getOrPut(connectionId) { Mutex() }
    }

    private fun signedOut(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "sign in again to finish Antigravity project setup",
        needsSetup = true
    )

    private data class Blocked(val untilMillis: Long, val failure: RouteFailure)

    companion object {
        const val RETRY_AFTER_MILLIS = 60_000L
    }
}

private fun Candidate.withProject(projectId: String): Candidate =
    copy(connection = connection.copy(projectId = projectId))
