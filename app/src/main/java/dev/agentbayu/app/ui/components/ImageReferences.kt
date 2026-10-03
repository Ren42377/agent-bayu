package dev.agentbayu.app.ui.components

import java.io.File
import java.net.URI
import java.net.URISyntaxException

internal val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg"
)

private const val FILE_SCHEME = "file://"
private const val CONTENT_SCHEME = "content://"
private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"
private const val SDCARD_PREFIX = "sdcard/"
private const val STORAGE_PREFIX = "storage/"
private const val IMAGE_OPEN = "!["
private const val IMAGE_JOIN = "]("

private val TITLE_SUFFIX = Regex("\\s+(\"[^\"]*\"|'[^']*')\\s*\$")

internal data class ImageSpan(
    val start: Int,
    val end: Int,
    val destStart: Int,
    val destEnd: Int
)

internal data class TextInsertion(val text: String, val cursor: Int)

internal fun imageModelFor(destination: String): Any {
    val target = imageTargetOf(destination)
    val lower = target.lowercase()
    return when {
        lower.startsWith(FILE_SCHEME) -> File(pathOfFileUri(target))
        lower.startsWith(CONTENT_SCHEME) ||
            lower.startsWith(HTTP_SCHEME) ||
            lower.startsWith(HTTPS_SCHEME) -> target
        target.startsWith("//") -> "https:" + target
        target.startsWith("/") -> File(unescapeWhitespace(target))
        target.startsWith(SDCARD_PREFIX) || target.startsWith(STORAGE_PREFIX) ->
            File(unescapeWhitespace("/" + target))
        else -> target
    }
}

internal fun imageTargetOf(raw: String): String {
    val trimmed = raw.trim()
    val title = TITLE_SUFFIX.find(trimmed)
    val withoutTitle = if (title != null) trimmed.substring(0, title.range.first) else trimmed
    return withoutTitle.trim().removeSurrounding("<", ">").trim()
}

internal fun isImageReference(candidate: String): Boolean {
    val text = candidate.trim()
    if (text.isEmpty()) return false
    val lower = text.lowercase()
    return when {
        lower.startsWith(CONTENT_SCHEME) -> text.none { it.isWhitespace() }
        lower.startsWith(HTTP_SCHEME) || lower.startsWith(HTTPS_SCHEME) ->
            text.none { it.isWhitespace() } &&
                hasImageExtension(lower.substringBefore('#').substringBefore('?'))
        lower.startsWith(FILE_SCHEME) -> hasImageExtension(lower)
        text.startsWith("/") && !text.startsWith("//") -> hasImageExtension(lower)
        else -> false
    }
}

internal fun markdownImageFor(alt: String, destination: String): String {
    val safeAlt = alt.replace("[", "").replace("]", "").replace("\n", " ")
    val target = escapeWhitespace(destination.trim())
    return IMAGE_OPEN + safeAlt + IMAGE_JOIN + target + ")"
}

internal fun scanImages(source: String): List<ImageSpan> {
    if (!source.contains(IMAGE_OPEN)) return emptyList()
    val spans = ArrayList<ImageSpan>(2)
    var cursor = 0
    while (cursor < source.length) {
        val start = source.indexOf(IMAGE_OPEN, cursor)
        if (start < 0) break
        val join = source.indexOf(IMAGE_JOIN, start + IMAGE_OPEN.length)
        val newline = source.indexOf('\n', start)
        val lineEnd = if (newline < 0) source.length else newline
        if (join < 0 || join > lineEnd) {
            cursor = start + IMAGE_OPEN.length
            continue
        }
        if (source.substring(start + IMAGE_OPEN.length, join).contains(']')) {
            cursor = start + IMAGE_OPEN.length
            continue
        }
        val destStart = join + IMAGE_JOIN.length
        val close = closingParenthesis(source, destStart)
        if (close < 0) {
            cursor = start + IMAGE_OPEN.length
            continue
        }
        spans += ImageSpan(start, close + 1, destStart, close)
        cursor = close + 1
    }
    return spans
}

internal fun encodeImageDestinations(source: String): String {
    if (!source.contains(IMAGE_OPEN)) return source
    return transformOutsideFences(source, ::encodeSpans)
}

internal fun embedImageReferences(source: String): String =
    transformOutsideFences(source, ::embedStandaloneReferences)

internal fun firstImageReference(content: String): String? {
    val visible = outsideFences(content)
    val span = scanImages(visible).firstOrNull()
    val markdownStart = span?.start ?: Int.MAX_VALUE
    var standalone: String? = null
    var standaloneStart = Int.MAX_VALUE
    var offset = 0
    for (line in visible.split("\n")) {
        val candidate = standaloneReference(line)
        if (candidate != null) {
            standalone = candidate
            standaloneStart = offset
            break
        }
        offset += line.length + 1
    }
    return when {
        span == null -> standalone
        standalone == null -> imageTargetOf(visible.substring(span.destStart, span.destEnd))
        standaloneStart < markdownStart -> standalone
        else -> imageTargetOf(visible.substring(span.destStart, span.destEnd))
    }
}

internal fun stripImageReferences(content: String): String {
    val spans = scanImages(content)
    val builder = StringBuilder(content.length)
    var cursor = 0
    spans.forEach { span ->
        builder.append(content, cursor, span.start)
        cursor = span.end
    }
    builder.append(content, cursor, content.length)
    return builder.toString()
        .split("\n")
        .filterNot { standaloneReference(it) != null }
        .joinToString("\n")
        .trim()
}

internal fun insertImageBlock(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    snippet: String
): TextInsertion {
    val start = selectionStart.coerceIn(0, text.length)
    val end = selectionEnd.coerceIn(start, text.length)
    val before = text.substring(0, start)
    val after = text.substring(end)
    val lead = when {
        before.isEmpty() || before.endsWith("\n\n") -> ""
        before.endsWith("\n") -> "\n"
        else -> "\n\n"
    }
    val tail = when {
        after.startsWith("\n\n") -> ""
        after.startsWith("\n") -> "\n"
        else -> "\n\n"
    }
    val block = lead + snippet + tail
    return TextInsertion(
        text = before + block + after,
        cursor = before.length + block.length
    )
}

private fun standaloneReference(line: String): String? {
    if (line.startsWith("    ") || line.startsWith("\t")) return null
    var candidate = line.trim()
    if (candidate.startsWith("!(") && candidate.endsWith(")")) {
        candidate = candidate.substring(2, candidate.length - 1)
    }
    candidate = candidate.removeSurrounding("<", ">").trim()
    return candidate.takeIf(::isImageReference)
}

private fun embedStandaloneReferences(source: String): String {
    val lines = source.split("\n")
    val out = ArrayList<String>(lines.size + 2)
    var changed = false
    lines.forEach { line ->
        val reference = standaloneReference(line)
        if (reference == null) {
            out += line
        } else {
            if (out.isNotEmpty() && out.last().isNotBlank()) out += ""
            out += markdownImageFor("", reference)
            out += ""
            changed = true
        }
    }
    return if (changed) out.joinToString("\n") else source
}

private fun encodeSpans(source: String): String {
    val spans = scanImages(source)
    if (spans.isEmpty()) return source
    val builder = StringBuilder(source.length + 16)
    var cursor = 0
    spans.forEach { span ->
        val raw = source.substring(span.destStart, span.destEnd)
        val encoded = encodeDestination(raw)
        if (encoded != raw) {
            builder.append(source, cursor, span.destStart)
            builder.append(encoded)
            cursor = span.destEnd
        }
    }
    builder.append(source, cursor, source.length)
    return builder.toString()
}

private fun encodeDestination(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("<")) return raw
    val title = TITLE_SUFFIX.find(trimmed)
    val target = if (title != null) trimmed.substring(0, title.range.first) else trimmed
    if (target.none { it.isWhitespace() }) return raw
    val suffix = if (title != null) trimmed.substring(title.range.first) else ""
    return escapeWhitespace(target) + suffix
}

private fun closingParenthesis(source: String, from: Int): Int {
    var depth = 0
    var index = from
    while (index < source.length) {
        when (source[index]) {
            '\n' -> return -1
            '(' -> depth += 1
            ')' -> {
                if (depth == 0) return index
                depth -= 1
            }
        }
        index += 1
    }
    return -1
}

private fun outsideFences(source: String): String {
    val ranges = fencedRanges(source)
    if (ranges.isEmpty()) return source
    val builder = StringBuilder(source.length)
    var cursor = 0
    ranges.forEach { range ->
        if (range.first > cursor) builder.append(source, cursor, range.first)
        cursor = minOf(range.last, source.length)
    }
    if (cursor < source.length) builder.append(source, cursor, source.length)
    return builder.toString()
}

private fun hasImageExtension(path: String): Boolean {
    val name = path.substringAfterLast('/')
    if (!name.contains('.')) return false
    return name.substringAfterLast('.') in IMAGE_EXTENSIONS
}

private fun pathOfFileUri(uri: String): String {
    val parsed = try {
        URI(uri.replace(" ", "%20")).path
    } catch (error: URISyntaxException) {
        null
    }
    return parsed?.takeIf { it.isNotEmpty() }
        ?: unescapeWhitespace(uri.substring(FILE_SCHEME.length))
}

private fun escapeWhitespace(text: String): String =
    text.replace(" ", "%20").replace("\t", "%09")

private fun unescapeWhitespace(text: String): String =
    text.replace("%20", " ").replace("%09", "\t")
