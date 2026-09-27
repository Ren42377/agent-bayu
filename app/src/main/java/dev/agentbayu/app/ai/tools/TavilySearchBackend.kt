package dev.agentbayu.app.ai.tools

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class TavilySearchBackend(
    client: OkHttpClient,
    private val endpoint: String = ENDPOINT,
    private val apiKey: String? = null
) : SearchBackend {

    private val client = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override val name: String = TAVILY_SOURCE

    override suspend fun search(query: SearchQuery): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("query", query.text)
                put("max_results", query.limit)
                put("search_depth", DEPTH)
                put("include_published_date", true)
                put("topic", if (query.mode == SearchMode.NEWS) TOPIC_NEWS else TOPIC_GENERAL)
                timeRangeOf(query.freshness)?.let { put("time_range", it) }
            }.toString()
            val request = Request.Builder()
                .url(endpoint)
                .header("Content-Type", JSON_TYPE)
                .header("Accept", JSON_TYPE)
                .apply {
                    if (apiKey.isNullOrBlank()) {
                        header("X-Tavily-Access-Mode", KEYLESS)
                    } else {
                        header("Authorization", "Bearer " + apiKey)
                    }
                }
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            val payload = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw SearchUnavailable(REFUSED + response.code)
                    }
                    response.body?.string().orEmpty()
                }
            } catch (error: IOException) {
                throw SearchUnavailable(UNREACHABLE)
            }
            trimResults(parseTavilyResponse(payload), query.limit)
        }

    private companion object {
        const val ENDPOINT = "https://api.tavily.com/search"
        const val DEPTH = "basic"
        const val TOPIC_NEWS = "news"
        const val TOPIC_GENERAL = "general"
        const val KEYLESS = "keyless"
        const val JSON_TYPE = "application/json"
        const val CALL_TIMEOUT_SECONDS = 25L
        const val REFUSED = "The search service refused the request with status "
        const val UNREACHABLE = "Cannot reach the search service right now"
    }
}

private const val TAVILY_SOURCE = "Tavily"

private const val UNREADABLE = "The search service sent back something that could not be read"

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private val LENIENT_JSON = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private const val MAX_SUMMARY_CHARS = 400

private val DATE_TIME_PATTERNS = listOf(
    DateTimeFormatter.ISO_INSTANT,
    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    DateTimeFormatter.ISO_LOCAL_DATE_TIME
)

internal fun parseTavilyResponse(payload: String): List<SearchResult> {
    val root = try {
        LENIENT_JSON.parseToJsonElement(payload).jsonObject
    } catch (error: IllegalArgumentException) {
        throw SearchUnavailable(UNREADABLE)
    }
    val items = root["results"]?.jsonArray ?: return emptyList()
    return items.mapNotNull { element ->
        val item: JsonObject = element.jsonObject
        val title = item.stringOf("title")
        val link = item.stringOf("url")
        if (title.isEmpty() || link.isEmpty()) {
            null
        } else {
            SearchResult(
                title = title,
                link = link,
                summary = tidyTavily(item.stringOf("content")),
                publishedAt = epochOfTavily(item.stringOf("published_date")),
                source = TAVILY_SOURCE,
                dateKind = DateKind.PUBLISHED
            )
        }
    }
}

private fun JsonObject.stringOf(field: String): String =
    this[field]?.jsonPrimitive?.contentOrNull.orEmpty().trim()

internal fun epochOfTavily(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    DATE_TIME_PATTERNS.forEach { pattern ->
        try {
            return Instant.from(pattern.parse(text)).toEpochMilli()
        } catch (error: DateTimeParseException) {
        }
    }
    return try {
        LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (error: DateTimeParseException) {
        null
    }
}

private fun tidyTavily(raw: String): String {
    if (raw.isEmpty()) return raw
    val flat = TAVILY_WHITESPACE.replace(raw, " ").trim()
    if (flat.length <= MAX_SUMMARY_CHARS) return flat
    return flat.take(MAX_SUMMARY_CHARS).trimEnd() + "..."
}

private val TAVILY_WHITESPACE = Regex("\\s+")

private fun timeRangeOf(freshness: Freshness): String? = when (freshness) {
    Freshness.DAY -> "day"
    Freshness.WEEK -> "week"
    Freshness.MONTH -> "month"
    Freshness.ANY -> null
}
