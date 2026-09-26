package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import dev.agentbayu.app.ai.oauth.OAuthFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuotaFetcherTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val clock = FakeClock(1_700_000_000_000L)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `codex usage endpoint fills both windows with the account header`() {
        server.enqueue(
            jsonResponse(
                "{\"rate_limit\":{" +
                    "\"primary_window\":{\"used_percent\":42.5,\"reset_at\":1800000000}," +
                    "\"secondary_window\":{\"used_percent\":10,\"reset_after_seconds\":3600}" +
                    "}}"
            )
        )
        val fetcher = fetcher(
            codexProvider(quotaUrl = server.url("/backend-api/wham/usage").toString()),
            FixedCredentials("token-123", mapOf("chatgpt-account-id" to "acct-1"))
        )

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "codex", model = "gpt-5.6-luna"))
        }

        val snapshot = (result as QuotaFetchResult.Success).snapshot
        val short = snapshot.windows.first { it.id == QuotaParser.WINDOW_5H }
        assertEquals(42.5, short.percentUsed!!, 0.001)
        assertEquals(1_800_000_000_000L, short.resetAtMillis!!)
        assertEquals(
            clock.nowMillis() + 3_600_000L,
            snapshot.windows.first { it.id == QuotaParser.WINDOW_7D }.resetAtMillis!!
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/backend-api/wham/usage", request.path)
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
        assertEquals("acct-1", request.getHeader("chatgpt-account-id"))
    }

    @Test
    fun `antigravity quota summary expands every pool bucket`() {
        server.enqueue(
            jsonResponse(
                "{\"groups\":[" +
                    "{\"displayName\":\"Gemini models\",\"buckets\":[" +
                    "{\"bucketId\":\"gemini-5h\",\"remainingFraction\":0.75,\"resetTime\":\"1800000000\"}," +
                    "{\"bucketId\":\"gemini-weekly\",\"remainingFraction\":0.9}" +
                    "]}," +
                    "{\"displayName\":\"Claude and other models\",\"buckets\":[" +
                    "{\"bucketId\":\"3p-5h\",\"remainingFraction\":0.4}" +
                    "]}" +
                    "]}"
            )
        )
        val fetcher = fetcher(agyProvider(), FixedCredentials("token-123"))

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "agy", model = "gemini-3.8-flash"))
        }

        val snapshot = (result as QuotaFetchResult.Success).snapshot
        assertEquals(3, snapshot.windows.size)
        val geminiShort = snapshot.windows.first {
            it.poolId == QuotaParser.POOL_GEMINI && it.id == QuotaParser.WINDOW_5H
        }
        assertEquals(25.0, geminiShort.percentUsed!!, 0.001)
        val otherShort = snapshot.windows.first {
            it.poolId == QuotaParser.POOL_THIRD_PARTY && it.id == QuotaParser.WINDOW_5H
        }
        assertEquals(60.0, otherShort.percentUsed!!, 0.001)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/daily/v1internal:retrieveUserQuotaSummary", request.path)
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `antigravity quota falls back to the control host`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            jsonResponse(
                "{\"response\":{\"groups\":[{\"buckets\":[" +
                    "{\"bucketId\":\"3p-5h\",\"remainingFraction\":0.5}" +
                    "]}]}}"
            )
        )
        val fetcher = fetcher(agyProvider(), FixedCredentials("token-123"))

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "agy", model = "gemini-3.8-flash"))
        }

        val snapshot = (result as QuotaFetchResult.Success).snapshot
        assertEquals(1, snapshot.windows.size)
        assertEquals(50.0, snapshot.windows.single().percentUsed!!, 0.001)
        assertEquals("/daily/v1internal:retrieveUserQuotaSummary", server.takeRequest().path)
        assertEquals("/control/v1internal:retrieveUserQuotaSummary", server.takeRequest().path)
    }

    @Test
    fun `dead token stops the host fallback`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val fetcher = fetcher(agyProvider(), FixedCredentials("expired"))

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "agy", model = "gemini-3.8-flash"))
        }

        assertEquals(401, (result as QuotaFetchResult.Failure).failure.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalid buckets and unknown ids produce a failure`() {
        val body =
            "{\"groups\":[{\"buckets\":[" +
                "{\"bucketId\":\"gemini-5h\",\"resetTime\":\"1800000000\"}," +
                "{\"bucketId\":\"gemini-image-5h\",\"remainingFraction\":0.1}," +
                "{\"bucketId\":\"3p-5h\",\"remainingFraction\":2.0}" +
                "]}]}"
        server.enqueue(jsonResponse(body))
        server.enqueue(jsonResponse(body))
        val fetcher = fetcher(agyProvider(), FixedCredentials("token-123"))

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "agy", model = "gemini-3.8-flash"))
        }

        assertTrue(result is QuotaFetchResult.Failure)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `providers without a quota url are unsupported`() {
        val fetcher = fetcher(codexProvider(quotaUrl = null), FixedCredentials("token-123"))

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "codex", model = "gpt-5.6-luna"))
        }

        assertEquals(QuotaFetchResult.Unsupported, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing credential fails without a request`() {
        val fetcher = fetcher(
            codexProvider(quotaUrl = server.url("/backend-api/wham/usage").toString()),
            FixedCredentials(null)
        )

        val result = runBlocking {
            fetcher.fetch(testConnection(providerId = "codex", model = "gpt-5.6-luna"))
        }

        assertTrue(result is QuotaFetchResult.Failure)
        assertEquals(0, server.requestCount)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun fetcher(provider: ProviderEntry, credentials: CredentialProvider): QuotaFetcher =
        QuotaFetcher(
            client = client,
            catalog = ProviderCatalog(listOf(provider)),
            credentials = credentials,
            clock = clock
        )

    private fun codexProvider(quotaUrl: String?): ProviderEntry = testProvider(
        id = "codex",
        authKind = AuthKind.OAUTH_DEVICE,
        tier = ProviderTier.SUBSCRIPTION,
        models = listOf(ModelEntry(id = "gpt-5.6-luna")),
        oauth = OAuthConfig(
            flow = OAuthFlow.DEVICE_CODE,
            clientId = "client-id",
            tokenUrl = "https://auth.example.test/token",
            quotaUrl = quotaUrl
        )
    )

    private fun agyProvider(): ProviderEntry = testProvider(
        id = "agy",
        wireFormat = WireFormat.ANTIGRAVITY,
        baseUrl = server.url("/daily").toString(),
        controlBaseUrl = server.url("/control").toString(),
        authKind = AuthKind.OAUTH_PKCE,
        tier = ProviderTier.SUBSCRIPTION,
        models = listOf(ModelEntry(id = "gemini-3.8-flash")),
        oauth = OAuthConfig(
            flow = OAuthFlow.AUTHORIZATION_CODE,
            clientId = "client-id",
            tokenUrl = "https://oauth2.example.test/token",
            quotaUrl = "/v1internal:retrieveUserQuotaSummary",
            quotaMethod = "POST"
        )
    )

    private class FixedCredentials(
        private val token: String?,
        private val headers: Map<String, String> = emptyMap()
    ) : CredentialProvider {
        override suspend fun resolve(candidate: Candidate): WireCredential =
            WireCredential(token = token, headers = headers)
    }
}
