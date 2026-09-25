package dev.agentbayu.app.ai

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable

@Serializable
data class QuotaWindow(
    val id: String,
    val used: Double? = null,
    val limit: Double? = null,
    val resetAtMillis: Long? = null
) {

    val percentUsed: Double?
        get() {
            val total = limit?.takeIf { it > 0.0 }
            if (total != null && used != null) return ((used / total) * 100.0).coerceIn(0.0, 100.0)
            return used?.takeIf { it in 0.0..100.0 }
        }
}

@Serializable
data class QuotaSnapshot(
    val windows: List<QuotaWindow> = emptyList(),
    val updatedAtMillis: Long = 0L
)

object QuotaParser {

    fun parse(headers: Map<String, String>): QuotaSnapshot? {
        if (headers.isEmpty()) return null
        val lower = HashMap<String, String>(headers.size * 2)
        headers.forEach { (key, value) -> lower[key.lowercase()] = value }
        val windows = WINDOW_SPECS.mapNotNull { spec -> window(lower, spec) }
        if (windows.isEmpty()) return null
        return QuotaSnapshot(windows = windows)
    }

    fun resetAtMillis(raw: String?): Long? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val numeric = trimmed.toLongOrNull() ?: trimmed.toDoubleOrNull()?.toLong()
        if (numeric != null) {
            return if (numeric > MILLIS_THRESHOLD) numeric else numeric * SECOND_IN_MILLIS
        }
        return try {
            Instant.parse(trimmed).toEpochMilli()
        } catch (error: DateTimeParseException) {
            try {
                OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
            } catch (fallback: DateTimeParseException) {
                null
            }
        }
    }

    private fun window(lower: Map<String, String>, spec: WindowSpec): QuotaWindow? {
        val used = lower[spec.usageHeader]?.trim()?.toDoubleOrNull()
        val limit = lower[spec.limitHeader]?.trim()?.toDoubleOrNull()
        val resetAt = resetAtMillis(lower[spec.resetHeader])
        if (used == null && limit == null && resetAt == null) return null
        return QuotaWindow(id = spec.id, used = used, limit = limit, resetAtMillis = resetAt)
    }

    private const val SECOND_IN_MILLIS = 1_000L
    private const val MILLIS_THRESHOLD = 100_000_000_000L

    private val WINDOW_SPECS = listOf(
        WindowSpec(
            id = WINDOW_5H,
            usageHeader = "x-codex-5h-usage",
            limitHeader = "x-codex-5h-limit",
            resetHeader = "x-codex-5h-reset-at"
        ),
        WindowSpec(
            id = WINDOW_7D,
            usageHeader = "x-codex-7d-usage",
            limitHeader = "x-codex-7d-limit",
            resetHeader = "x-codex-7d-reset-at"
        )
    )

    private class WindowSpec(
        val id: String,
        val usageHeader: String,
        val limitHeader: String,
        val resetHeader: String
    )

    const val WINDOW_5H = "5h"
    const val WINDOW_7D = "7d"
}
