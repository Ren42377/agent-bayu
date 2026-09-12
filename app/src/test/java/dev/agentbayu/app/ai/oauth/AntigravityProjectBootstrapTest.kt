package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.adapter.intField
import dev.agentbayu.app.ai.adapter.objectField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntigravityProjectBootstrapTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun bootstrap(): AntigravityProjectBootstrap = AntigravityProjectBootstrap(client)

    private fun jsonResponse(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private suspend fun resolve(
        extraHeaders: Map<String, String> = emptyMap()
    ): ProjectBootstrapResult = bootstrap().resolve(
        baseUrl = server.url("/").toString().removeSuffix("/"),
        accessToken = "access-1",
        extraHeaders = extraHeaders
    )

    @Test
    fun loadCodeAssistCarriesTheIdeMetadataAndBearerToken() = runTest {
        server.enqueue(jsonResponse("{\"cloudaicompanionProject\":\"project-1\"}"))

        val outcome = resolve(mapOf("X-Client" to "antigravity"))

        assertEquals(ProjectBootstrapResult.Success("project-1"), outcome)
        val request = server.takeRequest()
        assertEquals("/v1internal:loadCodeAssist", request.path)
        assertEquals("Bearer access-1", request.getHeader("Authorization"))
        assertEquals("antigravity", request.getHeader("X-Client"))
        val metadata = parseJsonObject(request.body.readUtf8())?.objectField("metadata")
        assertEquals(9, metadata?.intField("ideType"))
        assertEquals(2, metadata?.intField("platform"))
        assertEquals(2, metadata?.intField("pluginType"))
    }

    @Test
    fun anObjectShapedProjectIsAcceptedWithoutOnboarding() = runTest {
        server.enqueue(
            jsonResponse("{\"cloudaicompanionProject\":{\"id\":\"project-2\"}}")
        )

        assertEquals(ProjectBootstrapResult.Success("project-2"), resolve())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun onboardingSendsTheDefaultTierWhenTheAccountHasNone() = runTest {
        server.enqueue(jsonResponse("{}"))
        server.enqueue(
            jsonResponse(
                "{\"done\":true,\"response\":{\"cloudaicompanionProject\":{\"id\":\"project-3\"}}}"
            )
        )

        assertEquals(ProjectBootstrapResult.Success("project-3"), resolve())
        server.takeRequest()
        val onboard = server.takeRequest()
        assertEquals("/v1internal:onboardUser", onboard.path)
        val body = parseJsonObject(onboard.body.readUtf8())
        assertEquals("legacy-tier", body?.stringField("tier_id"))
    }

    @Test
    fun onboardingPrefersThePaidTierOverTheDefaultOne() = runTest {
        server.enqueue(
            jsonResponse(
                "{\"paidTier\":{\"id\":\"paid-tier\"}," +
                    "\"allowedTiers\":[{\"id\":\"free-tier\",\"isDefault\":true}]}"
            )
        )
        server.enqueue(
            jsonResponse("{\"done\":true,\"cloudaicompanionProject\":\"project-4\"}")
        )

        assertEquals(ProjectBootstrapResult.Success("project-4"), resolve())
        server.takeRequest()
        val onboard = server.takeRequest()
        assertEquals("paid-tier", parseJsonObject(onboard.body.readUtf8())?.stringField("tier_id"))
    }

    @Test
    fun onboardingKeepsPollingWhileTheOperationOmitsDone() = runTest {
        server.enqueue(jsonResponse("{\"currentTier\":{\"id\":\"free-tier\"}}"))
        server.enqueue(jsonResponse("{\"name\":\"operations/1\"}"))
        server.enqueue(jsonResponse("{\"done\":false}"))
        server.enqueue(
            jsonResponse(
                "{\"done\":true,\"response\":{\"cloudaicompanionProject\":\"project-5\"}}"
            )
        )

        assertEquals(ProjectBootstrapResult.Success("project-5"), resolve())
        assertEquals(4, server.requestCount)
    }

    @Test
    fun onboardingThatNeverSettlesStaysRetryable() = runTest {
        server.enqueue(jsonResponse("{}"))
        repeat(5) { server.enqueue(jsonResponse("{\"name\":\"operations/1\"}")) }

        val outcome = resolve()

        val failure = (outcome as ProjectBootstrapResult.Failure).failure
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertEquals(6, server.requestCount)
    }

    @Test
    fun anAccountWithoutAProjectIsTerminal() = runTest {
        server.enqueue(jsonResponse("{}"))
        server.enqueue(jsonResponse("{\"done\":true,\"response\":{}}"))

        val failure = (resolve() as ProjectBootstrapResult.Failure).failure

        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertTrue(failure.message.contains("Google Cloud project"))
    }

    @Test
    fun aRejectedLoadIsClassifiedFromItsStatus() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"invalid\"}", code = 401))

        val failure = (resolve() as ProjectBootstrapResult.Failure).failure

        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertEquals(401, failure.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aBodyThatIsNotJsonIsTerminal() = runTest {
        server.enqueue(jsonResponse("not json"))

        val failure = (resolve() as ProjectBootstrapResult.Failure).failure

        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertNull(failure.statusCode)
    }
}
