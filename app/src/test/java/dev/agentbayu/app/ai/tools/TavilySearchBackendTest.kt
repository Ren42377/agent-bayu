package dev.agentbayu.app.ai.tools

import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class TavilySearchBackendTest {

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
    fun resultsBecomeSearchResults() {
        val body = """
            {"query":"harga emas","results":[
            {"title":"Harga Emas Hari Ini","url":"https://a.example/1",
             "content":"Antam Rp 2.559.000 per gram.","published_date":"Sun, 27 Sep 2026 06:10:16 GMT"},
            {"title":"Grafik Emas","url":"https://b.example/2",
             "content":"Selisih Rp 175.000.","published_date":"2026-09-27T04:00:00Z"}
            ]}
        """.trimIndent()

        val results = parseTavilyResponse(body)

        assertEquals(2, results.size)
        assertEquals("Harga Emas Hari Ini", results[0].title)
        assertEquals("https://a.example/1", results[0].link)
        assertEquals("Antam Rp 2.559.000 per gram.", results[0].summary)
        assertEquals("Tavily", results[0].source)
        assertEquals(DateKind.PUBLISHED, results[0].dateKind)
        assertEquals(
            Instant.parse("2026-09-27T06:10:16Z").toEpochMilli(),
            results[0].publishedAt
        )
        assertEquals(
            Instant.parse("2026-09-27T04:00:00Z").toEpochMilli(),
            results[1].publishedAt
        )
    }

    @Test
    fun aMissingPublishedDateStaysNull() {
        val body = """{"results":[{"title":"T","url":"https://a.example","content":"c"}]}"""

        val results = parseTavilyResponse(body)

        assertEquals(1, results.size)
        assertNull(results[0].publishedAt)
    }

    @Test
    fun anUnreadableDateStaysNull() {
        assertNull(epochOfTavily("not a date"))
        assertNull(epochOfTavily(""))
    }

    @Test
    fun aPlainCalendarDateIsAccepted() {
        assertEquals(
            Instant.parse("2026-09-27T00:00:00Z").toEpochMilli(),
            epochOfTavily("2026-09-27")
        )
    }

    @Test
    fun anItemWithoutAUrlIsDropped() {
        val body = """{"results":[
            {"title":"Tanpa url","content":"c"},
            {"title":"Ada","url":"https://a.example","content":"c"}]}"""

        val results = parseTavilyResponse(body)

        assertEquals(1, results.size)
        assertEquals("Ada", results[0].title)
    }

    @Test
    fun aMissingResultsArrayGivesNothing() {
        assertTrue(parseTavilyResponse("""{"query":"x"}""").isEmpty())
    }

    @Test
    fun aLongSummaryIsFlattenedAndTrimmed() {
        val body = "{\"results\":[{\"title\":\"T\",\"url\":\"https://a.example\"," +
            "\"content\":\"baris satu\\n\\n  baris   dua " + "x".repeat(600) + "\"}]}"

        val summary = parseTavilyResponse(body)[0].summary

        assertTrue(summary.startsWith("baris satu baris dua"))
        assertFalse(summary.contains("\n"))
        assertTrue(summary.endsWith("..."))
    }

    @Test
    fun aMalformedBodyBecomesAnUnavailableSignal() {
        val error = try {
            parseTavilyResponse("not json at all")
            null
        } catch (failure: SearchUnavailable) {
            failure
        }

        assertTrue(error != null)
    }

    @Test
    fun newsModeAsksForTheNewsTopic() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))

        backend().search(SearchQuery("emas", 3, SearchMode.NEWS, Freshness.ANY))

        val body = server.takeRequest().body.readUtf8()
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("news", json["topic"]?.jsonPrimitive?.content)
        assertEquals("3", json["max_results"]?.jsonPrimitive?.content)
        assertEquals("basic", json["search_depth"]?.jsonPrimitive?.content)
    }

    @Test
    fun autoModeAsksForTheGeneralTopic() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))

        backend().search(SearchQuery("kotlin", 3, SearchMode.AUTO, Freshness.ANY))

        val body = server.takeRequest().body.readUtf8()
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("general", json["topic"]?.jsonPrimitive?.content)
    }

    @Test
    fun aTimeRangeIsSentOnlyWhenFreshnessIsSet() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))

        backend().search(SearchQuery("emas", 3, SearchMode.AUTO, Freshness.WEEK))
        backend().search(SearchQuery("emas", 3, SearchMode.AUTO, Freshness.ANY))

        val withRange = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val withoutRange = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

        assertEquals("week", withRange["time_range"]?.jsonPrimitive?.content)
        assertFalse(withoutRange.containsKey("time_range"))
    }

    @Test
    fun keylessModeSendsTheAccessHeaderAndNoAuthorization() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))

        backend().search(SearchQuery("emas", 3, SearchMode.AUTO, Freshness.ANY))

        val request = server.takeRequest()
        assertEquals("keyless", request.getHeader("X-Tavily-Access-Mode"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun anApiKeyReplacesTheKeylessHeader() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))

        TavilySearchBackend(
            client = OkHttpClient(),
            endpoint = server.url("/search").toString(),
            apiKey = "tvly-secret"
        ).search(SearchQuery("emas", 3, SearchMode.AUTO, Freshness.ANY))

        val request = server.takeRequest()
        assertEquals("Bearer tvly-secret", request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Tavily-Access-Mode"))
    }

    @Test
    fun aRefusedRequestBecomesAnUnavailableSignal() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))

        val error = try {
            backend().search(SearchQuery("emas", 3, SearchMode.AUTO, Freshness.ANY))
            null
        } catch (failure: SearchUnavailable) {
            failure
        }

        assertTrue(error != null)
        assertTrue(error!!.reason.contains("429"))
    }

    @Test
    fun theLimitIsAppliedToTheReturnedResults() = runBlocking {
        val body = """{"results":[
            {"title":"A","url":"https://a.example","content":"c"},
            {"title":"B","url":"https://b.example","content":"c"},
            {"title":"C","url":"https://c.example","content":"c"}]}"""
        server.enqueue(MockResponse().setBody(body))

        val results = backend().search(SearchQuery("emas", 2, SearchMode.AUTO, Freshness.ANY))

        assertEquals(2, results.size)
    }

    private fun backend() = TavilySearchBackend(
        client = OkHttpClient(),
        endpoint = server.url("/search").toString()
    )
}
