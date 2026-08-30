package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.Credential
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.FakeClock
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenRefresherTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val clock = FakeClock(10_000L)

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
        accountClaim = "https://api.openai.com/auth",
        accountField = "chatgpt_account_id",
        accountHeader = "chatgpt-account-id"
    )

    private fun jsonResponse(body: String, code: Int = 200): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun idToken(accountId: String): String {
        val payload = "{\"https://api.openai.com/auth\":{\"chatgpt_account_id\":\"" +
            accountId + "\"}}"
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "e30." + encoded + ".signature"
    }

    private fun tokens(
        accessToken: String = "old-access",
        refreshToken: String? = "refresh-1",
        expiresAtMillis: Long = 0L,
        extras: Map<String, String> = emptyMap()
    ): Credential.OAuthTokens = Credential.OAuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAtMillis,
        extras = extras
    )

    private fun refresher(): TokenRefresher = TokenRefresher(client, clock)

    @Test
    fun `a refresh posts the grant and returns the new tokens`() = runTest {
        server.enqueue(
            jsonResponse(
                "{\"access_token\":\"new-access\",\"refresh_token\":\"refresh-2\"," +
                    "\"expires_in\":3600,\"id_token\":\"" + idToken("acc-42") + "\"}"
            )
        )

        val result = refresher().refresh(config(), tokens())

        val refreshed = (result as TokenRefreshResult.Success).tokens
        assertEquals("new-access", refreshed.accessToken)
        assertEquals("refresh-2", refreshed.refreshToken)
        assertEquals(10_000L + 3_600_000L, refreshed.expiresAtMillis)
        assertEquals(mapOf("chatgpt_account_id" to "acc-42"), refreshed.extras)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/oauth/token", recorded.path)
        val form = recorded.body.readUtf8()
        assertTrue(form, form.contains("grant_type=refresh_token"))
        assertTrue(form, form.contains("client_id=app_test"))
        assertTrue(form, form.contains("refresh_token=refresh-1"))
    }

    @Test
    fun `a response without a refresh token keeps the old one`() = runTest {
        server.enqueue(jsonResponse("{\"access_token\":\"new-access\",\"expires_in\":60}"))

        val refreshed = (refresher().refresh(config(), tokens()) as TokenRefreshResult.Success).tokens

        assertEquals("refresh-1", refreshed.refreshToken)
        assertEquals(10_000L + 60_000L, refreshed.expiresAtMillis)
    }

    @Test
    fun `extras survive a refresh that carries no id token`() = runTest {
        server.enqueue(jsonResponse("{\"access_token\":\"new-access\"}"))

        val refreshed = (
            refresher().refresh(
                config(),
                tokens(extras = mapOf("chatgpt_account_id" to "acc-7"))
            ) as TokenRefreshResult.Success
            ).tokens

        assertEquals(mapOf("chatgpt_account_id" to "acc-7"), refreshed.extras)
        assertEquals(0L, refreshed.expiresAtMillis)
    }

    @Test
    fun `a refresh without a stored refresh token never leaves the device`() = runTest {
        val result = refresher().refresh(config(), tokens(refreshToken = null))

        assertEquals(FailureKind.TERMINAL, (result as TokenRefreshResult.Failure).failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a rejected refresh token is terminal`() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"invalid_grant\"}", code = 400))

        val result = refresher().refresh(config(), tokens())

        val failure = (result as TokenRefreshResult.Failure).failure
        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertEquals(400, failure.statusCode)
    }

    @Test
    fun `a server error stays retryable`() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"boom\"}", code = 503))

        val result = refresher().refresh(config(), tokens())

        val failure = (result as TokenRefreshResult.Failure).failure
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertEquals(503, failure.statusCode)
    }

    @Test
    fun `a response without an access token is terminal`() = runTest {
        server.enqueue(jsonResponse("{\"token_type\":\"bearer\"}"))

        val result = refresher().refresh(config(), tokens())

        assertEquals(FailureKind.TERMINAL, (result as TokenRefreshResult.Failure).failure.kind)
    }

    @Test
    fun `read tokens ignores an id token when the config names no claim`() {
        val parsed = readTokens(
            body = "{\"access_token\":\"new-access\",\"id_token\":\"" + idToken("acc-42") + "\"}",
            config = config().copy(accountClaim = null, accountField = null),
            previous = null,
            nowMillis = 0L
        )

        assertTrue(parsed!!.extras.isEmpty())
    }

    @Test
    fun `read tokens returns null on a malformed body`() {
        assertNull(readTokens("not json", config(), null, 0L))
    }

    @Test
    fun `the expiry margin asks for a refresh a minute early`() {
        val expiring = tokens(expiresAtMillis = 40_000L)

        assertTrue(expiring.isExpired(0L))
        assertTrue(expiring.isExpired(40_000L))
        assertFalse(tokens(expiresAtMillis = 400_000L).isExpired(0L))
        assertFalse(tokens(expiresAtMillis = 0L).isExpired(Long.MAX_VALUE / 2))
    }
}
