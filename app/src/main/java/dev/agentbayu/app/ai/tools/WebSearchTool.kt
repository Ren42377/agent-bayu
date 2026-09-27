package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.RealClock
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSearchTool(
    client: OkHttpClient,
    private val clock: Clock = RealClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val newsEndpoint: String = NEWS_ENDPOINT,
    private val webEndpoint: String = WEB_ENDPOINT
) : ToolHandler {

    private val client = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Search the public web and read back the top results as a title, a link, " +
            "the date, and a short description. Use it for anything newer than your own " +
            "knowledge, for prices, and for facts you would otherwise guess. Search with a few " +
            "keywords, not with a whole question, and put a time range in the query itself when " +
            "the answer is time sensitive, for example a month or a year. The default mode " +
            "looks in news first, where the date is the real publication time, and only fills " +
            "the rest from general pages, where the date is the last time the search engine " +
            "indexed the page and can be years old, so treat an old general page as possibly " +
            "stale rather than as fresh. When sources disagree, prefer the newest one and say " +
            "which date you used. A description is one or two lines and often does not contain " +
            "the answer; when it does not, open the page with read_web_page instead of filling " +
            "the gap yourself, and always name the source you took an answer from.",
        parameters = toolSchema(
            ToolField("query", "string", "A few keywords to look for"),
            ToolField(
                name = "mode",
                type = "string",
                description = "auto looks in news first and then in general pages, news looks " +
                    "only in news and suits prices, events, and anything that changed recently, " +
                    "web looks only in general pages and suits references and technical topics",
                required = false,
                options = listOf(MODE_AUTO, MODE_NEWS, MODE_WEB)
            ),
            ToolField(
                name = "freshness",
                type = "string",
                description = "How recent the news results must be. Ignored in web mode",
                required = false,
                options = listOf(FRESHNESS_ANY, FRESHNESS_DAY, FRESHNESS_WEEK, FRESHNESS_MONTH)
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
        val mode = arguments.text("mode")?.lowercase(Locale.US) ?: MODE_AUTO
        val freshness = arguments.text("freshness")?.lowercase(Locale.US) ?: FRESHNESS_ANY
        val failure = StringBuilder()

        val news: List<SearchResult> = if (mode == MODE_WEB) {
            emptyList()
        } else {
            when (val feed = load(newsEndpoint, query, newsInterval(freshness))) {
                is Feed.Loaded -> parseSearchFeed(feed.body, MAX_LIMIT, SearchSource.NEWS)
                is Feed.Failed -> {
                    failure.append(feed.reason)
                    emptyList()
                }
            }
        }

        val web: List<SearchResult> = if (mode == MODE_NEWS || news.size >= limit) {
            emptyList()
        } else {
            when (val feed = load(webEndpoint, query, null)) {
                is Feed.Loaded -> parseSearchFeed(feed.body, MAX_LIMIT, SearchSource.WEB)
                is Feed.Failed -> {
                    if (failure.isEmpty()) failure.append(feed.reason)
                    emptyList()
                }
            }
        }

        val results = selectResults(news, web, limit)
        if (results.isEmpty()) {
            return@withContext if (failure.isNotEmpty()) {
                call.problem(failure.toString())
            } else {
                call.reply(NO_RESULTS + query)
            }
        }
        call.reply(renderResults(query, results, clock.nowMillis(), zone))
    }

    private fun load(endpoint: String, query: String, interval: String?): Feed {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "rss")
            .apply {
                if (interval != null) {
                    addQueryParameter("qft", "interval=\"" + interval + "\"")
                }
            }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Feed.Loaded(response.body?.string().orEmpty())
                } else {
                    Feed.Failed(REFUSED + response.code)
                }
            }
        } catch (error: IOException) {
            Feed.Failed(UNREACHABLE)
        }
    }

    private companion object {
        const val NAME = "web_search"
        const val NEWS_ENDPOINT = "https://www.bing.com/news/search"
        const val WEB_ENDPOINT = "https://www.bing.com/search"
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 8
        const val CALL_TIMEOUT_SECONDS = 20L
        const val QUERY_REQUIRED = "A query is required"
        const val REFUSED = "The search engine refused the request with status "
        const val UNREACHABLE = "Cannot reach the search engine right now"
        const val NO_RESULTS = "No results for "
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

internal enum class SearchSource { NEWS, WEB }

internal class SearchResult(
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long?,
    val source: SearchSource
)

private sealed interface Feed {
    class Loaded(val body: String) : Feed
    class Failed(val reason: String) : Feed
}

private val STAMP_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm zzz", Locale.US)

private const val MODE_AUTO = "auto"
private const val MODE_NEWS = "news"
private const val MODE_WEB = "web"
private const val FRESHNESS_ANY = "any"
private const val FRESHNESS_DAY = "day"
private const val FRESHNESS_WEEK = "week"
private const val FRESHNESS_MONTH = "month"

private val ITEM_PATTERN = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

private val ENTITY_PATTERN = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")

private val TAG_PATTERN = Regex("</?[a-zA-Z][^>]*>")

private val WHITESPACE_PATTERN = Regex("\\s+")

private const val MAX_SUMMARY_CHARS = 300
private const val MAX_PER_HOST = 2
private const val MILLIS_PER_SECOND = 1_000L
private const val MINUTE = 60L
private const val HOUR = 3_600L
private const val DAY = 86_400L
private const val WEEK = 604_800L
private const val MONTH = 2_592_000L
private const val YEAR = 31_536_000L
private const val DATE_LEGEND =
    "Dates: a news result shows when the article was published. A general page result shows " +
        "only when the search engine last indexed the page, which is not the article date."

internal fun parseSearchFeed(
    feed: String,
    limit: Int,
    source: SearchSource = SearchSource.WEB
): List<SearchResult> =
    ITEM_PATTERN.findAll(feed)
        .map { match -> match.groupValues[1] }
        .mapNotNull { item ->
            val title = unescape(rawTagOf(item, "title"))
            val link = articleUrl(unescape(rawTagOf(item, "link")))
            if (title.isEmpty() || link.isEmpty()) {
                null
            } else {
                SearchResult(
                    title = title,
                    link = link,
                    summary = tidy(rawTagOf(item, "description")),
                    publishedAt = epochOf(unescape(rawTagOf(item, "pubDate"))),
                    source = source
                )
            }
        }
        .take(limit)
        .toList()

internal fun selectResults(
    news: List<SearchResult>,
    web: List<SearchResult>,
    limit: Int
): List<SearchResult> {
    val ordered = newestFirst(news) + newestFirst(web)
    val seenLinks = HashSet<String>()
    val perHost = HashMap<String, Int>()
    val chosen = ArrayList<SearchResult>(limit)
    for (result in ordered) {
        if (chosen.size >= limit) break
        if (!seenLinks.add(dedupKey(result.link))) continue
        val host = hostOf(result.link)
        val used = perHost[host] ?: 0
        if (host.isNotEmpty() && used >= MAX_PER_HOST) continue
        perHost[host] = used + 1
        chosen += result
    }
    return chosen
}

internal fun renderResults(
    query: String,
    results: List<SearchResult>,
    now: Long,
    zone: ZoneId
): String {
    val lines = ArrayList<String>(results.size * 5 + 2)
    lines += "Results for " + query
    if (results.any { it.source == SearchSource.WEB }) lines += DATE_LEGEND
    results.forEachIndexed { index, result ->
        lines += (index + 1).toString() + ". " + result.title
        lines += "   " + result.link
        dateLine(result, now, zone)?.let { lines += "   " + it }
        if (result.summary.isNotEmpty()) lines += "   " + result.summary
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

internal fun articleUrl(link: String): String {
    if (link.isEmpty()) return link
    val url = link.toHttpUrlOrNull() ?: return link
    if (!url.host.endsWith("bing.com")) return link
    return url.queryParameter("url")?.takeIf { it.isNotEmpty() } ?: link
}

internal fun epochOf(pubDate: String): Long? {
    if (pubDate.isEmpty()) return null
    return try {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(pubDate)).toEpochMilli()
    } catch (error: DateTimeParseException) {
        null
    }
}

private fun dateLine(result: SearchResult, now: Long, zone: ZoneId): String? {
    val published = result.publishedAt ?: return null
    val stamp = STAMP_FORMATTER.format(Instant.ofEpochMilli(published).atZone(zone))
    val verb = if (result.source == SearchSource.NEWS) "published" else "indexed"
    return verb + " " + describeAge(now - published) + " (" + stamp + ")"
}

private fun countOf(count: Long, unit: String): String =
    count.toString() + " " + unit + (if (count == 1L) "" else "s") + " ago"

private fun newsInterval(freshness: String): String? = when (freshness) {
    FRESHNESS_DAY -> "1"
    FRESHNESS_WEEK -> "7"
    FRESHNESS_MONTH -> "31"
    else -> null
}

private fun newestFirst(results: List<SearchResult>): List<SearchResult> =
    results.sortedByDescending { it.publishedAt ?: Long.MIN_VALUE }

private fun dedupKey(link: String): String {
    val url = link.toHttpUrlOrNull() ?: return link.lowercase(Locale.US)
    val query = url.encodedQuery?.let { "?" + it }.orEmpty()
    return (url.host + url.encodedPath + query).lowercase(Locale.US)
}

private fun hostOf(link: String): String = link.toHttpUrlOrNull()?.host.orEmpty()

private fun tidy(raw: String): String {
    if (raw.isEmpty()) return raw
    val stripped = TAG_PATTERN.replace(raw, "")
    val text = WHITESPACE_PATTERN.replace(unescape(stripped), " ").trim()
    if (text.length <= MAX_SUMMARY_CHARS) return text
    return text.take(MAX_SUMMARY_CHARS).trimEnd() + "..."
}

private fun rawTagOf(item: String, tag: String): String {
    val open = item.indexOf("<" + tag + ">")
    if (open < 0) return ""
    val start = open + tag.length + 2
    val close = item.indexOf("</" + tag + ">", start)
    if (close < 0) return ""
    return item.substring(start, close)
        .replace("<![CDATA[", "")
        .replace("]]>", "")
        .trim()
}

private fun unescape(raw: String): String {
    if (!raw.contains('&')) return raw
    return ENTITY_PATTERN.replace(raw) { match ->
        when (val name = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            "nbsp" -> " "
            else -> codePointOf(name)?.let { String(Character.toChars(it)) } ?: match.value
        }
    }
}

private fun codePointOf(name: String): Int? = when {
    name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)
    name.startsWith("#") -> name.drop(1).toIntOrNull()
    else -> null
}
