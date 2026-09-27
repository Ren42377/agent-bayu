package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.ai.FakeClock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {

    @Test
    fun thePrimaryBackendIsUsedWhenItAnswers() = runBlocking {
        val primary = RecordingBackend("Primary", listOf(article("berita", "Primary")))
        val fallback = RecordingBackend("Fallback", listOf(article("cadangan", "Fallback")))

        val result = tool(primary, fallback).run(query("emas"))

        assertFalse(result.isError)
        assertTrue(result.content.contains("berita"))
        assertFalse(result.content.contains("cadangan"))
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun theFallbackIsUsedWhenThePrimaryFails() = runBlocking {
        val primary = RecordingBackend("Primary", emptyList(), failing = true)
        val fallback = RecordingBackend("Fallback", listOf(article("cadangan", "Fallback")))

        val result = tool(primary, fallback).run(query("emas"))

        assertFalse(result.isError)
        assertTrue(result.content.contains("cadangan"))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun theFallbackIsUsedWhenThePrimaryReturnsNothing() = runBlocking {
        val primary = RecordingBackend("Primary", emptyList())
        val fallback = RecordingBackend("Fallback", listOf(article("cadangan", "Fallback")))

        val result = tool(primary, fallback).run(query("emas"))

        assertFalse(result.isError)
        assertTrue(result.content.contains("cadangan"))
        assertEquals(1, fallback.calls)
    }

    @Test
    fun bothFailingReportsBothReasons() = runBlocking {
        val primary = RecordingBackend("Primary", emptyList(), failing = true, reason = "primary is down")
        val fallback = RecordingBackend("Fallback", emptyList(), failing = true, reason = "fallback is down")

        val result = tool(primary, fallback).run(query("emas"))

        assertTrue(result.isError)
        assertTrue(result.content.contains("primary is down"))
        assertTrue(result.content.contains("fallback is down"))
    }

    @Test
    fun bothReturningNothingSaysThereAreNoResults() = runBlocking {
        val result = tool(
            RecordingBackend("Primary", emptyList()),
            RecordingBackend("Fallback", emptyList())
        ).run(query("emas"))

        assertFalse(result.isError)
        assertEquals("No results for emas", result.content)
    }

    @Test
    fun aQueryIsRequired() = runBlocking {
        val result = tool(
            RecordingBackend("Primary", emptyList()),
            RecordingBackend("Fallback", emptyList())
        ).run(ToolCall("1", "web_search", "{}"))

        assertTrue(result.isError)
        assertEquals("A query is required", result.content)
    }

    @Test
    fun theModeAndFreshnessReachTheBackend() = runBlocking {
        val primary = RecordingBackend("Primary", listOf(article("a", "Primary")))

        tool(primary, RecordingBackend("Fallback", emptyList())).run(
            ToolCall(
                "1",
                "web_search",
                "{\"query\":\"emas\",\"mode\":\"news\",\"freshness\":\"week\"}"
            )
        )

        assertEquals(SearchMode.NEWS, primary.lastQuery?.mode)
        assertEquals(Freshness.WEEK, primary.lastQuery?.freshness)
    }

    @Test
    fun theLimitIsClampedToTheAllowedRange() = runBlocking {
        val primary = RecordingBackend("Primary", listOf(article("a", "Primary")))

        tool(primary, RecordingBackend("Fallback", emptyList())).run(
            ToolCall("1", "web_search", "{\"query\":\"emas\",\"limit\":99}")
        )

        assertEquals(8, primary.lastQuery?.limit)
    }

    @Test
    fun modesAndFreshnessValuesAreParsedLeniently() {
        assertEquals(SearchMode.NEWS, modeOf("news"))
        assertEquals(SearchMode.NEWS, modeOf("NEWS"))
        assertEquals(SearchMode.WEB, modeOf("web"))
        assertEquals(SearchMode.AUTO, modeOf("auto"))
        assertEquals(SearchMode.AUTO, modeOf(null))
        assertEquals(SearchMode.AUTO, modeOf("nonsense"))

        assertEquals(Freshness.DAY, freshnessOf("day"))
        assertEquals(Freshness.MONTH, freshnessOf("MONTH"))
        assertEquals(Freshness.ANY, freshnessOf(null))
        assertEquals(Freshness.ANY, freshnessOf("nonsense"))
    }

    @Test
    fun aPublishedDateIsLabelledPublishedAndAnIndexedDateIndexed() {
        val results = listOf(
            article("berita", "Tavily", "2026-09-27T09:00:00Z", DateKind.PUBLISHED),
            article("halaman", "Bing", "2010-07-12T00:00:00Z", DateKind.INDEXED)
        )
        val now = Instant.parse("2026-09-27T12:00:00Z").toEpochMilli()

        val rendered = renderResults("emas", results, now, ZoneId.of("UTC"))

        assertTrue(rendered.contains("published 3 hours ago (2026-09-27 09:00 UTC)"))
        assertTrue(rendered.contains("indexed "))
        assertTrue(rendered.contains("(2010-07-12 00:00 UTC)"))
        assertTrue(rendered.contains("last saw the page"))
    }

    @Test
    fun theSourceIsNamedForEveryResult() {
        val results = listOf(
            article("berita", "Tavily", "2026-09-27T09:00:00Z", DateKind.PUBLISHED),
            article("halaman", "Bing", "2026-09-27T09:00:00Z", DateKind.INDEXED)
        )

        val rendered = renderResults("emas", results, 0L, ZoneId.of("UTC"))

        assertTrue(rendered.contains("source Tavily"))
        assertTrue(rendered.contains("source Bing"))
    }

    @Test
    fun theIndexLegendIsOmittedWhenEveryResultIsPublished() {
        val results = listOf(article("berita", "Tavily", "2026-09-27T09:00:00Z", DateKind.PUBLISHED))

        val rendered = renderResults("emas", results, 0L, ZoneId.of("UTC"))

        assertFalse(rendered.contains("last saw the page"))
    }

    @Test
    fun agesReadNaturally() {
        assertEquals("just now", describeAge(30_000L))
        assertEquals("1 minute ago", describeAge(60_000L))
        assertEquals("5 minutes ago", describeAge(300_000L))
        assertEquals("2 hours ago", describeAge(7_200_000L))
        assertEquals("3 days ago", describeAge(259_200_000L))
    }

    private fun tool(primary: SearchBackend, fallback: SearchBackend) = WebSearchTool(
        primary = primary,
        fallback = fallback,
        clock = FakeClock(Instant.parse("2026-09-27T12:00:00Z").toEpochMilli()),
        zone = ZoneId.of("UTC")
    )

    private fun query(text: String) =
        ToolCall("1", "web_search", "{\"query\":\"" + text + "\"}")

    private fun article(
        title: String,
        source: String,
        published: String = "2026-09-27T09:00:00Z",
        dateKind: DateKind = DateKind.PUBLISHED
    ) = SearchResult(
        title = title,
        link = "https://" + title + ".example/a",
        summary = "ringkasan",
        publishedAt = Instant.parse(published).toEpochMilli(),
        source = source,
        dateKind = dateKind
    )
}

private class RecordingBackend(
    override val name: String,
    private val results: List<SearchResult>,
    private val failing: Boolean = false,
    private val reason: String = "unavailable"
) : SearchBackend {

    var calls: Int = 0
    var lastQuery: SearchQuery? = null

    override suspend fun search(query: SearchQuery): List<SearchResult> {
        calls += 1
        lastQuery = query
        if (failing) throw SearchUnavailable(reason)
        return results
    }
}
