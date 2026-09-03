package dev.agentbayu.app.ai.adapter

data class ChatImage(
    val mimeType: String,
    val data: String
) {
    val dataUrl: String
        get() = "data:" + mimeType + ";base64," + data

    companion object {
        const val TOKEN_COST = 1_200
    }
}
