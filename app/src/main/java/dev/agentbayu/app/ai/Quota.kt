package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.arrayField
import dev.agentbayu.app.ai.adapter.booleanField
import dev.agentbayu.app.ai.adapter.doubleField
import dev.agentbayu.app.ai.adapter.intField
import dev.agentbayu.app.ai.adapter.longField
import dev.agentbayu.app.ai.adapter.objectField
import dev.agentbayu.app.ai.adapter.parseJsonObject
import dev.agentbayu.app.ai.adapter.stringField
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
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
        antigravityWindows(root)?.let { windows ->
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

    private fun antigravityWindows(root: JsonObject): List<QuotaWindow>? {
        val windows = LinkedHashMap<String, QuotaWindow>()
        envelopeNodes(root).forEach { node -> collectModelBuckets(node, windows) }
        envelopeNodes(root).forEach { node -> collectFamilyBuckets(node, windows) }
        return windows.values.toList().takeIf { it.isNotEmpty() }
    }

    private fun envelopeNodes(root: JsonObject): List<JsonObject> = listOfNotNull(
        root,
        root.objectField(QUOTA_SUMMARY),
        root.objectField(RESPONSE_ENVELOPE)
    )

    private fun collectModelBuckets(node: JsonObject, windows: LinkedHashMap<String, QuotaWindow>) {
        when (val buckets = node[BUCKETS]) {
            is JsonArray -> buckets.forEach { element ->
                (element as? JsonObject)?.let { collectModelBucket(it, null, windows) }
            }

            is JsonObject -> buckets.forEach { (key, value) ->
                (value as? JsonObject)?.let { collectModelBucket(it, key, windows) }
            }

            else -> Unit
        }
    }

    private fun collectModelBucket(
        bucket: JsonObject,
        keyHint: String?,
        windows: LinkedHashMap<String, QuotaWindow>
    ) {
        if (bucket.booleanField(DISABLED) == true) return
        val remaining = remainingFractionOf(bucket) ?: return
        val modelId = (bucket.stringField(MODEL_ID) ?: keyHint)?.trim().orEmpty()
        if (modelId.isEmpty()) return
        val pool = poolFromText(modelId) ?: POOL_THIRD_PARTY
        mergeWindow(
            windows,
            QuotaWindow(
                id = WINDOW_5H,
                poolId = pool,
                used = (1.0 - remaining) * 100.0,
                limit = FULL_LIMIT,
                resetAtMillis = resetTimeOf(bucket)
            ),
            keepWorst = true
        )
    }

    private fun collectFamilyBuckets(node: JsonObject, windows: LinkedHashMap<String, QuotaWindow>) {
        val groups = node.arrayField(GROUPS) ?: return
        groups.forEach { groupElement ->
            val group = groupElement as? JsonObject ?: return@forEach
            val groupText = group.stringField(DISPLAY_NAME).orEmpty()
            group.arrayField(BUCKETS)?.forEach { bucketElement ->
                val bucket = bucketElement as? JsonObject ?: return@forEach
                collectFamilyBucket(bucket, groupText, windows)
            }
        }
    }

    private fun collectFamilyBucket(
        bucket: JsonObject,
        groupText: String,
        windows: LinkedHashMap<String, QuotaWindow>
    ) {
        if (bucket.booleanField(DISABLED) == true) return
        val text = (
            bucket.stringField(BUCKET_ID).orEmpty() + " " +
                bucket.stringField(DISPLAY_NAME).orEmpty() + " " + groupText
            ).lowercase()
        val windowId = when {
            text.contains(WEEKLY_TOKEN) -> WINDOW_7D
            text.contains(FIVE_HOUR_TOKEN) || text.contains(FIVE_HOUR_LABEL) -> WINDOW_5H
            else -> return
        }
        val remaining = remainingFractionOf(bucket) ?: return
        val pool = poolFromText(
            bucket.stringField(BUCKET_ID).orEmpty() + " " + bucket.stringField(DISPLAY_NAME).orEmpty()
        ) ?: poolFromText(groupText) ?: POOL_THIRD_PARTY
        mergeWindow(
            windows,
            QuotaWindow(
                id = windowId,
                poolId = pool,
                used = (1.0 - remaining) * 100.0,
                limit = FULL_LIMIT,
                resetAtMillis = resetTimeOf(bucket)
            ),
            keepWorst = false
        )
    }

    private fun remainingFractionOf(bucket: JsonObject): Double? =
        bucket.doubleField(REMAINING_FRACTION)
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.coerceAtMost(1.0)

    private fun poolFromText(text: String): String? =
        if (text.contains(GEMINI_TOKEN, ignoreCase = true)) POOL_GEMINI else null

    private fun mergeWindow(
        windows: LinkedHashMap<String, QuotaWindow>,
        window: QuotaWindow,
        keepWorst: Boolean
    ) {
        val key = (window.poolId ?: "") + WINDOW_KEY_SEPARATOR + window.id
        val existing = windows[key] ?: run {
            windows[key] = window
            return
        }
        if (keepWorst) {
            windows[key] = listOf(existing, window).maxByOrNull { it.used ?: 0.0 } ?: window
        }
    }

    private fun resetTimeOf(node: JsonObject): Long? {
        val raw = RESET_FIELDS.firstNotNullOfOrNull { field -> node.stringField(field) }
            ?: RESET_FIELDS.firstNotNullOfOrNull { field -> node.longField(field) }?.toString()
        return raw?.let { value -> resetAtMillis(value)?.takeIf { it > 0L } }
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
        resetAtMillis(absolute)?.takeIf { it > 0L }?.let { return it }
        val relative = node.intField(RESET_AFTER) ?: node.intField(RESET_AFTER_CAMEL)
        return relative?.takeIf { it > 0 }?.let { nowMillis + it * SECOND_IN_MILLIS }
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
    private const val FULL_LIMIT = 100.0
    private const val GROUPS = "groups"
    private const val BUCKETS = "buckets"
    private const val QUOTA_SUMMARY = "quotaSummary"
    private const val RESPONSE_ENVELOPE = "response"
    private const val MODEL_ID = "modelId"
    private const val BUCKET_ID = "bucketId"
    private const val DISPLAY_NAME = "displayName"
    private const val DISABLED = "disabled"
    private const val REMAINING_FRACTION = "remainingFraction"
    private const val WEEKLY_TOKEN = "weekly"
    private const val FIVE_HOUR_TOKEN = "5h"
    private const val FIVE_HOUR_LABEL = "5-hour"
    private const val GEMINI_TOKEN = "gemini"
    private const val WINDOW_KEY_SEPARATOR = "|"
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
    private const val RESET_TIME = "resetTime"

    private val RESET_FIELDS = listOf(RESET_TIME, RESET_AT, RESET_AT_CAMEL)

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
