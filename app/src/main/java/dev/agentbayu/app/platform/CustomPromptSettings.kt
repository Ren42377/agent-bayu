package dev.agentbayu.app.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CustomPromptSettings(
    private val storage: EncryptedStorage
) {

    private val customPromptState = MutableStateFlow(
        normalize(storage.read(FILE_NAME).orEmpty())
    )

    val customPrompt: StateFlow<String> = customPromptState.asStateFlow()

    fun setCustomPrompt(value: String) = synchronized(this) {
        val normalized = normalize(value)
        if (normalized == customPromptState.value) return
        if (normalized.isEmpty()) {
            storage.delete(FILE_NAME)
        } else {
            storage.write(FILE_NAME, normalized)
        }
        customPromptState.value = normalized
    }

    companion object {
        const val FILE_NAME = "custom-prompt.bin"

        fun normalize(value: String): String = value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
            .take(MAX_PROMPT_CHARS)

        const val MAX_PROMPT_CHARS = 16_000
    }
}
