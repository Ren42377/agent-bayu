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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface TokenRefreshResult {
    data class Success(val tokens: Credential.OAuthTokens) : TokenRefreshResult

    data class Failure(val failure: RouteFailure) : TokenRefreshResult
}

class TokenRefresher(
    private val client: OkHttpClient,
    private val clock: Clock = RealClock
) {

    suspend fun refresh(
        config: OAuthConfig,
        tokens: Credential.OAuthTokens
    ): TokenRefreshResult = withContext(Dispatchers.IO) {
        val refreshToken = tokens.refreshToken?.takeIf { it.isNotBlank() }
            ?: return@withContext TokenRefreshResult.Failure(missingRefreshToken())

        val form = FormBody.Builder()
            .add(GRANT_TYPE, REFRESH_TOKEN_GRANT)
            .add(CLIENT_ID, config.clientId)
            .add(REFRESH_TOKEN, refreshToken)
            .apply {
                config.clientSecret?.let { secret -> add(CLIENT_SECRET, secret) }
            }
            .build()
        val request = Request.Builder()
            .url(config.tokenUrl)
            .header("Accept", "application/json")
            .post(form)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext TokenRefreshResult.Failure(
                        FailureClassifier.classifyHttp(
                            response.code,
                            body.take(ERROR_SNIPPET_LENGTH),
                            response.header("Retry-After")
                        )
                    )
                }
                val refreshed = readTokens(body, config, tokens, clock.nowMillis())
                    ?: return@withContext TokenRefreshResult.Failure(malformedResponse())
                TokenRefreshResult.Success(refreshed)
            }
        } catch (error: IOException) {
            TokenRefreshResult.Failure(FailureClassifier.classifyError(error))
        }
    }

    private fun missingRefreshToken(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "no refresh token"
    )

    private fun malformedResponse(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "token response without an access token"
    )

    companion object {
        const val GRANT_TYPE = "grant_type"
        const val CLIENT_ID = "client_id"
        const val CLIENT_SECRET = "client_secret"
        const val REFRESH_TOKEN = "refresh_token"
        const val REFRESH_TOKEN_GRANT = "refresh_token"
        const val ERROR_SNIPPET_LENGTH = 512
    }
}

internal fun readTokens(
    body: String,
    config: OAuthConfig,
    previous: Credential.OAuthTokens?,
    nowMillis: Long
): Credential.OAuthTokens? {
    val root = parseJsonObject(body) ?: return null
    val accessToken = root.stringField("access_token")?.takeIf { it.isNotBlank() } ?: return null
    val refreshToken = root.stringField("refresh_token")?.takeIf { it.isNotBlank() }
        ?: previous?.refreshToken
    val expiresInSeconds = root.intField("expires_in")?.takeIf { it > 0 }
    val expiresAtMillis = expiresInSeconds?.let { nowMillis + it * MILLIS_PER_SECOND } ?: 0L
    val extras = HashMap(previous?.extras.orEmpty())
    val claim = config.accountClaim
    val field = config.accountField
    val idToken = root.stringField("id_token")
    if (idToken != null && !claim.isNullOrBlank() && !field.isNullOrBlank()) {
        JwtClaims.claim(idToken, claim, field)?.let { value -> extras[field] = value }
    }
    return Credential.OAuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAtMillis,
        extras = extras
    )
}

private const val MILLIS_PER_SECOND = 1_000L
