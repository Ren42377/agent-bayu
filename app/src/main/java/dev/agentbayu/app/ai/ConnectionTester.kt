package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.ChatRequest
import dev.agentbayu.app.ai.adapter.ChatRole
import dev.agentbayu.app.ai.adapter.ChatTurn
import dev.agentbayu.app.ai.adapter.WireEvent
import dev.agentbayu.app.ai.adapter.applyAuth
import dev.agentbayu.app.ai.adapter.applyExtraHeaders
import dev.agentbayu.app.ai.adapter.arrayField
import dev.agentbayu.app.ai.adapter.joinUrl
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ConnectionTestResult {
    data class Success(val latencyMillis: Long, val model: String) : ConnectionTestResult

    data class Failure(val failure: RouteFailure) : ConnectionTestResult
}

sealed interface ModelFetchResult {
    data class Success(val models: List<String>) : ModelFetchResult

    data class Failure(val failure: RouteFailure) : ModelFetchResult
}

class ConnectionTester(
    private val client: OkHttpClient,
    private val catalog: ProviderCatalog,
    private val keySource: KeySource,
    private val adapters: Map<WireFormat, ChatAdapter>,
    private val clock: Clock = RealClock
) {

    suspend fun test(connection: Connection, apiKey: String? = null): ConnectionTestResult {
        val candidate = candidateOf(connection)
            ?: return ConnectionTestResult.Failure(unknownProvider())
        val adapter = adapters[candidate.provider.wireFormat]
            ?: return ConnectionTestResult.Failure(unknownProvider())

        val request = ChatRequest(
            turns = listOf(ChatTurn(ChatRole.USER, PROBE_PROMPT)),
            maxOutputTokens = candidate.provider.clampOutputTokens(PROBE_MAX_TOKENS),
            temperature = null
        )
        val startedAt = clock.nowMillis()
        val event = adapter.stream(candidate, keyFor(candidate, apiKey), request)
            .filter { it is WireEvent.Delta || it is WireEvent.Failure }
            .firstOrNull()

        return when (event) {
            is WireEvent.Delta -> ConnectionTestResult.Success(
                latencyMillis = clock.nowMillis() - startedAt,
                model = candidate.model.id
            )

            is WireEvent.Failure -> ConnectionTestResult.Failure(event.failure)
            else -> ConnectionTestResult.Failure(
                RouteFailure(kind = FailureKind.RETRYABLE, message = "no content")
            )
        }
    }

    suspend fun fetchModels(
        connection: Connection,
        apiKey: String? = null
    ): ModelFetchResult = withContext(Dispatchers.IO) {
        val candidate = candidateOf(connection)
            ?: return@withContext ModelFetchResult.Failure(unknownProvider())
        val path = candidate.provider.modelsPath
            ?: return@withContext ModelFetchResult.Failure(unknownProvider())

        val request = Request.Builder()
            .url(joinUrl(candidate.baseUrl, path))
            .get()
            .applyAuth(candidate, keyFor(candidate, apiKey))
            .applyExtraHeaders(candidate)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ModelFetchResult.Failure(
                        FailureClassifier.classifyHttp(
                            response.code,
                            body.take(ERROR_SNIPPET_LENGTH),
                            response.header("Retry-After")
                        )
                    )
                }
                ModelFetchResult.Success(parseModels(body))
            }
        } catch (error: IOException) {
            ModelFetchResult.Failure(FailureClassifier.classifyError(error))
        }
    }

    private fun parseModels(body: String): List<String> {
        val root = parseJsonObject(body) ?: return emptyList()
        val fromData = root.arrayField("data")?.mapNotNull { (it as? JsonObject)?.stringField("id") }
        if (!fromData.isNullOrEmpty()) return fromData.sorted()
        val fromModels = root.arrayField("models")?.mapNotNull { entry ->
            val node = entry as? JsonObject ?: return@mapNotNull null
            val name = node.stringField("name") ?: node.stringField("id")
            name?.removePrefix(GEMINI_MODEL_PREFIX)
        }
        return fromModels?.sorted().orEmpty()
    }

    private fun candidateOf(connection: Connection): Candidate? {
        val provider = catalog.find(connection.providerId) ?: return null
        return Candidate(connection, provider, provider.modelOrFallback(connection.model))
    }

    private fun keyFor(candidate: Candidate, apiKey: String?): String? =
        apiKey?.takeIf { it.isNotBlank() } ?: keySource.secretFor(candidate)

    private fun unknownProvider(): RouteFailure =
        RouteFailure(kind = FailureKind.TERMINAL, message = "unsupported provider")

    companion object {
        const val PROBE_PROMPT = "ping"
        const val PROBE_MAX_TOKENS = 16
        const val GEMINI_MODEL_PREFIX = "models/"
        const val ERROR_SNIPPET_LENGTH = 512
    }
}
