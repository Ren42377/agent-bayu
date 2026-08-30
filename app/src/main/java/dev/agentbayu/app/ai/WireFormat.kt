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
enum class AuthKind {
    @SerialName("none")
    NONE,

    @SerialName("apikey")
    API_KEY,

    @SerialName("oauth_device")
    OAUTH_DEVICE,

    @SerialName("oauth_pkce")
    OAUTH_PKCE;

    val isOAuth: Boolean
        get() = this == OAUTH_DEVICE || this == OAUTH_PKCE
}

@Serializable
enum class RiskLevel {
    @SerialName("none")
    NONE,

    @SerialName("tos_gray")
    TOS_GRAY,

    @SerialName("fragile")
    FRAGILE
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
            FREE -> 0
            SUBSCRIPTION -> 1
            CHEAP -> 2
            API_KEY -> 3
        }
}
