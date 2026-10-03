package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class WebSearchTool internal constructor(
    private val primary: SearchBackend,
    private val fallback: SearchBackend,
    private val clock: Clock,
    private val zone: ZoneId
) : ToolHandler {

    constructor(
        client: OkHttpClient,
        clock: Clock = RealClock,
        zone: ZoneId = ZoneId.systemDefault()
    ) : this(
        primary = TavilySearchBackend(client),
        fallback = BingSearchBackend(client),
        clock = clock,
        zone = zone
    )

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Search the public web and read back the top results as a title, a link, " +
            "the date, and a description. Use it for anything newer than your own knowledge, " +
            "for prices, and for facts you would otherwise guess. Search with a few keywords, " +
            "not with a whole question, and put a time range in the query itself when the " +
            "answer is time sensitive, for example a month or a year. Results come from a " +
            "search service first and fall back to a search engine index when that service is " +
            "unavailable, so each result says which source it came from. A published date is " +
            "when the page was written; an indexed date is only when the search engine last " +
            "saw the page, which can be years old, so treat an indexed date as a sign the page " +
            "may be stale rather than as freshness. When sources disagree, prefer the newest " +
            "one and say which date you used. A description is a short extract and sometimes " +
            "does not contain the answer; when it does not, open the page with read_web_page " +
            "instead of filling the gap yourself, and always name the source you took an " +
            "answer from.",
        parameters = toolSchema(
            ToolField("query", "string", "A few keywords to look for"),
            ToolField(
                name = "mode",
                type = "string",
                description = "auto searches broadly, news searches recent reporting and suits " +
                    "prices, events, and anything that changed recently, web suits references " +
                    "and technical topics",
                required = false,
                options = listOf("auto", "news", "web")
            ),
            ToolField(
                name = "freshness",
                type = "string",
                description = "How recent the results must be. Leave it at any when the answer " +
                    "does not depend on the date",
                required = false,
                options = listOf("any", "day", "week", "month")
            ),
            ToolField(
                name = "limit",
                type = "integer",
                description = "How many results to read back, from 1 to 8",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val query = arguments.text("query")
            ?: return@withContext call.problem(QUERY_REQUIRED)
        val limit = arguments.number("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val request = SearchQuery(
            text = query,
            limit = limit,
            mode = modeOf(arguments.text("mode")),
            freshness = freshnessOf(arguments.text("freshness"))
        )
        val failure = StringBuilder()
        val fromPrimary = attempt(primary, request, failure)
        val fromFallback = if (fromPrimary.isNullOrEmpty()) {
            attempt(fallback, request, failure)
        } else {
            null
        }
        val results = if (!fromPrimary.isNullOrEmpty()) fromPrimary else fromFallback.orEmpty()
        if (results.isEmpty()) {
            val answered = fromPrimary != null || fromFallback != null
            return@withContext if (answered) {
                call.reply(NO_RESULTS + query)
            } else {
                call.problem(failure.toString())
            }
        }
        call.reply(renderResults(query, results, clock.nowMillis(), zone))
    }

    private suspend fun attempt(
        backend: SearchBackend,
        request: SearchQuery,
        failure: StringBuilder
    ): List<SearchResult>? = try {
        backend.search(request)
    } catch (error: SearchUnavailable) {
        if (failure.isNotEmpty()) failure.append(" ")
        failure.append(error.reason)
        null
    }

    private companion object {
        const val NAME = "web_search"
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 8
        const val QUERY_REQUIRED = "A query is required"
        const val NO_RESULTS = "No results for "
    }
}

private val STAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz", Locale.US)

private const val MILLIS_PER_SECOND = 1_000L
private const val MINUTE = 60L
private const val HOUR = 3_600L
private const val DAY = 86_400L
private const val WEEK = 604_800L
private const val MONTH = 2_592_000L
private const val YEAR = 31_536_000L

private const val DATE_LEGEND =
    "Dates: published means when the page was written. indexed means only when the search " +
        "engine last saw the page, which is not the writing date and can be years old."

internal fun modeOf(raw: String?): SearchMode = when (raw?.lowercase(Locale.US)) {
    "news" -> SearchMode.NEWS
    "web" -> SearchMode.WEB
    else -> SearchMode.AUTO
}

internal fun freshnessOf(raw: String?): Freshness = when (raw?.lowercase(Locale.US)) {
    "day" -> Freshness.DAY
    "week" -> Freshness.WEEK
    "month" -> Freshness.MONTH
    else -> Freshness.ANY
}

internal fun renderResults(
    query: String,
    results: List<SearchResult>,
    now: Long,
    zone: ZoneId
): String {
    val lines = ArrayList<String>(results.size * 6 + 2)
    lines += "Results for " + query
    if (results.any { it.dateKind == DateKind.INDEXED }) lines += DATE_LEGEND
    results.forEachIndexed { index, result ->
        lines += (index + 1).toString() + ". " + result.title
        lines += "   " + result.link
        dateLine(result, now, zone)?.let { lines += "   " + it }
        if (result.summary.isNotEmpty()) lines += "   " + result.summary
        lines += "   source " + result.source
    }
    return lines.joinToString("\n")
}

internal fun describeAge(millis: Long): String {
    val seconds = millis / MILLIS_PER_SECOND
    return when {
        seconds < MINUTE -> "just now"
        seconds < HOUR -> countOf(seconds / MINUTE, "minute")
        seconds < DAY -> countOf(seconds / HOUR, "hour")
        seconds < WEEK -> countOf(seconds / DAY, "day")
        seconds < MONTH -> countOf(seconds / WEEK, "week")
        seconds < YEAR -> countOf(seconds / MONTH, "month")
        else -> countOf(seconds / YEAR, "year")
    }
}

private fun dateLine(result: SearchResult, now: Long, zone: ZoneId): String? {
    val published = result.publishedAt ?: return null
    val stamp = STAMP_FORMATTER.format(Instant.ofEpochMilli(published).atZone(zone))
    val verb = if (result.dateKind == DateKind.PUBLISHED) "published" else "indexed"
    return verb + " " + describeAge(now - published) + " (" + stamp + ")"
}

private fun countOf(count: Long, unit: String): String =
    count.toString() + " " + unit + (if (count == 1L) "" else "s") + " ago"
