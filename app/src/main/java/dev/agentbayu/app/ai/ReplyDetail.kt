package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class ReplyDetail(
    val providerId: String,
    val providerLabel: String,
    val model: String,
    val connectionId: String,
    val connectionLabel: String,
    val authKind: AuthKind = AuthKind.API_KEY,
    val firstTokenMillis: Long = 0L,
    val totalMillis: Long = 0L
) {
    val label: String
        get() = providerLabel + " " + model
}
