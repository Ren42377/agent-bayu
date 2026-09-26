package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.applyAuth
import dev.agentbayu.app.ai.adapter.applyAuthHeaders
import dev.agentbayu.app.ai.adapter.applyExtraHeaders
import dev.agentbayu.app.ai.adapter.joinUrl
import dev.agentbayu.app.ai.oauth.OAuthConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        return if (provider.wireFormat == WireFormat.ANTIGRAVITY) {
            fetchAntigravity(quotaUrl, candidate, credential, config, connection)
        } else {
            fetchUsage(quotaUrl, candidate, credential, config)
        }
    }

    private suspend fun fetchUsage(
        quotaUrl: String,
        candidate: Candidate,
        credential: WireCredential,
        config: OAuthConfig
    ): QuotaFetchResult {
        val urls = urlsFor(candidate, quotaUrl)
        return withContext(Dispatchers.IO) {
            var lastFailure: RouteFailure? = null
            urls.forEach { url ->
                when (val result = requestUsage(url, candidate, credential, config, null)) {
                    is QuotaFetchResult.Success -> return@withContext result

                    is QuotaFetchResult.Failure -> {
                        if (isDeadToken(result.failure)) return@withContext result
                        lastFailure = result.failure
                    }

                    QuotaFetchResult.Unsupported -> Unit
                }
            }
            QuotaFetchResult.Failure(lastFailure ?: unexpectedResponse())
        }
    }

    private suspend fun fetchAntigravity(
        summaryPath: String,
        candidate: Candidate,
        credential: WireCredential,
        config: OAuthConfig,
        connection: Connection
    ): QuotaFetchResult {
        val projectId = connection.projectId?.takeIf { it.isNotBlank() }
            ?: return QuotaFetchResult.Unsupported
        val hosts = listOf(candidate.baseUrl, candidate.controlBaseUrl).distinct()
        return withContext(Dispatchers.IO) {
            val summary = collectWindows(hosts, summaryPath, projectId, candidate, credential, config)
            if (summary.windows == null && summary.failure?.let { isDeadToken(it) } == true) {
                return@withContext QuotaFetchResult.Failure(summary.failure ?: unexpectedResponse())
            }
            val perModel = collectWindows(
                hosts,
                PER_MODEL_PATH,
                projectId,
                candidate,
                credential,
                config
            )
            val windows = LinkedHashMap<String, QuotaWindow>()
            perModel.windows.orEmpty().forEach { window -> windows[windowKey(window)] = window }
            summary.windows.orEmpty().forEach { window ->
                windows.putIfAbsent(windowKey(window), window)
            }
            if (windows.isEmpty()) {
                val summaryDead = summary.failure?.let { isDeadToken(it) } == true
                val perModelDead = perModel.failure?.let { isDeadToken(it) } == true
                if (summaryDead) {
                    return@withContext QuotaFetchResult.Failure(summary.failure ?: unexpectedResponse())
                }
                if (perModelDead) {
                    return@withContext QuotaFetchResult.Failure(perModel.failure ?: unexpectedResponse())
                }
                return@withContext QuotaFetchResult.Failure(
                    perModel.failure ?: summary.failure ?: unexpectedResponse()
                )
            }
            QuotaFetchResult.Success(QuotaSnapshot(windows = windows.values.toList()))
        }
    }

    private suspend fun collectWindows(
        hosts: List<String>,
        path: String,
        projectId: String,
        candidate: Candidate,
        credential: WireCredential,
        config: OAuthConfig
    ): Collected {
        var lastFailure: RouteFailure? = null
        for (host in hosts) {
            val result = requestUsage(
                joinUrl(host, path),
                candidate,
                credential,
                config,
                projectBody(projectId)
            )
            when (result) {
                is QuotaFetchResult.Success -> return Collected(result.snapshot.windows, null)

                is QuotaFetchResult.Failure -> {
                    if (isDeadToken(result.failure)) return Collected(null, result.failure)
                    lastFailure = result.failure
                }

                QuotaFetchResult.Unsupported -> Unit
            }
        }
        return Collected(null, lastFailure)
    }

    private class Collected(
        val windows: List<QuotaWindow>?,
        val failure: RouteFailure?
    )

    private fun windowKey(window: QuotaWindow): String =
        (window.poolId ?: "") + WINDOW_KEY_SEPARATOR + window.id

    private fun projectBody(projectId: String): JsonObject =
        buildJsonObject { put(PROJECT_FIELD, projectId) }

    private fun urlsFor(candidate: Candidate, quotaUrl: String): List<String> {
        if (quotaUrl.contains(SCHEME_SEPARATOR)) return listOf(quotaUrl)
        return listOf(candidate.baseUrl, candidate.controlBaseUrl)
            .map { joinUrl(it, quotaUrl) }
            .distinct()
    }

    private fun requestUsage(
        url: String,
        candidate: Candidate,
        credential: WireCredential,
        config: OAuthConfig,
        body: JsonObject?
    ): QuotaFetchResult {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (body != null || config.quotaMethod.equals(POST_METHOD, ignoreCase = true)) {
                    header("Content-Type", "application/json")
                    post((body?.toString() ?: EMPTY_JSON_BODY).toRequestBody(JSON_MEDIA_TYPE))
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
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return QuotaFetchResult.Failure(
                        FailureClassifier.classifyHttp(
                            response.code,
                            text.take(ERROR_SNIPPET_LENGTH),
                            response.header("Retry-After")
                        )
                    )
                }
                val snapshot = QuotaParser.parseUsage(text, clock.nowMillis())
                    ?: return QuotaFetchResult.Failure(unexpectedResponse())
                QuotaFetchResult.Success(snapshot)
            }
        } catch (error: IOException) {
            QuotaFetchResult.Failure(FailureClassifier.classifyError(error))
        }
    }

    private fun isDeadToken(failure: RouteFailure): Boolean {
        val status = failure.statusCode ?: return false
        return status in DEAD_TOKEN_STATUSES
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
        const val PROJECT_FIELD = "project"
        const val PER_MODEL_PATH = "/v1internal:retrieveUserQuota"
        const val WINDOW_KEY_SEPARATOR = "|"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val DEAD_TOKEN_STATUSES = setOf(401, 403)
    }
}
