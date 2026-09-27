package dev.agentbayu.app.ai.tools

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadWebPageToolTest {

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
    fun scriptsStylesAndFurnitureAreRemoved() {
        val html = """
            <html><head><title>Judul</title>
            <style>body { color: red; }</style>
            <script>var x = 1;</script>
            </head><body>
            <nav>Menu utama</nav>
            <article><p>Isi sebenarnya.</p></article>
            <footer>Hak cipta</footer>
            <aside>Iklan</aside>
            </body></html>
        """.trimIndent()

        val page = readablePage(html, "https://x.example/a")

        assertEquals("Judul", page.title)
        assertEquals("Isi sebenarnya.", page.text)
    }

    @Test
    fun theArticleWinsOverTheWholeBody() {
        val html = "<body><div>Bukan isi</div><article><p>Artikel inti.</p></article></body>"

        val page = readablePage(html, "https://x.example/a")

        assertEquals("Artikel inti.", page.text)
    }

    @Test
    fun mainIsUsedWhenThereIsNoArticle() {
        val html = "<body><div>Bukan isi</div><main><p>Isi utama.</p></main></body>"

        val page = readablePage(html, "https://x.example/a")

        assertEquals("Isi utama.", page.text)
    }

    @Test
    fun blockElementsBecomeSeparateLines() {
        val html = "<article><h1>Harga Emas</h1><p>Antam Rp 2.740.000.</p>" +
            "<ul><li>Satu</li><li>Dua</li></ul></article>"

        val page = readablePage(html, "https://x.example/a")

        assertEquals("Harga Emas\nAntam Rp 2.740.000.\nSatu\nDua", page.text)
    }

    @Test
    fun runsOfWhitespaceCollapse() {
        val html = "<article><p>harga    emas\n\n   hari   ini</p></article>"

        val page = readablePage(html, "https://x.example/a")

        assertEquals("harga emas hari ini", page.text)
    }

    @Test
    fun aPageWithoutReadableTextComesBackEmpty() {
        val html = "<html><head><title>Kosong</title></head><body></body></html>"

        val page = readablePage(html, "https://x.example/a")

        assertEquals("", page.text)
    }

    @Test
    fun privateAndLocalHostsAreBlocked() {
        assertTrue(isBlockedHost("localhost"))
        assertTrue(isBlockedHost("127.0.0.1"))
        assertTrue(isBlockedHost("10.1.2.3"))
        assertTrue(isBlockedHost("172.16.0.9"))
        assertTrue(isBlockedHost("192.168.1.10"))
        assertTrue(isBlockedHost("169.254.1.1"))
        assertTrue(isBlockedHost("nas.local"))
        assertTrue(isBlockedHost("router.internal"))
        assertTrue(isBlockedHost("::1"))
        assertTrue(isBlockedHost(""))
    }

    @Test
    fun publicHostsAreAllowed() {
        assertFalse(isBlockedHost("example.com"))
        assertFalse(isBlockedHost("www.liputan6.com"))
        assertFalse(isBlockedHost("172.32.0.1"))
        assertFalse(isBlockedHost("8.8.8.8"))
    }

    @Test
    fun aUrlIsRequired() = runBlocking {
        val result = tool().run(ToolCall("1", "read_web_page", "{}"))

        assertTrue(result.isError)
        assertEquals("A url is required", result.content)
    }

    @Test
    fun anAddressThatIsNotAUrlIsRefused() = runBlocking {
        val result = tool().run(
            ToolCall("1", "read_web_page", "{\"url\":\"ftp://example.com/a\"}")
        )

        assertTrue(result.isError)
        assertTrue(result.content.startsWith("That is not a readable address: "))
    }

    @Test
    fun aPrivateAddressIsRefusedWithoutARequest() = runBlocking {
        val blocked = ReadWebPageTool(OkHttpClient()) { true }

        val result = blocked.run(
            ToolCall("1", "read_web_page", "{\"url\":\"" + server.url("/rahasia") + "\"}")
        )

        assertTrue(result.isError)
        assertTrue(result.content.contains("local or private"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aPageIsReadBackWithItsTitleAndAddress() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><head><title>Harga Emas</title></head><body>" +
                    "<article><p>Antam Rp 2.740.000 per gram.</p></article></body></html>")
        )
        val url = server.url("/harga").toString()

        val result = tool().run(ToolCall("1", "read_web_page", "{\"url\":\"" + url + "\"}"))

        assertFalse(result.isError)
        assertTrue(result.content.startsWith("Page: Harga Emas"))
        assertTrue(result.content.contains(url))
        assertTrue(result.content.contains("Antam Rp 2.740.000 per gram."))
    }

    @Test
    fun theTextIsCutToTheRequestedLength() = runBlocking {
        val body = "<article><p>" + "kata ".repeat(600) + "</p></article>"
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/html").setBody(body)
        )
        val url = server.url("/panjang").toString()

        val result = tool().run(
            ToolCall("1", "read_web_page", "{\"url\":\"" + url + "\",\"max_chars\":500}")
        )

        assertTrue(result.content.contains("(text cut short)"))
        assertTrue(result.content.length < 700)
    }

    @Test
    fun aRefusedPageIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val url = server.url("/hilang").toString()

        val result = tool().run(ToolCall("1", "read_web_page", "{\"url\":\"" + url + "\"}"))

        assertTrue(result.isError)
        assertTrue(result.content.contains("404"))
    }

    @Test
    fun aNonHtmlPageIsRefused() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/pdf")
                .setBody("%PDF-1.4")
        )
        val url = server.url("/berkas.pdf").toString()

        val result = tool().run(ToolCall("1", "read_web_page", "{\"url\":\"" + url + "\"}"))

        assertTrue(result.isError)
        assertTrue(result.content.contains("application/pdf"))
    }

    @Test
    fun aRedirectIsFollowedToTheFinalPage() = runBlocking {
        val target = server.url("/akhir").toString()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", target))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<article><p>Halaman akhir.</p></article>")
        )

        val result = tool().run(
            ToolCall("1", "read_web_page", "{\"url\":\"" + server.url("/awal") + "\"}")
        )

        assertFalse(result.isError)
        assertTrue(result.content.contains("Halaman akhir."))
        assertTrue(result.content.contains("/akhir"))
    }

    @Test
    fun aPageWithNoReadableTextSaysSo() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody("<html><body><script>var x = 1;</script></body></html>")
        )
        val url = server.url("/kosong").toString()

        val result = tool().run(ToolCall("1", "read_web_page", "{\"url\":\"" + url + "\"}"))

        assertFalse(result.isError)
        assertTrue(result.content.startsWith("No readable text at "))
    }

    private fun tool() = ReadWebPageTool(OkHttpClient()) { false }
}
