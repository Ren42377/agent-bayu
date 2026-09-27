package dev.agentbayu.app.ai.tools

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class SearchMode { AUTO, NEWS, WEB }

internal enum class Freshness { ANY, DAY, WEEK, MONTH }

internal enum class DateKind { PUBLISHED, INDEXED }

internal class SearchQuery(
    val text: String,
    val limit: Int,
    val mode: SearchMode,
    val freshness: Freshness
)

internal class SearchResult(
    val title: String,
    val link: String,
    val summary: String,
    val publishedAt: Long?,
    val source: String,
    val dateKind: DateKind
)

internal class SearchUnavailable(val reason: String) : Exception(reason)

internal interface SearchBackend {
    val name: String

    suspend fun search(query: SearchQuery): List<SearchResult>
}

internal const val MAX_PER_HOST = 2

internal fun newestFirst(results: List<SearchResult>): List<SearchResult> =
    results.sortedByDescending { it.publishedAt ?: Long.MIN_VALUE }

internal fun trimResults(ordered: List<SearchResult>, limit: Int): List<SearchResult> {
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

private fun dedupKey(link: String): String {
    val url = link.toHttpUrlOrNull() ?: return link.lowercase(Locale.US)
    val query = url.encodedQuery?.let { "?" + it }.orEmpty()
    return (url.host + url.encodedPath + query).lowercase(Locale.US)
}

private fun hostOf(link: String): String = link.toHttpUrlOrNull()?.host.orEmpty()
