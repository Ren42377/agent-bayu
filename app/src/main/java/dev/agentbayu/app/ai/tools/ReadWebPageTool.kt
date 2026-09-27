package dev.agentbayu.app.ai.tools

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class ReadWebPageTool(
    client: OkHttpClient,
    private val blockedHost: (String) -> Boolean = ::isBlockedHost
) : ToolHandler {

    private val client = client.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override val spec: ToolSpec = ToolSpec(
        name = NAME,
        description = "Open one web page and read its main text. Use it after web_search when " +
            "the search descriptions do not contain the answer, when a result looks outdated " +
            "and you need to check what the page says now, or when the owner gives you a link " +
            "to read. It returns the page title, the address, and the readable text with menus, " +
            "scripts, and page furniture removed, cut short if the page is long. Only pages " +
            "that a normal browser could open are readable; local and private addresses are " +
            "refused. Quote what the page actually says and say when the page does not answer " +
            "the question instead of filling the gap yourself.",
        parameters = toolSchema(
            ToolField("url", "string", "The full address of the page, starting with http or https"),
            ToolField(
                name = "max_chars",
                type = "integer",
                description = "How much text to read back, from 500 to 8000 characters",
                required = false
            )
        )
    )

    override suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val arguments = ToolArguments(call.arguments)
        val raw = arguments.text("url") ?: return@withContext call.problem(URL_REQUIRED)
        val maxChars = arguments.number("max_chars", DEFAULT_MAX_CHARS)
            .coerceIn(MIN_MAX_CHARS, MAX_MAX_CHARS)
        val url = raw.toHttpUrlOrNull() ?: return@withContext call.problem(NOT_A_URL + raw)
        when (val fetched = fetch(url)) {
            is FetchResult.Failed -> return@withContext call.problem(fetched.reason)
            is FetchResult.Html -> {
                val page = readablePage(fetched.html, fetched.url)
                if (page.text.isEmpty()) {
                    return@withContext call.reply(NO_TEXT + page.url)
                }
                val clipped = if (page.text.length <= maxChars) {
                    page.text
                } else {
                    page.text.take(maxChars).trimEnd() + TRUNCATED
                }
                val heading = if (page.title.isEmpty()) {
                    PAGE + page.url
                } else {
                    PAGE + page.title + "\n" + page.url
                }
                call.reply(heading + "\n\n" + clipped)
            }
        }
    }

    private fun fetch(start: HttpUrl): FetchResult {
        var url = start
        var hops = 0
        while (true) {
            when (val step = step(url)) {
                is Step.Follow -> {
                    if (hops >= MAX_REDIRECTS) return FetchResult.Failed(REDIRECT_LOOP)
                    hops += 1
                    url = step.url
                }

                is Step.Done -> return step.result
            }
        }
    }

    private fun step(url: HttpUrl): Step {
        if (blockedHost(url.host)) return Step.Done(FetchResult.Failed(BLOCKED))
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val location = response.header("Location")
                when {
                    response.code in REDIRECT_CODES && location != null -> {
                        val next = url.resolve(location)
                        if (next == null) {
                            Step.Done(FetchResult.Failed(REDIRECT_BAD))
                        } else {
                            Step.Follow(next)
                        }
                    }

                    response.code in REDIRECT_CODES -> Step.Done(FetchResult.Failed(REDIRECT_BAD))

                    !response.isSuccessful -> Step.Done(
                        FetchResult.Failed(REFUSED + response.code)
                    )

                    else -> Step.Done(readBody(response))
                }
            }
        } catch (error: IOException) {
            Step.Done(FetchResult.Failed(UNREACHABLE))
        }
    }

    private fun readBody(response: Response): FetchResult {
        val type = response.header("Content-Type").orEmpty().lowercase(Locale.US)
        val readable = type.isEmpty() ||
            type.contains("html") ||
            type.contains("xml") ||
            type.contains("text/plain")
        if (!readable) {
            return FetchResult.Failed(NOT_HTML + type.substringBefore(';').trim())
        }
        val body = response.body ?: return FetchResult.Failed(PAGE_EMPTY)
        if (body.contentLength() > MAX_BYTES) return FetchResult.Failed(PAGE_TOO_LARGE)
        val charset = body.contentType()?.charset() ?: Charsets.UTF_8
        return try {
            val source = body.source()
            source.request(MAX_BYTES + 1L)
            if (source.buffer.size > MAX_BYTES) {
                FetchResult.Failed(PAGE_TOO_LARGE)
            } else {
                FetchResult.Html(
                    url = response.request.url.toString(),
                    html = source.buffer.clone().readString(charset)
                )
            }
        } catch (error: IOException) {
            FetchResult.Failed(UNREACHABLE)
        }
    }

    private companion object {
        const val NAME = "read_web_page"
        const val DEFAULT_MAX_CHARS = 4_000
        const val MIN_MAX_CHARS = 500
        const val MAX_MAX_CHARS = 8_000
        const val CALL_TIMEOUT_SECONDS = 20L
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = 300..399
        const val MAX_BYTES = 2L * 1024L * 1024L
        const val URL_REQUIRED = "A url is required"
        const val NOT_A_URL = "That is not a readable address: "
        const val BLOCKED = "That link points to a local or private address, so it was not opened"
        const val REFUSED = "The page refused the request with status "
        const val UNREACHABLE = "Cannot reach that page right now"
        const val NOT_HTML = "That link is not a readable page ("
        const val PAGE_EMPTY = "The page returned nothing"
        const val PAGE_TOO_LARGE = "The page is too large to read"
        const val REDIRECT_LOOP = "That link redirects too many times to follow"
        const val REDIRECT_BAD = "That link redirects to an address that cannot be read"
        const val NO_TEXT = "No readable text at "
        const val PAGE = "Page: "
        const val TRUNCATED = "\n\n(text cut short)"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}

internal class ReadablePage(
    val title: String,
    val url: String,
    val text: String
)

private sealed interface Step {
    class Follow(val url: HttpUrl) : Step
    class Done(val result: FetchResult) : Step
}

private sealed interface FetchResult {
    class Html(val url: String, val html: String) : FetchResult
    class Failed(val reason: String) : FetchResult
}

private val STRIPPED_SELECTOR =
    "script, style, noscript, nav, footer, aside, form, iframe, svg, button, template, " +
        "dialog, [aria-hidden=true]"

private val BLOCK_TAGS = setOf(
    "p", "div", "section", "article", "main", "ul", "ol", "li", "table", "tr", "td",
    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "br", "hr", "dd", "dt",
    "figure", "figcaption"
)

private val PAGE_WHITESPACE_PATTERN = Regex("\\s+")

internal fun readablePage(html: String, url: String): ReadablePage {
    val document = Jsoup.parse(html, url)
    val title = document.title().trim()
    document.select(STRIPPED_SELECTOR).remove()
    val root = document.selectFirst("article")
        ?: document.selectFirst("main")
        ?: document.body()
        ?: return ReadablePage(title, url, "")
    return ReadablePage(title, url, blockText(root))
}

private fun blockText(root: Element): String {
    val out = StringBuilder()
    appendText(root, out)
    return collapseLines(out.toString())
}

private fun appendText(node: Node, out: StringBuilder) {
    if (node is TextNode) {
        out.append(PAGE_WHITESPACE_PATTERN.replace(node.text(), " "))
        return
    }
    if (node !is Element) return
    val tag = node.tagName().lowercase(Locale.US)
    if (tag == "br") {
        out.append('\n')
        return
    }
    val block = tag in BLOCK_TAGS
    if (block && out.isNotEmpty() && out.last() != '\n') out.append('\n')
    node.childNodes().forEach { appendText(it, out) }
    if (block && out.isNotEmpty() && out.last() != '\n') out.append('\n')
}

private fun collapseLines(raw: String): String {
    val kept = ArrayList<String>()
    raw.split('\n').forEach { line ->
        val trimmed = PAGE_WHITESPACE_PATTERN.replace(line, " ").trim()
        if (trimmed.isNotEmpty()) kept += trimmed
    }
    return kept.joinToString("\n")
}

internal fun isBlockedHost(host: String): Boolean {
    val name = host.lowercase(Locale.US).trimEnd('.')
    if (name.isEmpty()) return true
    if (name == "localhost" || name.endsWith(".localhost")) return true
    if (name.endsWith(".local") || name.endsWith(".internal")) return true
    if (name == "::1" || name == "[::1]") return true
    return isPrivateIpv4(name)
}

private fun isPrivateIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it < 0 || it > 255 }) return false
    val first = octets[0]
    val second = octets[1]
    return when {
        first == 0 || first == 127 -> true
        first == 10 -> true
        first == 169 && second == 254 -> true
        first == 172 && second in 16..31 -> true
        first == 192 && second == 168 -> true
        else -> false
    }
}
