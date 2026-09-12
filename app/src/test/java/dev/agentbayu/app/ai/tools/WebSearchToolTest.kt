package dev.agentbayu.app.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {

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

        val results = parseSearchFeed(feed, 5)

        assertEquals(2, results.size)
        assertEquals("Satu", results[0].title)
        assertEquals("https://a.example/1", results[0].link)
        assertEquals("Ringkasan satu", results[0].summary)
        assertEquals("Sat, 05 Sep 2026 22:29:00", results[0].seen)
    }

    @Test
    fun theLimitCapsTheResults() {
        val feed = (1..6).joinToString("") { index ->
            "<item><title>T" + index + "</title><link>https://x.example/" + index +
                "</link><description>d</description></item>"
        }

        assertEquals(2, parseSearchFeed(feed, 2).size)
    }

    @Test
    fun entitiesAndSectionsAreUnescaped() {
        val feed = "<item><title>Emas &amp; Perak</title><link>https://c.example</link>" +
            "<description><![CDATA[harga &lt;naik&gt; &#8211; hari ini]]></description></item>"

        val results = parseSearchFeed(feed, 5)

        assertEquals("Emas & Perak", results[0].title)
        assertTrue(results[0].summary.startsWith("harga <naik>"))
    }

    @Test
    fun anItemWithoutALinkIsDropped() {
        val feed = "<item><title>Tanpa tautan</title><description>d</description></item>" +
            "<item><title>Ada</title><link>https://d.example</link></item>"

        val results = parseSearchFeed(feed, 5)

        assertEquals(1, results.size)
        assertEquals("Ada", results[0].title)
    }

    @Test
    fun anEmptyFeedGivesNoResults() {
        assertTrue(parseSearchFeed("<rss><channel></channel></rss>", 5).isEmpty())
    }
}
