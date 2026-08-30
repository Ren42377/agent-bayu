package dev.agentbayu.app.ai.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class OAuthFlow {
    @SerialName("device_code")
    DEVICE_CODE,

    @SerialName("authorization_code")
    AUTHORIZATION_CODE
}

@Serializable
data class OAuthConfig(
    val flow: OAuthFlow,
    val clientId: String,
    val tokenUrl: String,
    val userCodeUrl: String? = null,
    val pollUrl: String? = null,
    val verificationUrl: String? = null,
    val redirectUri: String? = null,
    val scopes: List<String> = emptyList(),
    val accountClaim: String? = null,
    val accountField: String? = null,
    val accountHeader: String? = null
) {
    val isDeviceCode: Boolean
        get() = flow == OAuthFlow.DEVICE_CODE

    fun headersFor(extras: Map<String, String>): Map<String, String> {
        val header = accountHeader?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val field = accountField?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val value = extras[field]?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return mapOf(header to value)
    }
}
