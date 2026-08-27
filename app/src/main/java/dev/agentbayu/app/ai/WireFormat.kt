package dev.agentbayu.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WireFormat {
    @SerialName("openai")
    OPENAI,

    @SerialName("anthropic")
    ANTHROPIC,

    @SerialName("gemini")
    GEMINI
}

@Serializable
enum class AuthType {
    @SerialName("apikey")
    API_KEY,

    @SerialName("optional")
    OPTIONAL,

    @SerialName("none")
    NONE
}

@Serializable
enum class AuthHeader {
    @SerialName("bearer")
    BEARER,

    @SerialName("x-api-key")
    X_API_KEY,

    @SerialName("x-goog-api-key")
    X_GOOG_API_KEY
}

@Serializable
enum class ProviderTier {
    @SerialName("subscription")
    SUBSCRIPTION,

    @SerialName("api_key")
    API_KEY,

    @SerialName("cheap")
    CHEAP,

    @SerialName("free")
    FREE;

    val order: Int
        get() = when (this) {
            SUBSCRIPTION -> 0
            API_KEY -> 1
            CHEAP -> 2
            FREE -> 3
        }
}
