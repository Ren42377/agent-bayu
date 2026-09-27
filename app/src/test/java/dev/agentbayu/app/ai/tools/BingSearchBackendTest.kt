package dev.agentbayu.app.ai.tools

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BingSearchBackendTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun feedItemsBecomeResults() {
        val feed = """
            <rss><channel>
            <item><title>Satu</title><link>https://a.example/1</link>
            <description>Ringkasan satu</description>
            <pubDate>Sat, 05 Sep 2026 22:29:00 GMT</pubDate></item>
            <item><title>Dua</title><link>https://b.example/2</link>
            <description>Ringkasan dua</description>
            <pubDate>Wed, 02 Sep 2026 08:13:00 GMT</pubDate></item>
            </channel></rss>
        """.trimIndent()

        val results = parseSearchFeed(feed, 5, DateKind.PUBLISHED)

        assertEquals(2, results.size)
        assertEquals("Satu", results[0].title)
        assertEquals("https://a.example/1", results[0].link)
        assertEquals("Ringkasan satu", results[0].summary)
        assertEquals("Bing", results[0].source)
        assertEquals(DateKind.PUBLISHED, results[0].dateKind)
        assertEquals(
            Instant.parse("2026-09-05T22:29:00Z").toEpochMilli(),
            results[0].publishedAt
        )
    }

    @Test
    fun anItemWithoutADateKeepsANullDate() {
        val feed = "<item><title>Tanpa tanggal</title>" +
            "<link>https://a.example/1</link><description>d</description></item>"

        val results = parseSearchFeed(feed, 5, DateKind.INDEXED)

        assertEquals(1, results.size)
        assertNull(results[0].publishedAt)
        assertEquals(DateKind.INDEXED, results[0].dateKind)
    }

    @Test
    fun theLimitCapsTheResults() {
        val feed = (1..6).joinToString("") { index ->
            "<item><title>T" + index + "</title><link>https://x.example/" + index +
                "</link><description>d</description></item>"
        }

        assertEquals(2, parseSearchFeed(feed, 2, DateKind.INDEXED).size)
    }

    @Test
    fun entitiesAndSectionsAreUnescaped() {
        val feed = "<item><title>Emas &amp; Perak</title><link>https://c.example</link>" +
            "<description><![CDATA[harga &lt;naik&gt; &#8211; hari ini]]></description></item>"

        val results = parseSearchFeed(feed, 5, DateKind.INDEXED)

        assertEquals("Emas & Perak", results[0].title)
        assertTrue(results[0].summary.startsWith("harga <naik>"))
    }

    @Test
    fun anItemWithoutALinkIsDropped() {
        val feed = "<item><title>Tanpa tautan</title><description>d</description></item>" +
            "<item><title>Ada</title><link>https://d.example</link></item>"

        val results = parseSearchFeed(feed, 5, DateKind.INDEXED)

        assertEquals(1, results.size)
        assertEquals("Ada", results[0].title)
    }

    @Test
    fun anEmptyFeedGivesNoResults() {
        assertTrue(parseSearchFeed("<rss><channel></channel></rss>", 5, DateKind.INDEXED).isEmpty())
    }

    @Test
    fun aLongSummaryIsTrimmedAndFlattened() {
        val feed = "<item><title>T</title><link>https://e.example</link><description>" +
            "<b>tebal</b>   banyak    spasi " + "x".repeat(400) +
            "</description></item>"

        val summary = parseSearchFeed(feed, 5, DateKind.INDEXED)[0].summary

        assertTrue(summary.startsWith("tebal banyak spasi"))
        assertFalse(summary.contains("<b>"))
        assertTrue(summary.endsWith("..."))
        assertTrue(summary.length <= 303)
    }

    @Test
    fun aBingNewsWrapperLinkIsUnwrapped() {
        val wrapped = "http://www.bing.com/news/apiclick.aspx?ref=FexRss&aid=&tid=abc" +
            "&url=https%3a%2f%2finvestor.id%2fmarket%2f455694%2fberita&c=123&mkt=en-id"

        assertEquals("https://investor.id/market/455694/berita", articleUrl(wrapped))
    }

    @Test
    fun anOrdinaryLinkIsLeftAlone() {
        assertEquals(
            "https://www.liputan6.com/harga-emas-hari-ini",
            articleUrl("https://www.liputan6.com/harga-emas-hari-ini")
        )
    }

    @Test
    fun newsComesFirstAndEachGroupIsNewestFirst() {
        val news = listOf(
            result("news lama", "https://n1.example/a", "2026-09-01T00:00:00Z", DateKind.PUBLISHED),
            result("news baru", "https://n2.example/a", "2026-09-05T00:00:00Z", DateKind.PUBLISHED)
        )
        val web = listOf(
            result("web baru", "https://w1.example/a", "2026-09-26T00:00:00Z", DateKind.INDEXED),
            result("web lama", "https://w2.example/a", "2010-07-12T00:00:00Z", DateKind.INDEXED)
        )

        val chosen = trimResults(newestFirst(news) + newestFirst(web), 5)

        assertEquals(
            listOf("news baru", "news lama", "web baru", "web lama"),
            chosen.map { it.title }
        )
    }

    @Test
    fun oneHostCannotFillEverySlot() {
        val web = listOf(
            result("a", "https://same.example/1", "2026-09-26T00:00:00Z", DateKind.INDEXED),
            result("b", "https://same.example/2", "2026-09-26T00:00:00Z", DateKind.INDEXED),
            result("c", "https://same.example/3", "2026-09-26T00:00:00Z", DateKind.INDEXED),
            result("d", "https://other.example/1", "2026-09-26T00:00:00Z", DateKind.INDEXED)
        )

        val chosen = trimResults(newestFirst(web), 5)

        assertEquals(listOf("a", "b", "d"), chosen.map { it.title })
    }

    @Test
    fun aRepeatedLinkIsKeptOnlyOnce() {
        val web = listOf(
            result("asli", "https://x.example/a", "2026-09-26T00:00:00Z", DateKind.INDEXED),
            result("kembar", "https://x.example/a", "2026-09-25T00:00:00Z", DateKind.INDEXED)
        )

        val chosen = trimResults(newestFirst(web), 5)

        assertEquals(listOf("asli"), chosen.map { it.title })
    }

    @Test
    fun aFreshnessFilterReachesTheNewsRequestOnly() = runBlocking {
        server.enqueue(rss("Berita", "https://news.example/a", "Sun, 27 Sep 2026 09:00:00 GMT"))
        server.enqueue(rss("Halaman", "https://web.example/a", "Sun, 27 Sep 2026 09:00:00 GMT"))

        backend().search(
            SearchQuery("emas", 8, SearchMode.AUTO, Freshness.WEEK)
        )

        val newsRequest = server.takeRequest().requestUrl
        val webRequest = server.takeRequest().requestUrl

        assertEquals("interval=\"7\"", newsRequest?.queryParameter("qft"))
        assertNull(webRequest?.queryParameter("qft"))
    }

    @Test
    fun webModeSkipsTheNewsRequest() = runBlocking {
        server.enqueue(rss("Halaman", "https://web.example/a", "Sun, 27 Sep 2026 09:00:00 GMT"))

        val results = backend().search(
            SearchQuery("emas", 8, SearchMode.WEB, Freshness.ANY)
        )

        assertEquals(1, server.requestCount)
        assertEquals(DateKind.INDEXED, results[0].dateKind)
    }

    @Test
    fun newsModeSkipsTheGeneralRequest() = runBlocking {
        server.enqueue(rss("Berita", "https://news.example/a", "Sun, 27 Sep 2026 09:00:00 GMT"))

        val results = backend().search(
            SearchQuery("emas", 8, SearchMode.NEWS, Freshness.ANY)
        )

        assertEquals(1, server.requestCount)
        assertEquals(DateKind.PUBLISHED, results[0].dateKind)
    }

    @Test
    fun aRefusedRequestBecomesAnUnavailableSignal() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = try {
            backend().search(SearchQuery("emas", 8, SearchMode.WEB, Freshness.ANY))
            null
        } catch (failure: SearchUnavailable) {
            failure
        }

        assertTrue(error != null)
        assertTrue(error!!.reason.contains("503"))
    }

    @Test
    fun anEmptyFeedIsNotAnError() = runBlocking {
        server.enqueue(MockResponse().setBody("<rss><channel></channel></rss>"))

        val results = backend().search(SearchQuery("emas", 8, SearchMode.WEB, Freshness.ANY))

        assertTrue(results.isEmpty())
    }

    private fun backend() = BingSearchBackend(
        client = OkHttpClient(),
        newsEndpoint = server.url("/news").toString(),
        webEndpoint = server.url("/web").toString()
    )

    private fun rss(title: String, link: String, pubDate: String): MockResponse =
        MockResponse().setBody(
            "<rss><channel><item><title>" + title + "</title><link>" + link +
                "</link><description>ringkasan</description><pubDate>" + pubDate +
                "</pubDate></item></channel></rss>"
        )

    private fun result(
        title: String,
        link: String,
        published: String,
        dateKind: DateKind
    ) = SearchResult(
        title = title,
        link = link,
        summary = "ringkasan",
        publishedAt = Instant.parse(published).toEpochMilli(),
        source = "Bing",
        dateKind = dateKind
    )
}
