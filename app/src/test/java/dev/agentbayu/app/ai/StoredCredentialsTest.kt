package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import dev.agentbayu.app.ai.oauth.OAuthFlow
import dev.agentbayu.app.ai.oauth.TokenRefresher
import dev.agentbayu.app.platform.InMemoryStorage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

class StoredCredentialsTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val clock = FakeClock(100_000L)
    private val store = CredentialStore(InMemoryStorage())
    private val connections = FakeConnectionSource()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(): OAuthConfig = OAuthConfig(
        flow = OAuthFlow.DEVICE_CODE,
        clientId = "app_test",
        tokenUrl = server.url("/oauth/token").toString(),
        accountField = "chatgpt_account_id",
        accountHeader = "chatgpt-account-id"
    )

    private fun candidate(oauth: OAuthConfig? = config()): Candidate = testCandidate(
        authKind = if (oauth == null) AuthKind.API_KEY else AuthKind.OAUTH_DEVICE,
        oauth = oauth
    )

    private fun credentials(): StoredCredentials = StoredCredentials(
        store = store,
        refresher = TokenRefresher(client, clock),
        connections = connections,
        clock = clock
    )

    private fun tokenResponse(accessToken: String, delayMillis: Long = 0L): MockResponse {
        val response = MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"refresh-2\"," +
                    "\"expires_in\":3600}"
            )
        if (delayMillis > 0L) {
            response.setBodyDelay(delayMillis, TimeUnit.MILLISECONDS)
        }
        return response
    }

    @Test
    fun `an api key is handed over as it is`() = runTest {
        store.putApiKey("conn-1", "key-1234")

        val resolved = credentials().resolve(candidate(oauth = null))

        assertEquals("key-1234", resolved.token)
        assertTrue(resolved.headers.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a live token is handed over with the account header`() = runTest {
        store.put(
            "conn-1",
            Credential.OAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = 900_000L,
                extras = mapOf("chatgpt_account_id" to "acc-42")
            )
        )

        val resolved = credentials().resolve(candidate())

        assertEquals("access-1", resolved.token)
        assertEquals(mapOf("chatgpt-account-id" to "acc-42"), resolved.headers)
        assertEquals(0, server.requestCount)
        assertTrue(connections.healthCalls.isEmpty())
    }

    @Test
    fun `a token inside the expiry margin is refreshed and stored`() = runTest {
        server.enqueue(tokenResponse("access-2"))
        store.put(
            "conn-1",
            Credential.OAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = 130_000L,
                extras = mapOf("chatgpt_account_id" to "acc-42")
            )
        )

        val resolved = credentials().resolve(candidate())

        assertEquals("access-2", resolved.token)
        assertEquals(mapOf("chatgpt-account-id" to "acc-42"), resolved.headers)
        assertEquals(1, server.requestCount)

        val stored = store.credential("conn-1") as Credential.OAuthTokens
        assertEquals("access-2", stored.accessToken)
        assertEquals("refresh-2", stored.refreshToken)
        assertEquals(100_000L + 3_600_000L, stored.expiresAtMillis)
        assertTrue(connections.healthCalls.isEmpty())
    }

    @Test
    fun `concurrent callers share a single refresh`() = runTest {
        server.enqueue(tokenResponse("access-2", delayMillis = 200L))
        store.put(
            "conn-1",
            Credential.OAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = 130_000L
            )
        )
        val provider = credentials()
        val tokens = ArrayList<String?>()

        coroutineScope {
            repeat(3) {
                launch { tokens += provider.resolve(candidate()).token }
            }
        }

        assertEquals(listOf("access-2", "access-2", "access-2"), tokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a rejected refresh marks the connection and keeps the stale token`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"))
        store.put(
            "conn-1",
            Credential.OAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = 130_000L
            )
        )

        val resolved = credentials().resolve(candidate())

        assertEquals("access-1", resolved.token)
        assertEquals("conn-1" to ConnectionHealth.NEEDS_KEY, connections.healthCalls.single())
    }

    @Test
    fun `a refresh that fails on the network leaves health alone`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("overloaded"))
        store.put(
            "conn-1",
            Credential.OAuthTokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                expiresAtMillis = 130_000L
            )
        )

        val resolved = credentials().resolve(candidate())

        assertEquals("access-1", resolved.token)
        assertTrue(connections.healthCalls.isEmpty())
    }

    @Test
    fun `a connection without any credential resolves to nothing`() = runTest {
        val resolved = credentials().resolve(candidate())

        assertNull(resolved.token)
        assertTrue(resolved.headers.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an anonymous key stands in when nothing is stored`() = runTest {
        val resolved = credentials().resolve(
            testCandidate(authKind = AuthKind.NONE, optionalKey = true, anonymousKey = "0000000000")
        )

        assertEquals("0000000000", resolved.token)
    }

    @Test
    fun `oauth tokens without a matching config fall back to the raw secret`() = runTest {
        store.put(
            "conn-1",
            Credential.OAuthTokens(accessToken = "access-1", expiresAtMillis = 1L)
        )

        val resolved = credentials().resolve(candidate(oauth = null))

        assertEquals("access-1", resolved.token)
        assertTrue(resolved.headers.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a key source provider hands over the stored key`() = runTest {
        val resolved = KeySourceCredentials(FakeKeys(mapOf("conn-1" to "key-1")))
            .resolve(candidate(oauth = null))

        assertEquals("key-1", resolved.token)
        assertTrue(resolved.headers.isEmpty())
    }
}
