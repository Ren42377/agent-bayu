package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.util.Base64
import kotlinx.serialization.json.JsonObject

object JwtClaims {

    fun payload(token: String): JsonObject? {
        val parts = token.split('.')
        if (parts.size < MIN_PARTS) return null
        val decoded = decodeSegment(parts[1]) ?: return null
        return parseJsonObject(decoded)
    }

    fun claim(token: String, claim: String, field: String?): String? {
        val payload = payload(token) ?: return null
        if (field.isNullOrBlank()) return payload.stringField(claim)
        val nested = payload[claim] as? JsonObject ?: return null
        return nested.stringField(field)
    }

    private fun decodeSegment(segment: String): String? {
        if (segment.isEmpty()) return null
        return try {
            String(Base64.getUrlDecoder().decode(segment.trimEnd('=')), Charsets.UTF_8)
        } catch (error: IllegalArgumentException) {
            null
        }
    }

    private const val MIN_PARTS = 2
}
