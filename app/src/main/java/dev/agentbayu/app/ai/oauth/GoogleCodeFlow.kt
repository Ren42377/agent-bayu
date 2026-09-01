package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.Credential
import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.RouteFailure
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class LoopbackSession internal constructor(
    internal val socket: ServerSocket,
    val redirectUri: String,
    val state: String
) {

    internal fun accept(): String? {
        socket.accept().use { peer ->
            val line = peer.getInputStream().bufferedReader().readLine() ?: return null
            val target = line.split(' ').getOrNull(1) ?: return null
            val handled = target.contains('?')
            peer.getOutputStream().writer().apply {
                write(responseFor(handled))
                flush()
            }
            return target.takeIf { handled }
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    private fun responseFor(handled: Boolean): String {
        val status = if (handled) "200 OK" else "404 Not Found"
        val body = if (handled) CALLBACK_PAGE else EMPTY_PAGE
        return buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(body.toByteArray().size).append("\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
    }

    private companion object {
        const val CALLBACK_PAGE =
            "<html><body><h3>Sign-in complete</h3><p>Return to Agent Bayu.</p></body></html>"
        const val EMPTY_PAGE = "<html><body></body></html>"
    }
}

sealed interface BrowserLoginStartResult {
    class Success(val session: LoopbackSession, val authorizeUrl: String) : BrowserLoginStartResult

    data class Failure(val failure: RouteFailure) : BrowserLoginStartResult
}

sealed interface BrowserCallbackResult {
    data class Success(val redirect: String) : BrowserCallbackResult

    data class Failure(val failure: RouteFailure) : BrowserCallbackResult
}

sealed interface BrowserLoginResult {
    data class Success(val tokens: Credential.OAuthTokens) : BrowserLoginResult

    data class Failure(val failure: RouteFailure) : BrowserLoginResult
}

class GoogleCodeFlow(
    private val client: OkHttpClient,
    private val clock: Clock = RealClock
) {

    fun start(config: OAuthConfig): BrowserLoginStartResult {
        val authorizeUrl = config.authorizeUrl?.takeIf { it.isNotBlank() }
            ?: return BrowserLoginStartResult.Failure(unsupported())
        val socket = try {
            ServerSocket(0, BACKLOG, InetAddress.getByName(LOOPBACK_HOST))
        } catch (error: IOException) {
            return BrowserLoginStartResult.Failure(listenerFailed())
        }
        val path = config.redirectPath?.takeIf { it.isNotBlank() } ?: DEFAULT_REDIRECT_PATH
        val redirectUri = "http://$LOOPBACK_HOST:${socket.localPort}$path"
        val state = randomState()
        return BrowserLoginStartResult.Success(
            session = LoopbackSession(socket, redirectUri, state),
            authorizeUrl = buildAuthorizeUrl(config, authorizeUrl, redirectUri, state)
        )
    }

    suspend fun awaitCallback(
        session: LoopbackSession,
        timeoutMillis: Long
    ): BrowserCallbackResult = withContext(Dispatchers.IO) {
        val deadline = clock.nowMillis() + timeoutMillis
        try {
            session.socket.soTimeout = ACCEPT_POLL_MILLIS
        } catch (error: IOException) {
            return@withContext BrowserCallbackResult.Failure(listenerFailed())
        }
        while (currentCoroutineContext().isActive && clock.nowMillis() < deadline) {
            val target = try {
                session.accept()
            } catch (error: SocketTimeoutException) {
                null
            } catch (error: IOException) {
                return@withContext BrowserCallbackResult.Failure(listenerFailed())
            }
            if (target != null) {
                return@withContext BrowserCallbackResult.Success(target)
            }
        }
        BrowserCallbackResult.Failure(expired())
    }

    suspend fun exchange(
        config: OAuthConfig,
        session: LoopbackSession,
        redirect: String
    ): BrowserLoginResult {
        val params = parseQuery(redirect)
        params[ERROR]?.takeIf { it.isNotBlank() }?.let { reason ->
            return BrowserLoginResult.Failure(denied(reason))
        }
        if (params[STATE] != session.state) {
            return BrowserLoginResult.Failure(stateMismatch())
        }
        val code = params[CODE]?.takeIf { it.isNotBlank() }
            ?: return BrowserLoginResult.Failure(malformed())
        val form = FormBody.Builder()
            .add(GRANT_TYPE, AUTHORIZATION_CODE_GRANT)
            .add(CODE, code)
            .add(REDIRECT_URI, session.redirectUri)
            .add(CLIENT_ID, config.clientId)
            .apply {
                config.clientSecret?.let { secret -> add(CLIENT_SECRET, secret) }
            }
            .build()
        val outcome = try {
            post(config.tokenUrl, form)
        } catch (error: IOException) {
            return BrowserLoginResult.Failure(FailureClassifier.classifyError(error))
        }
        if (!outcome.successful) return BrowserLoginResult.Failure(outcome.asFailure())
        val tokens = readTokens(outcome.body, config, null, clock.nowMillis())
            ?: return BrowserLoginResult.Failure(malformed())
        return BrowserLoginResult.Success(tokens)
    }

    private fun buildAuthorizeUrl(
        config: OAuthConfig,
        base: String,
        redirectUri: String,
        state: String
    ): String {
        val params = LinkedHashMap<String, String>()
        params[CLIENT_ID] = config.clientId
        params[RESPONSE_TYPE] = CODE
        params[REDIRECT_URI] = redirectUri
        if (config.scopes.isNotEmpty()) {
            params[SCOPE] = config.scopes.joinToString(" ")
        }
        params[STATE] = state
        config.extraAuthorizeParams.forEach { (key, value) -> params[key] = value }
        val query = params.entries.joinToString("&") { entry ->
            encode(entry.key) + "=" + encode(entry.value)
        }
        return base + (if (base.contains('?')) "&" else "?") + query
    }

    private fun parseQuery(redirect: String): Map<String, String> {
        val query = try {
            URI(redirect).rawQuery
        } catch (error: URISyntaxException) {
            null
        } ?: redirect.substringAfter('?', "")
        if (query.isBlank()) return emptyMap()
        val params = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isNotBlank()) {
                val key = decode(pair.substringBefore('='))
                if (key.isNotBlank()) {
                    params[key] = decode(pair.substringAfter('=', ""))
                }
            }
        }
        return params
    }

    private suspend fun post(url: String, body: RequestBody): HttpOutcome =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                HttpOutcome(
                    code = response.code,
                    body = response.body?.string().orEmpty(),
                    retryAfter = response.header("Retry-After")
                )
            }
        }

    private fun randomState(): String {
        val bytes = ByteArray(STATE_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, CHARSET)

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, CHARSET) }.getOrDefault(value)

    private fun unsupported(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "browser login is not configured"
    )

    private fun listenerFailed(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "could not open the local callback listener"
    )

    private fun expired(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "browser login timed out"
    )

    private fun malformed(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "unexpected browser login response"
    )

    private fun stateMismatch(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "callback state does not match"
    )

    private fun denied(reason: String): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "browser login rejected: $reason"
    )

    private class HttpOutcome(val code: Int, val body: String, val retryAfter: String?) {
        val successful: Boolean
            get() = code in 200..299

        fun asFailure(): RouteFailure = FailureClassifier.classifyHttp(
            code,
            body.take(ERROR_SNIPPET_LENGTH),
            retryAfter
        )
    }

    companion object {
        const val CLIENT_ID = "client_id"
        const val CLIENT_SECRET = "client_secret"
        const val RESPONSE_TYPE = "response_type"
        const val REDIRECT_URI = "redirect_uri"
        const val SCOPE = "scope"
        const val STATE = "state"
        const val CODE = "code"
        const val ERROR = "error"
        const val GRANT_TYPE = "grant_type"
        const val AUTHORIZATION_CODE_GRANT = "authorization_code"
        const val DEFAULT_TIMEOUT_MILLIS = 300_000L
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val DEFAULT_REDIRECT_PATH = "/callback"
        private const val BACKLOG = 1
        private const val ACCEPT_POLL_MILLIS = 500
        private const val STATE_BYTES = 16
        private const val CHARSET = "UTF-8"
        private const val ERROR_SNIPPET_LENGTH = 512
    }
}
