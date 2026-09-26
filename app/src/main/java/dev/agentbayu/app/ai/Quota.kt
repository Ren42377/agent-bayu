package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.arrayField
import dev.agentbayu.app.ai.adapter.doubleField
import dev.agentbayu.app.ai.adapter.intField
import dev.agentbayu.app.ai.adapter.objectField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class QuotaWindow(
    val id: String,
    val poolId: String? = null,
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

    fun parseUsage(body: String, nowMillis: Long): QuotaSnapshot? {
        val root = parseJsonObject(body) ?: return null
        antigravityWindows(root, nowMillis)?.let { windows ->
            return QuotaSnapshot(windows = windows)
        }
        codexWindows(root, nowMillis)?.let { windows ->
            return QuotaSnapshot(windows = windows)
        }
        return null
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

    private fun antigravityWindows(root: JsonObject, nowMillis: Long): List<QuotaWindow>? {
        val groups = root.arrayField(GROUPS)
            ?: root.objectField(RESPONSE_ENVELOPE)?.arrayField(GROUPS)
            ?: return null
        val windows = ArrayList<QuotaWindow>()
        groups.forEach { groupElement ->
            val group = groupElement as? JsonObject ?: return@forEach
            group.arrayField(BUCKETS)?.forEach { bucketElement ->
                val bucket = bucketElement as? JsonObject ?: return@forEach
                antigravityBucket(bucket, nowMillis)?.let { windows += it }
            }
        }
        return windows.takeIf { it.isNotEmpty() }
    }

    private fun antigravityBucket(bucket: JsonObject, nowMillis: Long): QuotaWindow? {
        val bucketId = bucket.stringField(BUCKET_ID) ?: return null
        val spec = ANTIGRAVITY_BUCKETS[bucketId] ?: return null
        val remaining = bucket.doubleField(REMAINING_FRACTION)
            ?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?: return null
        return QuotaWindow(
            id = spec.second,
            poolId = spec.first,
            used = (1.0 - remaining) * 100.0,
            limit = FULL_LIMIT,
            resetAtMillis = resetTimeMillis(bucket.stringField(RESET_TIME))
        )
    }

    private fun codexWindows(root: JsonObject, nowMillis: Long): List<QuotaWindow>? {
        val rateLimit = root.objectField(RATE_LIMIT) ?: root.objectField(RATE_LIMIT_CAMEL)
            ?: return null
        val windows = listOfNotNull(
            codexWindow(rateLimit, PRIMARY_WINDOW, PRIMARY_WINDOW_CAMEL, WINDOW_5H, nowMillis),
            codexWindow(rateLimit, SECONDARY_WINDOW, SECONDARY_WINDOW_CAMEL, WINDOW_7D, nowMillis)
        )
        return windows.takeIf { it.isNotEmpty() }
    }

    private fun codexWindow(
        rateLimit: JsonObject,
        snakeKey: String,
        camelKey: String,
        windowId: String,
        nowMillis: Long
    ): QuotaWindow? {
        val node = rateLimit.objectField(snakeKey) ?: rateLimit.objectField(camelKey) ?: return null
        val used = (node.doubleField(USED_PERCENT) ?: node.doubleField(USED_PERCENT_CAMEL))
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0.0, 100.0)
            ?: return null
        return QuotaWindow(
            id = windowId,
            used = used,
            limit = FULL_LIMIT,
            resetAtMillis = codexReset(node, nowMillis)
        )
    }

    private fun codexReset(node: JsonObject, nowMillis: Long): Long? {
        val absolute = node.stringField(RESET_AT) ?: node.stringField(RESET_AT_CAMEL)
        resetTimeMillis(absolute)?.let { return it }
        val relative = node.intField(RESET_AFTER) ?: node.intField(RESET_AFTER_CAMEL)
        return relative?.takeIf { it > 0 }?.let { nowMillis + it * SECOND_IN_MILLIS }
    }

    private fun resetTimeMillis(raw: String?): Long? = resetAtMillis(raw)?.takeIf { it > 0L }

    private fun window(lower: Map<String, String>, spec: WindowSpec): QuotaWindow? {
        val used = lower[spec.usageHeader]?.trim()?.toDoubleOrNull()
        val limit = lower[spec.limitHeader]?.trim()?.toDoubleOrNull()
        val resetAt = resetAtMillis(lower[spec.resetHeader])
        if (used == null && limit == null && resetAt == null) return null
        return QuotaWindow(id = spec.id, used = used, limit = limit, resetAtMillis = resetAt)
    }

    private const val SECOND_IN_MILLIS = 1_000L
    private const val MILLIS_THRESHOLD = 100_000_000_000L
    private const val FULL_LIMIT = 100.0
    private const val GROUPS = "groups"
    private const val RESPONSE_ENVELOPE = "response"
    private const val BUCKETS = "buckets"
    private const val BUCKET_ID = "bucketId"
    private const val REMAINING_FRACTION = "remainingFraction"
    private const val RESET_TIME = "resetTime"
    private const val RATE_LIMIT = "rate_limit"
    private const val RATE_LIMIT_CAMEL = "rateLimit"
    private const val PRIMARY_WINDOW = "primary_window"
    private const val PRIMARY_WINDOW_CAMEL = "primaryWindow"
    private const val SECONDARY_WINDOW = "secondary_window"
    private const val SECONDARY_WINDOW_CAMEL = "secondaryWindow"
    private const val USED_PERCENT = "used_percent"
    private const val USED_PERCENT_CAMEL = "usedPercent"
    private const val RESET_AT = "reset_at"
    private const val RESET_AT_CAMEL = "resetAt"
    private const val RESET_AFTER = "reset_after_seconds"
    private const val RESET_AFTER_CAMEL = "resetAfterSeconds"

    private val ANTIGRAVITY_BUCKETS = mapOf(
        "gemini-5h" to (POOL_GEMINI to WINDOW_5H),
        "gemini-weekly" to (POOL_GEMINI to WINDOW_7D),
        "3p-5h" to (POOL_THIRD_PARTY to WINDOW_5H),
        "3p-weekly" to (POOL_THIRD_PARTY to WINDOW_7D)
    )

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
    const val POOL_GEMINI = "gemini"
    const val POOL_THIRD_PARTY = "3p"
}
