package dev.agentbayu.app.ai.tools

import java.io.IOException
import java.time.Instant
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

internal class BingSearchBackend(
    client: OkHttpClient,
    private val newsEndpoint: String = NEWS_ENDPOINT,
    private val webEndpoint: String = WEB_ENDPOINT
) : SearchBackend {

    private val client = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override val name: String = SOURCE

    override suspend fun search(query: SearchQuery): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val failure = StringBuilder()
            val news = if (query.mode == SearchMode.WEB) {
                emptyList()
            } else {
                when (val feed = load(newsEndpoint, query.text, intervalOf(query.freshness))) {
                    is Feed.Loaded -> parseSearchFeed(feed.body, MAX_FETCH, DateKind.PUBLISHED)
                    is Feed.Failed -> {
                        failure.append(feed.reason)
                        emptyList()
                    }
                }
            }
            val web = if (query.mode == SearchMode.NEWS || news.size >= query.limit) {
                emptyList()
            } else {
                when (val feed = load(webEndpoint, query.text, null)) {
                    is Feed.Loaded -> parseSearchFeed(feed.body, MAX_FETCH, DateKind.INDEXED)
                    is Feed.Failed -> {
                        if (failure.isEmpty()) failure.append(feed.reason)
                        emptyList()
                    }
                }
            }
            if (news.isEmpty() && web.isEmpty() && failure.isNotEmpty()) {
                throw SearchUnavailable(failure.toString())
            }
            trimResults(newestFirst(news) + newestFirst(web), query.limit)
        }

    private fun load(endpoint: String, text: String, interval: String?): Feed {
        val url = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("q", text)
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
        const val SOURCE = "Bing"
        const val NEWS_ENDPOINT = "https://www.bing.com/news/search"
        const val WEB_ENDPOINT = "https://www.bing.com/search"
        const val MAX_FETCH = 8
        const val CALL_TIMEOUT_SECONDS = 20L
        const val REFUSED = "The search engine refused the request with status "
        const val UNREACHABLE = "Cannot reach the search engine right now"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

private sealed interface Feed {
    class Loaded(val body: String) : Feed
    class Failed(val reason: String) : Feed
}

private val ITEM_PATTERN = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

private val ENTITY_PATTERN = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")

private val TAG_PATTERN = Regex("</?[a-zA-Z][^>]*>")

private val FEED_WHITESPACE = Regex("\\s+")

private const val MAX_SUMMARY_CHARS = 300

internal fun parseSearchFeed(feed: String, limit: Int, dateKind: DateKind): List<SearchResult> =
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
                    summary = tidyFeed(rawTagOf(item, "description")),
                    publishedAt = epochOf(unescape(rawTagOf(item, "pubDate"))),
                    source = "Bing",
                    dateKind = dateKind
                )
            }
        }
        .take(limit)
        .toList()

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

private fun tidyFeed(raw: String): String {
    if (raw.isEmpty()) return raw
    val stripped = TAG_PATTERN.replace(raw, "")
    val text = FEED_WHITESPACE.replace(unescape(stripped), " ").trim()
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

private fun intervalOf(freshness: Freshness): String? = when (freshness) {
    Freshness.DAY -> "1"
    Freshness.WEEK -> "7"
    Freshness.MONTH -> "31"
    Freshness.ANY -> null
}
