package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.FakeClock
import java.time.Instant
import java.util.Base64
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

class CodexDeviceFlowTest {

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

    private class SteppingClock(private var now: Long, private val step: Long) : Clock {
        override fun nowMillis(): Long {
            val value = now
            now += step
            return value
        }
    }

    private fun config(): OAuthConfig = OAuthConfig(
        flow = OAuthFlow.DEVICE_CODE,
        clientId = "app_test",
        tokenUrl = server.url("/oauth/token").toString(),
        userCodeUrl = server.url("/api/accounts/deviceauth/usercode").toString(),
        pollUrl = server.url("/api/accounts/deviceauth/token").toString(),
        verificationUrl = "https://auth.example.test/codex/device",
        redirectUri = "https://auth.example.test/deviceauth/callback",
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

    private fun start(
        deviceAuthId: String = "device-1",
        userCode: String = "KTE0-XUIUX",
        pollIntervalMillis: Long = 10L,
        expiresAtMillis: Long = 600_000L
    ): DeviceCodeStart = DeviceCodeStart(
        deviceAuthId = deviceAuthId,
        userCode = userCode,
        pollIntervalMillis = pollIntervalMillis,
        expiresAtMillis = expiresAtMillis
    )

    private fun flow(clock: Clock = FakeClock()): CodexDeviceFlow = CodexDeviceFlow(client, clock)

    @Test
    fun `start reads an interval that arrives as a string`() = runTest {
        server.enqueue(
            jsonResponse(
                "{\"device_auth_id\":\"device-1\",\"user_code\":\"KTE0-XUIUX\"," +
                    "\"interval\":\"5\",\"expires_in\":600}"
            )
        )

        val result = flow().start(config())

        val started = (result as DeviceCodeStartResult.Success).start
        assertEquals("device-1", started.deviceAuthId)
        assertEquals("KTE0-XUIUX", started.userCode)
        assertEquals(5_000L, started.pollIntervalMillis)
        assertEquals(600_000L, started.expiresAtMillis)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/accounts/deviceauth/usercode", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"app_test\""))
    }

    @Test
    fun `start reads an interval that arrives as a number`() = runTest {
        server.enqueue(
            jsonResponse(
                "{\"device_auth_id\":\"device-1\",\"user_code\":\"CODE\",\"interval\":3}"
            )
        )

        val started = (flow().start(config()) as DeviceCodeStartResult.Success).start

        assertEquals(3_000L, started.pollIntervalMillis)
    }

    @Test
    fun `start falls back to the default interval and expiry`() = runTest {
        server.enqueue(jsonResponse("{\"device_auth_id\":\"device-1\",\"user_code\":\"CODE\"}"))

        val started = (flow().start(config()) as DeviceCodeStartResult.Success).start

        assertEquals(
            CodexDeviceFlow.DEFAULT_INTERVAL_SECONDS * 1_000L,
            started.pollIntervalMillis
        )
        assertEquals(CodexDeviceFlow.DEFAULT_EXPIRY_SECONDS * 1_000L, started.expiresAtMillis)
    }

    @Test
    fun `start reads an iso expiry`() = runTest {
        server.enqueue(
            jsonResponse(
                "{\"device_auth_id\":\"device-1\",\"user_code\":\"CODE\"," +
                    "\"expires_at\":\"2026-08-30T10:00:00Z\"}"
            )
        )

        val started = (flow().start(config()) as DeviceCodeStartResult.Success).start

        assertEquals(
            Instant.parse("2026-08-30T10:00:00Z").toEpochMilli(),
            started.expiresAtMillis
        )
    }

    @Test
    fun `start reports a malformed response`() = runTest {
        server.enqueue(jsonResponse("{\"user_code\":\"CODE\"}"))

        val result = flow().start(config())

        assertEquals(
            FailureKind.TERMINAL,
            (result as DeviceCodeStartResult.Failure).failure.kind
        )
    }

    @Test
    fun `start without a user code url is unsupported`() = runTest {
        val result = flow().start(config().copy(userCodeUrl = null))

        assertEquals(
            FailureKind.TERMINAL,
            (result as DeviceCodeStartResult.Failure).failure.kind
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `start sends an http error through the classifier`() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"nope\"}", code = 500))

        val result = flow().start(config())

        val failure = (result as DeviceCodeStartResult.Failure).failure
        assertEquals(FailureKind.RETRYABLE, failure.kind)
        assertEquals(500, failure.statusCode)
    }

    @Test
    fun `polling treats 403 and 404 as not yet authorized`() = runTest {
        server.enqueue(jsonResponse("{\"detail\":\"pending\"}", code = 403))
        server.enqueue(jsonResponse("{\"detail\":\"pending\"}", code = 404))
        server.enqueue(
            jsonResponse(
                "{\"authorization_code\":\"auth-1\",\"code_verifier\":\"verifier-1\"}"
            )
        )
        server.enqueue(
            jsonResponse(
                "{\"access_token\":\"access-1\",\"refresh_token\":\"refresh-1\"," +
                    "\"expires_in\":3600,\"id_token\":\"" + idToken("acc-42") + "\"}"
            )
        )

        val result = flow(FakeClock(1_000L)).awaitAuthorization(config(), start())

        val tokens = (result as DeviceCodeResult.Success).tokens
        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(1_000L + 3_600_000L, tokens.expiresAtMillis)
        assertEquals(mapOf("chatgpt_account_id" to "acc-42"), tokens.extras)
        assertEquals(4, server.requestCount)

        repeat(3) { server.takeRequest() }
        val exchange = server.takeRequest()
        assertEquals("/oauth/token", exchange.path)
        val form = exchange.body.readUtf8()
        assertTrue(form, form.contains("grant_type=authorization_code"))
        assertTrue(form, form.contains("client_id=app_test"))
        assertTrue(form, form.contains("code=auth-1"))
        assertTrue(form, form.contains("code_verifier=verifier-1"))
        assertTrue(form, form.contains("redirect_uri="))
    }

    @Test
    fun `polling treats a pending error body as not yet authorized`() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"authorization_pending\"}"))
        server.enqueue(
            jsonResponse("{\"authorization_code\":\"auth-1\",\"code_verifier\":\"verifier-1\"}")
        )
        server.enqueue(jsonResponse("{\"access_token\":\"access-1\"}"))

        val result = flow().awaitAuthorization(config(), start())

        val tokens = (result as DeviceCodeResult.Success).tokens
        assertEquals("access-1", tokens.accessToken)
        assertNull(tokens.refreshToken)
        assertEquals(0L, tokens.expiresAtMillis)
        assertTrue(tokens.extras.isEmpty())
    }

    @Test
    fun `polling stops on a terminal status`() = runTest {
        server.enqueue(jsonResponse("{\"error\":\"invalid_client\"}", code = 401))

        val result = flow().awaitAuthorization(config(), start())

        val failure = (result as DeviceCodeResult.Failure).failure
        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertEquals(401, failure.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `polling gives up once the code expires`() = runTest {
        server.enqueue(jsonResponse("{\"detail\":\"pending\"}", code = 403))
        server.enqueue(jsonResponse("{\"detail\":\"pending\"}", code = 403))

        val result = flow(SteppingClock(now = 0L, step = 6_000L))
            .awaitAuthorization(config(), start(expiresAtMillis = 10_000L))

        val failure = (result as DeviceCodeResult.Failure).failure
        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertEquals("device code expired", failure.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `polling without a poll url is unsupported`() = runTest {
        val result = flow().awaitAuthorization(config().copy(pollUrl = null), start())

        assertEquals(FailureKind.TERMINAL, (result as DeviceCodeResult.Failure).failure.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a token response without an access token fails`() = runTest {
        server.enqueue(
            jsonResponse("{\"authorization_code\":\"auth-1\",\"code_verifier\":\"verifier-1\"}")
        )
        server.enqueue(jsonResponse("{\"token_type\":\"bearer\"}"))

        val result = flow().awaitAuthorization(config(), start())

        assertEquals(FailureKind.TERMINAL, (result as DeviceCodeResult.Failure).failure.kind)
    }

    @Test
    fun `a rejected exchange stops the flow`() = runTest {
        server.enqueue(
            jsonResponse("{\"authorization_code\":\"auth-1\",\"code_verifier\":\"verifier-1\"}")
        )
        server.enqueue(jsonResponse("{\"error\":\"invalid_grant\"}", code = 400))

        val result = flow().awaitAuthorization(config(), start())

        val failure = (result as DeviceCodeResult.Failure).failure
        assertEquals(FailureKind.TERMINAL, failure.kind)
        assertEquals(400, failure.statusCode)
    }
}
