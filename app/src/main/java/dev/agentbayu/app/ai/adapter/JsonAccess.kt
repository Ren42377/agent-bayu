package dev.agentbayu.app.ai.adapter

import dev.agentbayu.app.ai.AuthHeader
import dev.agentbayu.app.ai.Candidate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Request

internal val wireJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun parseJsonObject(raw: String): JsonObject? {
    val element: JsonElement = try {
        wireJson.parseToJsonElement(raw)
    } catch (error: IllegalArgumentException) {
        return null
    }
    return element as? JsonObject
}

internal fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intField(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.objectField(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayField(key: String): JsonArray? = this[key] as? JsonArray

internal fun Request.Builder.applyAuth(candidate: Candidate, apiKey: String?): Request.Builder {
    val key = apiKey?.takeIf { it.isNotBlank() } ?: return this
    when (candidate.provider.authHeader) {
        AuthHeader.BEARER -> {
            val prefix = candidate.provider.authPrefix ?: "Bearer"
            header("Authorization", prefix + " " + key)
        }

        AuthHeader.X_API_KEY -> header("x-api-key", key)
        AuthHeader.X_GOOG_API_KEY -> header("x-goog-api-key", key)
    }
    return this
}

internal fun Request.Builder.applyExtraHeaders(candidate: Candidate): Request.Builder {
    candidate.provider.extraHeaders.forEach { (name, value) -> header(name, value) }
    return this
}

internal fun joinUrl(baseUrl: String, path: String): String =
    baseUrl.trimEnd('/') + "/" + path.trimStart('/')
