package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.applyAuth
import dev.agentbayu.app.ai.adapter.applyAuthHeaders
import dev.agentbayu.app.ai.adapter.applyExtraHeaders
import dev.agentbayu.app.ai.adapter.joinUrl
import dev.agentbayu.app.ai.oauth.OAuthConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface QuotaFetchResult {
    data class Success(val snapshot: QuotaSnapshot) : QuotaFetchResult

    data class Failure(val failure: RouteFailure) : QuotaFetchResult

    data object Unsupported : QuotaFetchResult
}

class QuotaFetcher(
    private val client: OkHttpClient,
    private val catalog: ProviderCatalog,
    private val credentials: CredentialProvider,
    private val clock: Clock = RealClock
) {

    suspend fun fetch(connection: Connection): QuotaFetchResult {
        val provider = catalog.find(connection.providerId)
            ?: return QuotaFetchResult.Failure(unknownProvider())
        val config = provider.oauth ?: return QuotaFetchResult.Unsupported
        val quotaUrl = config.quotaUrl?.takeIf { it.isNotBlank() }
            ?: return QuotaFetchResult.Unsupported
        val candidate = Candidate(connection, provider, provider.modelOrFallback(connection.model))
        val credential = credentials.resolve(candidate)
        if (credential.token.isNullOrBlank()) {
            return QuotaFetchResult.Failure(signedOut())
        }
        val urls = urlsFor(candidate, quotaUrl)
        return withContext(Dispatchers.IO) {
            var lastFailure: RouteFailure? = null
            urls.forEach { url ->
                when (val result = request(url, candidate, credential, config, clock.nowMillis())) {
                    is QuotaFetchResult.Success -> return@withContext result

                    is QuotaFetchResult.Failure -> {
                        val status = result.failure.statusCode
                        if (status != null && status in DEAD_TOKEN_STATUSES) {
                            return@withContext result
                        }
                        lastFailure = result.failure
                    }

                    QuotaFetchResult.Unsupported -> Unit
                }
            }
            QuotaFetchResult.Failure(lastFailure ?: unexpectedResponse())
        }
    }

    private fun urlsFor(candidate: Candidate, quotaUrl: String): List<String> {
        if (quotaUrl.contains(SCHEME_SEPARATOR)) return listOf(quotaUrl)
        return listOf(candidate.baseUrl, candidate.controlUrl)
            .map { joinUrl(it, quotaUrl) }
            .distinct()
    }

    private fun request(
        url: String,
        candidate: Candidate,
        credential: WireCredential,
        config: OAuthConfig,
        nowMillis: Long
    ): QuotaFetchResult {
        val post = config.quotaMethod.equals(POST_METHOD, ignoreCase = true)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (post) {
                    header("Content-Type", "application/json")
                    post(EMPTY_JSON_BODY.toRequestBody(JSON_MEDIA_TYPE))
                } else {
                    get()
                }
            }
            .applyAuth(candidate, credential.token)
            .applyExtraHeaders(candidate)
            .applyAuthHeaders(credential.headers)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return QuotaFetchResult.Failure(
                        FailureClassifier.classifyHttp(
                            response.code,
                            body.take(ERROR_SNIPPET_LENGTH),
                            response.header("Retry-After")
                        )
                    )
                }
                val snapshot = QuotaParser.parseUsage(body, nowMillis)
                    ?: return QuotaFetchResult.Failure(unexpectedResponse())
                QuotaFetchResult.Success(snapshot)
            }
        } catch (error: IOException) {
            QuotaFetchResult.Failure(FailureClassifier.classifyError(error))
        }
    }

    private fun unknownProvider(): RouteFailure =
        RouteFailure(kind = FailureKind.TERMINAL, message = "unsupported provider")

    private fun signedOut(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "no stored credential for this account"
    )

    private fun unexpectedResponse(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "unexpected usage response"
    )

    private companion object {
        const val POST_METHOD = "POST"
        const val SCHEME_SEPARATOR = "://"
        const val EMPTY_JSON_BODY = "{}"
        const val ERROR_SNIPPET_LENGTH = 512
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val DEAD_TOKEN_STATUSES = setOf(401, 403)
    }
}
