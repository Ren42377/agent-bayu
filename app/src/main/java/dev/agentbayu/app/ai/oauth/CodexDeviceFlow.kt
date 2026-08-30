package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.Credential
import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.adapter.intField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class DeviceCodeStart(
    val deviceAuthId: String,
    val userCode: String,
    val pollIntervalMillis: Long,
    val expiresAtMillis: Long
)

sealed interface DeviceCodeStartResult {
    data class Success(val start: DeviceCodeStart) : DeviceCodeStartResult

    data class Failure(val failure: RouteFailure) : DeviceCodeStartResult
}

sealed interface DeviceCodeResult {
    data class Success(val tokens: Credential.OAuthTokens) : DeviceCodeResult

    data class Failure(val failure: RouteFailure) : DeviceCodeResult
}

class CodexDeviceFlow(
    private val client: OkHttpClient,
    private val clock: Clock = RealClock
) {

    suspend fun start(config: OAuthConfig): DeviceCodeStartResult {
        val url = config.userCodeUrl?.takeIf { it.isNotBlank() }
            ?: return DeviceCodeStartResult.Failure(unsupported())
        val payload = buildJsonObject { put(CLIENT_ID, config.clientId) }
        val outcome = try {
            post(url, jsonBody(payload))
        } catch (error: IOException) {
            return DeviceCodeStartResult.Failure(FailureClassifier.classifyError(error))
        }
        if (!outcome.successful) return DeviceCodeStartResult.Failure(outcome.asFailure())

        val root = parseJsonObject(outcome.body)
            ?: return DeviceCodeStartResult.Failure(malformed())
        val deviceAuthId = root.stringField(DEVICE_AUTH_ID)?.takeIf { it.isNotBlank() }
            ?: return DeviceCodeStartResult.Failure(malformed())
        val userCode = root.stringField(USER_CODE)?.takeIf { it.isNotBlank() }
            ?: return DeviceCodeStartResult.Failure(malformed())
        val now = clock.nowMillis()
        val intervalSeconds = root.intField(INTERVAL)?.takeIf { it > 0 } ?: DEFAULT_INTERVAL_SECONDS
        return DeviceCodeStartResult.Success(
            DeviceCodeStart(
                deviceAuthId = deviceAuthId,
                userCode = userCode,
                pollIntervalMillis = intervalSeconds * MILLIS_PER_SECOND,
                expiresAtMillis = expiryOf(root, now)
            )
        )
    }

    suspend fun awaitAuthorization(config: OAuthConfig, start: DeviceCodeStart): DeviceCodeResult {
        val url = config.pollUrl?.takeIf { it.isNotBlank() }
            ?: return DeviceCodeResult.Failure(unsupported())
        val payload = buildJsonObject {
            put(DEVICE_AUTH_ID, start.deviceAuthId)
            put(USER_CODE, start.userCode)
        }

        while (clock.nowMillis() < start.expiresAtMillis) {
            val outcome = try {
                post(url, jsonBody(payload))
            } catch (error: IOException) {
                return DeviceCodeResult.Failure(FailureClassifier.classifyError(error))
            }
            if (outcome.successful) {
                val root = parseJsonObject(outcome.body) ?: return DeviceCodeResult.Failure(malformed())
                val code = root.stringField(AUTHORIZATION_CODE)?.takeIf { it.isNotBlank() }
                val verifier = root.stringField(CODE_VERIFIER)?.takeIf { it.isNotBlank() }
                if (code != null && verifier != null) return exchange(config, code, verifier)
                if (!isPending(root)) return DeviceCodeResult.Failure(malformed())
            } else if (!outcome.pending) {
                return DeviceCodeResult.Failure(outcome.asFailure())
            }
            delay(start.pollIntervalMillis)
        }
        return DeviceCodeResult.Failure(expired())
    }

    private suspend fun exchange(
        config: OAuthConfig,
        code: String,
        codeVerifier: String
    ): DeviceCodeResult {
        val form = FormBody.Builder()
            .add(GRANT_TYPE, AUTHORIZATION_CODE_GRANT)
            .add(CLIENT_ID, config.clientId)
            .add(CODE, code)
            .add(CODE_VERIFIER, codeVerifier)
            .apply {
                config.redirectUri?.takeIf { it.isNotBlank() }?.let { add(REDIRECT_URI, it) }
            }
            .build()
        val outcome = try {
            post(config.tokenUrl, form)
        } catch (error: IOException) {
            return DeviceCodeResult.Failure(FailureClassifier.classifyError(error))
        }
        if (!outcome.successful) return DeviceCodeResult.Failure(outcome.asFailure())
        val tokens = readTokens(outcome.body, config, null, clock.nowMillis())
            ?: return DeviceCodeResult.Failure(malformed())
        return DeviceCodeResult.Success(tokens)
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

    private fun jsonBody(payload: JsonObject): RequestBody =
        payload.toString().toRequestBody(JSON_MEDIA_TYPE)

    private fun isPending(root: JsonObject): Boolean {
        val error = root.stringField(ERROR)?.lowercase() ?: return true
        return PENDING_ERRORS.any { error.contains(it) }
    }

    private fun expiryOf(root: JsonObject, nowMillis: Long): Long {
        root.stringField(EXPIRES_AT)?.let { raw ->
            instantMillis(raw)?.let { return it }
        }
        val seconds = root.intField(EXPIRES_IN)?.takeIf { it > 0 } ?: DEFAULT_EXPIRY_SECONDS
        return nowMillis + seconds * MILLIS_PER_SECOND
    }

    private fun instantMillis(raw: String): Long? {
        try {
            return Instant.parse(raw).toEpochMilli()
        } catch (error: DateTimeParseException) {
            return try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (fallbackError: DateTimeParseException) {
                null
            }
        }
    }

    private fun unsupported(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "device login is not configured"
    )

    private fun malformed(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "unexpected device login response"
    )

    private fun expired(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "device code expired"
    )

    private class HttpOutcome(val code: Int, val body: String, val retryAfter: String?) {
        val successful: Boolean
            get() = code in 200..299

        val pending: Boolean
            get() = code in PENDING_STATUS

        fun asFailure(): RouteFailure = FailureClassifier.classifyHttp(
            code,
            body.take(ERROR_SNIPPET_LENGTH),
            retryAfter
        )
    }

    companion object {
        const val CLIENT_ID = "client_id"
        const val DEVICE_AUTH_ID = "device_auth_id"
        const val USER_CODE = "user_code"
        const val INTERVAL = "interval"
        const val EXPIRES_AT = "expires_at"
        const val EXPIRES_IN = "expires_in"
        const val AUTHORIZATION_CODE = "authorization_code"
        const val CODE_VERIFIER = "code_verifier"
        const val CODE = "code"
        const val GRANT_TYPE = "grant_type"
        const val REDIRECT_URI = "redirect_uri"
        const val AUTHORIZATION_CODE_GRANT = "authorization_code"
        const val ERROR = "error"
        const val DEFAULT_INTERVAL_SECONDS = 5
        const val DEFAULT_EXPIRY_SECONDS = 600
        const val ERROR_SNIPPET_LENGTH = 512
        private const val MILLIS_PER_SECOND = 1_000L
        private val PENDING_STATUS = setOf(403, 404)
        private val PENDING_ERRORS = listOf("pending", "slow_down")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
