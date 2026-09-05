package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import dev.agentbayu.app.ai.oauth.OAuthFlow
import dev.agentbayu.app.ai.oauth.ProjectBootstrap
import dev.agentbayu.app.ai.oauth.ProjectBootstrapResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProjectBootstrap(
    private val outcomes: MutableList<ProjectBootstrapResult>,
    private val gate: CompletableDeferred<Unit>? = null
) : ProjectBootstrap {

    constructor(vararg outcomes: ProjectBootstrapResult) : this(outcomes.toMutableList())

    val calls = ArrayList<Triple<String, String, Map<String, String>>>()

    override suspend fun resolve(
        baseUrl: String,
        accessToken: String,
        extraHeaders: Map<String, String>
    ): ProjectBootstrapResult {
        calls += Triple(baseUrl, accessToken, extraHeaders)
        gate?.await()
        return if (outcomes.size > 1) outcomes.removeAt(0) else outcomes.first()
    }
}

private class RecordingProjectIdSink : ProjectIdSink {
    val writes = ArrayList<Pair<String, String?>>()

    override fun setProjectId(connectionId: String, projectId: String?) {
        writes += connectionId to projectId
    }
}

class AntigravityProjectResolverTest {

    private val credential = WireCredential(token = "access-1")

    private fun oauth(): OAuthConfig = OAuthConfig(
        flow = OAuthFlow.AUTHORIZATION_CODE,
        clientId = "app_test",
        tokenUrl = "https://oauth.example.test/token",
        projectBootstrap = true
    )

    private fun candidate(projectId: String? = null): Candidate = testCandidate(
        providerId = "agy",
        wireFormat = WireFormat.ANTIGRAVITY,
        baseUrl = "https://daily.example.test",
        controlBaseUrl = "https://control.example.test",
        projectId = projectId,
        extraHeaders = mapOf("X-Client" to "antigravity"),
        oauth = oauth()
    )

    private fun resolver(
        bootstrap: ProjectBootstrap,
        sink: ProjectIdSink = RecordingProjectIdSink(),
        clock: Clock = FakeClock(1_000L)
    ): AntigravityProjectResolver = AntigravityProjectResolver(bootstrap, sink, clock)

    @Test
    fun aProviderWithoutBootstrapIsPassedThroughUntouched() = runTest {
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Success("project-1"))
        val plain = testCandidate()

        val outcome = resolver(bootstrap).resolve(plain, credential)

        assertSame(plain, (outcome as ProjectResolution.Ready).candidate)
        assertTrue(bootstrap.calls.isEmpty())
    }

    @Test
    fun aConnectionThatAlreadyHasAProjectIsPassedThroughUntouched() = runTest {
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Success("project-9"))
        val ready = candidate(projectId = "project-1")

        val outcome = resolver(bootstrap).resolve(ready, credential)

        assertSame(ready, (outcome as ProjectResolution.Ready).candidate)
        assertTrue(bootstrap.calls.isEmpty())
    }

    @Test
    fun aColdConnectionBootstrapsAgainstTheControlHostAndKeepsTheProject() = runTest {
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Success("project-2"))
        val sink = RecordingProjectIdSink()

        val outcome = resolver(bootstrap, sink).resolve(candidate(), credential)

        val routed = (outcome as ProjectResolution.Ready).candidate
        assertEquals("project-2", routed.connection.projectId)
        assertEquals(
            Triple("https://control.example.test", "access-1", mapOf("X-Client" to "antigravity")),
            bootstrap.calls.single()
        )
        assertEquals(listOf("conn-1" to "project-2"), sink.writes)
    }

    @Test
    fun aResolvedProjectIsReusedWithoutAskingAgain() = runTest {
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Success("project-3"))
        val resolver = resolver(bootstrap)

        resolver.resolve(candidate(), credential)
        val second = resolver.resolve(candidate(), credential)

        assertEquals("project-3", readyProject(second))
        assertEquals(1, bootstrap.calls.size)
    }

    @Test
    fun concurrentRequestsBootstrapOnlyOnce() = runTest {
        val gate = CompletableDeferred<Unit>()
        val bootstrap = FakeProjectBootstrap(
            outcomes = mutableListOf(ProjectBootstrapResult.Success("project-4")),
            gate = gate
        )
        val resolver = resolver(bootstrap)

        val first = async { resolver.resolve(candidate(), credential) }
        val second = async { resolver.resolve(candidate(), credential) }
        runCurrent()
        assertEquals(1, bootstrap.calls.size)
        gate.complete(Unit)

        assertEquals("project-4", readyProject(first.await()))
        assertEquals("project-4", readyProject(second.await()))
        assertEquals(1, bootstrap.calls.size)
    }

    @Test
    fun aFailedBootstrapIsMarkedAsSetupAndNotRetriedImmediately() = runTest {
        val failure = RouteFailure(kind = FailureKind.TERMINAL, message = "no project")
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Failure(failure))
        val clock = FakeClock(1_000L)
        val resolver = resolver(bootstrap, clock = clock)

        val first = resolver.resolve(candidate(), credential)
        clock.advance(AntigravityProjectResolver.RETRY_AFTER_MILLIS - 1L)
        val second = resolver.resolve(candidate(), credential)

        assertTrue((first as ProjectResolution.Failed).failure.needsSetup)
        assertEquals("no project", (second as ProjectResolution.Failed).failure.message)
        assertEquals(1, bootstrap.calls.size)
    }

    @Test
    fun theCooldownExpiresSoASlowSetupCanStillFinish() = runTest {
        val failure = RouteFailure(kind = FailureKind.RETRYABLE, message = "still running")
        val bootstrap = FakeProjectBootstrap(
            ProjectBootstrapResult.Failure(failure),
            ProjectBootstrapResult.Success("project-5")
        )
        val clock = FakeClock(1_000L)
        val resolver = resolver(bootstrap, clock = clock)

        resolver.resolve(candidate(), credential)
        clock.advance(AntigravityProjectResolver.RETRY_AFTER_MILLIS)
        val second = resolver.resolve(candidate(), credential)

        assertEquals("project-5", readyProject(second))
        assertEquals(2, bootstrap.calls.size)
    }

    @Test
    fun aMissingTokenFailsWithoutTouchingTheNetwork() = runTest {
        val bootstrap = FakeProjectBootstrap(ProjectBootstrapResult.Success("project-6"))

        val outcome = resolver(bootstrap).resolve(candidate(), WireCredential())

        val reported = (outcome as ProjectResolution.Failed).failure
        assertEquals(FailureKind.TERMINAL, reported.kind)
        assertTrue(reported.needsSetup)
        assertTrue(bootstrap.calls.isEmpty())
    }

    private fun readyProject(resolution: ProjectResolution): String? =
        (resolution as ProjectResolution.Ready).candidate.connection.projectId
}
