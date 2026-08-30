package dev.agentbayu.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Credential {

    val secret: String

    @Serializable
    @SerialName("apikey")
    data class ApiKey(val value: String) : Credential {
        override val secret: String
            get() = value
    }

    @Serializable
    @SerialName("oauth")
    data class OAuthTokens(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiresAtMillis: Long = 0L,
        val extras: Map<String, String> = emptyMap()
    ) : Credential {
        override val secret: String
            get() = accessToken

        fun isExpired(nowMillis: Long, marginMillis: Long = REFRESH_MARGIN_MILLIS): Boolean {
            if (expiresAtMillis <= 0L) return false
            return nowMillis + marginMillis >= expiresAtMillis
        }

        companion object {
            const val REFRESH_MARGIN_MILLIS = 60_000L
        }
    }

    companion object {
        const val HINT_LENGTH = 4
        const val HINT_MASK = "****"

        fun hintOf(value: String): String {
            if (value.length <= HINT_LENGTH) return HINT_MASK
            return HINT_MASK + value.takeLast(HINT_LENGTH)
        }
    }
}

@Serializable
data class CredentialFile(
    val version: Int = 2,
    val entries: Map<String, Credential> = emptyMap()
)
