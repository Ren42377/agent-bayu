package dev.agentbayu.app.ai.tools

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class WebSearchTool(client: OkHttpClient) : ToolHandler {

    private val client = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Search the public web and read back the top results as a title, a link, " +
            "the date the page was last seen, and a short description. Use it for anything " +
            "newer than your own knowledge, for prices, and for facts you would otherwise " +
            "guess. Search with a few keywords, not with a whole question. The descriptions " +
            "are short and sometimes carry page furniture instead of an answer, so say plainly " +
            "when they do not answer the question instead of filling the gap yourself, and " +
            "name the source you took an answer from.",
        parameters = toolSchema(
            ToolField("query", "string", "A few keywords to look for"),
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
            ?: return@withContext call.problem("A query is required")
        val limit = arguments.number("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val url = SEARCH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "rss")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/rss+xml, application/xml, text/xml")
            .build()
        val feed = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext call.problem(
                        "The search engine refused the request with status " + response.code
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            return@withContext call.problem("Cannot reach the search engine right now")
        }
        val results = parseSearchFeed(feed, limit)
        if (results.isEmpty()) return@withContext call.reply("No results for " + query)
        call.reply(render(query, results))
    }

    private fun render(query: String, results: List<SearchResult>): String {
        val lines = ArrayList<String>(results.size * 4 + 1)
        lines += "Results for " + query
        results.forEachIndexed { index, result ->
            lines += (index + 1).toString() + ". " + result.title
            lines += "   " + result.link
            if (result.seen.isNotEmpty()) lines += "   seen " + result.seen
            if (result.summary.isNotEmpty()) lines += "   " + result.summary
        }
        return lines.joinToString("\n")
    }

    private companion object {
        const val NAME = "web_search"
        const val SEARCH_ENDPOINT = "https://www.bing.com/search"
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 8
        const val CALL_TIMEOUT_SECONDS = 20L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

internal class SearchResult(
    val title: String,
    val link: String,
    val summary: String,
    val seen: String
)

private val ITEM_PATTERN = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)

private val ENTITY_PATTERN = Regex("&(#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);")

internal fun parseSearchFeed(feed: String, limit: Int): List<SearchResult> =
    ITEM_PATTERN.findAll(feed)
        .map { match -> match.groupValues[1] }
        .mapNotNull { item ->
            val title = tagOf(item, "title")
            val link = tagOf(item, "link")
            if (title.isEmpty() || link.isEmpty()) {
                null
            } else {
                SearchResult(
                    title = title,
                    link = link,
                    summary = tagOf(item, "description"),
                    seen = tagOf(item, "pubDate").substringBeforeLast(' ')
                )
            }
        }
        .take(limit)
        .toList()

private fun tagOf(item: String, tag: String): String {
    val open = item.indexOf("<" + tag + ">")
    if (open < 0) return ""
    val start = open + tag.length + 2
    val close = item.indexOf("</" + tag + ">", start)
    if (close < 0) return ""
    return unescape(item.substring(start, close)).trim()
}

private fun unescape(raw: String): String {
    val stripped = raw
        .replace("<![CDATA[", "")
        .replace("]]>", "")
    if (!stripped.contains('&')) return stripped
    return ENTITY_PATTERN.replace(stripped) { match ->
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
