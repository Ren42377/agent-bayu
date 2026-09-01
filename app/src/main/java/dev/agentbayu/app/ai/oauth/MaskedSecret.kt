package dev.agentbayu.app.ai.oauth

import java.util.Base64

internal fun unmaskSecret(value: String): String? {
    val decoded = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
    if (decoded.isEmpty()) {
        return null
    }
    val mask = SECRET_MASK.toByteArray(Charsets.ISO_8859_1)
    val plain = ByteArray(decoded.size) { index ->
        (decoded[index].toInt() xor mask[index % mask.size].toInt()).toByte()
    }
    return String(plain, Charsets.ISO_8859_1)
}

private const val SECRET_MASK = "omniroute-public-v1"
