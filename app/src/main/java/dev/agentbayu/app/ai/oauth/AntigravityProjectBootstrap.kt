package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.FailureClassifier
import dev.agentbayu.app.ai.FailureKind
import dev.agentbayu.app.ai.RouteFailure
import dev.agentbayu.app.ai.adapter.arrayField
import dev.agentbayu.app.ai.adapter.booleanField
import dev.agentbayu.app.ai.adapter.joinUrl
import dev.agentbayu.app.ai.adapter.objectField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ProjectBootstrapResult {
    data class Success(val projectId: String) : ProjectBootstrapResult

    data class Failure(val failure: RouteFailure) : ProjectBootstrapResult
}

class AntigravityProjectBootstrap(private val client: OkHttpClient) {

    suspend fun resolve(
        baseUrl: String,
        accessToken: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): ProjectBootstrapResult {
        val metadata = buildJsonObject {
            put(IDE_TYPE, IDE_TYPE_VALUE)
            put(PLATFORM, PLATFORM_VALUE)
            put(PLUGIN_TYPE, PLUGIN_TYPE_VALUE)
        }
        val loaded = post(
            url = joinUrl(baseUrl, LOAD_CODE_ASSIST_PATH),
            accessToken = accessToken,
            extraHeaders = extraHeaders,
            payload = buildJsonObject { put(METADATA, metadata) }
        )
        val loadedRoot = when (loaded) {
            is HttpResult.Failure -> return ProjectBootstrapResult.Failure(loaded.failure)
            is HttpResult.Success -> parseJsonObject(loaded.body)
                ?: return ProjectBootstrapResult.Failure(malformed())
        }
        projectIdOf(loadedRoot)?.let { return ProjectBootstrapResult.Success(it) }
        return onboard(baseUrl, accessToken, extraHeaders, metadata, tierIdOf(loadedRoot))
    }

    private suspend fun onboard(
        baseUrl: String,
        accessToken: String,
        extraHeaders: Map<String, String>,
        metadata: JsonObject,
        tierId: String
    ): ProjectBootstrapResult {
        val payload = buildJsonObject {
            put(TIER_ID, tierId)
            put(METADATA, metadata)
        }
        val url = joinUrl(baseUrl, ONBOARD_USER_PATH)
        var attempt = 0
        while (attempt < ONBOARD_MAX_ATTEMPTS) {
            attempt += 1
            val outcome = post(url, accessToken, extraHeaders, payload)
            val root = when (outcome) {
                is HttpResult.Failure -> return ProjectBootstrapResult.Failure(outcome.failure)
                is HttpResult.Success -> parseJsonObject(outcome.body)
                    ?: return ProjectBootstrapResult.Failure(malformed())
            }
            if (root.booleanField(DONE) == false) {
                if (attempt < ONBOARD_MAX_ATTEMPTS) {
                    delay(ONBOARD_POLL_INTERVAL_MILLIS)
                    continue
                }
                return ProjectBootstrapResult.Failure(pending())
            }
            val settled = root.objectField(RESPONSE)?.let { projectIdOf(it) } ?: projectIdOf(root)
            return if (settled != null) {
                ProjectBootstrapResult.Success(settled)
            } else {
                ProjectBootstrapResult.Failure(missingProject())
            }
        }
        return ProjectBootstrapResult.Failure(pending())
    }

    private fun projectIdOf(root: JsonObject): String? {
        val direct = root.stringField(CLOUD_PROJECT)?.takeIf { it.isNotBlank() }
        if (direct != null) return direct
        return root.objectField(CLOUD_PROJECT)?.stringField(ID)?.takeIf { it.isNotBlank() }
    }

    private fun tierIdOf(root: JsonObject): String {
        root.objectField(PAID_TIER)?.stringField(ID)?.takeIf { it.isNotBlank() }
            ?.let { return it }
        root.objectField(CURRENT_TIER)?.stringField(ID)?.takeIf { it.isNotBlank() }
            ?.let { return it }
        root.arrayField(ALLOWED_TIERS)?.forEach { element ->
            val tier = element as? JsonObject ?: return@forEach
            if (tier.booleanField(IS_DEFAULT) == true) {
                tier.stringField(ID)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return DEFAULT_TIER_ID
    }

    private suspend fun post(
        url: String,
        accessToken: String,
        extraHeaders: Map<String, String>,
        payload: JsonObject
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .apply {
                extraHeaders.forEach { (name, value) ->
                    if (name.isNotBlank() && value.isNotBlank()) header(name, value)
                }
            }
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    HttpResult.Success(body)
                } else {
                    HttpResult.Failure(
                        FailureClassifier.classifyHttp(
                            response.code,
                            body.take(ERROR_SNIPPET_LENGTH),
                            response.header("Retry-After")
                        )
                    )
                }
            }
        } catch (error: IOException) {
            HttpResult.Failure(FailureClassifier.classifyError(error))
        }
    }

    private fun malformed(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "unexpected project bootstrap response"
    )

    private fun pending(): RouteFailure = RouteFailure(
        kind = FailureKind.RETRYABLE,
        message = "project setup is still running"
    )

    private fun missingProject(): RouteFailure = RouteFailure(
        kind = FailureKind.TERMINAL,
        message = "account needs a Google Cloud project for Antigravity"
    )

    private sealed interface HttpResult {
        data class Success(val body: String) : HttpResult

        data class Failure(val failure: RouteFailure) : HttpResult
    }

    private companion object {
        const val LOAD_CODE_ASSIST_PATH = "/v1internal:loadCodeAssist"
        const val ONBOARD_USER_PATH = "/v1internal:onboardUser"
        const val METADATA = "metadata"
        const val IDE_TYPE = "ideType"
        const val PLATFORM = "platform"
        const val PLUGIN_TYPE = "pluginType"
        const val IDE_TYPE_VALUE = 9
        const val PLATFORM_VALUE = 2
        const val PLUGIN_TYPE_VALUE = 2
        const val TIER_ID = "tier_id"
        const val DONE = "done"
        const val RESPONSE = "response"
        const val CLOUD_PROJECT = "cloudaicompanionProject"
        const val ID = "id"
        const val PAID_TIER = "paidTier"
        const val CURRENT_TIER = "currentTier"
        const val ALLOWED_TIERS = "allowedTiers"
        const val IS_DEFAULT = "isDefault"
        const val DEFAULT_TIER_ID = "legacy-tier"
        const val ONBOARD_MAX_ATTEMPTS = 5
        const val ONBOARD_POLL_INTERVAL_MILLIS = 2_000L
        const val ERROR_SNIPPET_LENGTH = 512
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
