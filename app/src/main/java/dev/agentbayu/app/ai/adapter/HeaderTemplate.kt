package dev.agentbayu.app.ai.adapter

import java.security.MessageDigest
import java.util.UUID

private const val HEX_DIGITS = "0123456789abcdef"
private const val TOKEN_BYTES = 16
private const val SESSION_TOKEN = "{session}"
private const val REQUEST_TOKEN = "{request}"
private const val UUID_TOKEN = "{uuid}"
private const val SESSION_SEED_PREFIX = "header:session:"

internal fun hexOf(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size * 2)
    bytes.forEach { byte ->
        val value = byte.toInt() and 0xff
        out.append(HEX_DIGITS[value shr 4])
        out.append(HEX_DIGITS[value and 0x0f])
    }
    return out.toString()
}

internal fun sha256(text: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray())

internal fun stableHexToken(seed: String): String = hexOf(sha256(seed).copyOf(TOKEN_BYTES))

internal fun randomHexToken(): String =
    hexOf(sha256(UUID.randomUUID().toString()).copyOf(TOKEN_BYTES))

internal class HeaderTokens(private val connectionId: String) {

    private val session by lazy { stableHexToken(SESSION_SEED_PREFIX + connectionId) }
    private val request by lazy { randomHexToken() }
    private val uuid by lazy { UUID.randomUUID().toString() }

    fun expand(value: String): String {
        if (!value.contains('{')) return value
        return value
            .replace(SESSION_TOKEN, session)
            .replace(REQUEST_TOKEN, request)
            .replace(UUID_TOKEN, uuid)
    }
}
