package dev.agentbayu.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMarkupTest {

    @Test
    fun plainProseStaysOneBlock() {
        val blocks = splitMarkup("Halo, ini jawaban biasa.")
        assertEquals(listOf(MarkupBlock.Markdown("Halo, ini jawaban biasa.")), blocks)
    }

    @Test
    fun displayMathBecomesItsOwnBlock() {
        val blocks = splitMarkup("Rumusnya:\n\n\$\$x^2 + y^2 = z^2\$\$\n\nSelesai.")

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkupBlock.Markdown)
        assertEquals(MarkupBlock.Math("x^2 + y^2 = z^2"), blocks[1])
        assertTrue(blocks[2] is MarkupBlock.Markdown)
    }

    @Test
    fun bracketMathIsAlsoADisplayBlock() {
        val blocks = splitMarkup("Sebelum \\[a + b\\] sesudah")

        assertEquals(MarkupBlock.Math("a + b"), blocks[1])
    }

    @Test
    fun inlineMathIsFoundInsideASentence() {
        assertTrue(hasInlineMath("Kompleksitasnya \$O(n^2)\$ untuk kasus terburuk."))
        assertTrue(hasInlineMath("Jadi \\(a+b\\) hasilnya."))
    }

    @Test
    fun currencyIsNotMistakenForMath() {
        assertFalse(hasInlineMath("Harganya \$5 dan \$10 saja."))
        assertFalse(hasInlineMath("Sisa \$ 20"))
    }

    @Test
    fun inlineRunsKeepTheirOrder() {
        val runs = splitInlineRuns("Nilai \$x\$ lalu \$y\$ selesai")

        assertEquals(5, runs.size)
        assertEquals(InlineRun.Text("Nilai "), runs[0])
        assertEquals(InlineRun.Math("x"), runs[1])
        assertEquals(InlineRun.Text(" lalu "), runs[2])
        assertEquals(InlineRun.Math("y"), runs[3])
        assertEquals(InlineRun.Text(" selesai"), runs[4])
    }

    @Test
    fun aFenceReportsItsLanguageAndBody() {
        val fence = fenceOf("```python\nprint(1)\nprint(2)\n```")

        assertEquals("python", fence.language)
        assertEquals("print(1)\nprint(2)", fence.body)
    }

    @Test
    fun aFenceWithoutALanguageStillReportsItsBody() {
        val fence = fenceOf("```\nhalo\n```")

        assertEquals(null, fence.language)
        assertEquals("halo", fence.body)
    }

    @Test
    fun markdownFencesAreRecognisedByEitherName() {
        assertTrue(isMarkdownLanguage("md"))
        assertTrue(isMarkdownLanguage("Markdown"))
        assertFalse(isMarkdownLanguage("kotlin"))
        assertFalse(isMarkdownLanguage(null))
    }

    @Test
    fun theFirstHeadingBecomesTheCardTitle() {
        assertEquals("Catatan Harian", markdownTitleOf("# Catatan Harian\n\nisi"))
        assertEquals("Bagian", markdownTitleOf("teks dulu\n\n## Bagian"))
        assertEquals(null, markdownTitleOf("tidak ada heading"))
    }

    @Test
    fun aMarkdownFenceHoldingCodeGetsALongerDelimiter() {
        val source = "```md\n# Judul\n\n```python\nprint(1)\n```\n\nselesai\n```"

        val normalised = normaliseMarkdownFences(source)

        assertTrue(normalised.startsWith("````md\n"))
        assertTrue(normalised.endsWith("\n````"))
        assertTrue(normalised.contains("```python\nprint(1)\n```"))
        assertTrue(normalised.contains("selesai"))
    }

    @Test
    fun aFenceThatIsNotMarkdownIsLeftAlone() {
        val source = "```python\nprint(1)\n```"

        assertEquals(source, normaliseMarkdownFences(source))
    }

    @Test
    fun aLongerFenceStillReportsItsLanguageAndBody() {
        val fence = fenceOf("````md\n# Judul\n\n```py\nx\n```\n````")

        assertEquals("md", fence.language)
        assertEquals("# Judul\n\n```py\nx\n```", fence.body)
    }

    @Test
    fun mathMarkersInsideAFenceAreNotSplitOut() {
        val source = "Kode:\n\n```tex\n\$\$a+b\$\$\n```\n\nselesai"

        val blocks = splitMarkup(source)

        assertEquals(listOf(MarkupBlock.Markdown(source)), blocks)
    }

    @Test
    fun fencedRangesCoverTheWholeFence() {
        val source = "satu\n```\ndua\n```\ntiga"

        val ranges = fencedRanges(source)

        assertEquals(1, ranges.size)
        assertEquals("```\ndua\n```", source.substring(ranges.first().first, ranges.first().last))
    }

    @Test
    fun sanitiseSeparatesThematicBreakFromPrecedingText() {
        assertEquals("Teks\n\n---", sanitiseMarkdown("Teks\n---"))
    }

    @Test
    fun sanitiseSeparatesIndentedThematicBreakInListItem() {
        assertEquals("1. Poin\n\n---", sanitiseMarkdown("1. Poin\n   ---"))
    }

    @Test
    fun sanitiseKeepsFencedContentUntouched() {
        val source = "```\nTeks\n---\n<span hidden>rahasia</span>\n```"

        assertEquals(source, sanitiseMarkdown(source))
    }

    @Test
    fun sanitiseLiftsFootnoteDefinitionsToTheEnd() {
        val result = sanitiseMarkdown(
            "Teks dengan catatan kaki[^1].\n\n[^1]: Ini adalah contoh catatan kaki."
        )

        assertFalse(result.contains("[^"))
        assertTrue(result.contains("Teks dengan catatan kaki[1]."))
        assertTrue(result.endsWith("[1] Ini adalah contoh catatan kaki.\n"))
    }

    @Test
    fun sanitiseRemovesHiddenElementsAndKeepsSurroundingText() {
        val result = sanitiseMarkdown(
            "Sebelum <span hidden>Konten tersembunyi di dalam HTML.</span> Sesudah"
        )

        assertFalse(result.contains("Konten tersembunyi"))
        assertTrue(result.contains("Sebelum"))
        assertTrue(result.contains("Sesudah"))
    }

    @Test
    fun sanitiseConvertsSimpleHtmlTagsToMarkdown() {
        assertEquals(
            "**Tebal** dan *miring*",
            sanitiseMarkdown("<b>Tebal</b> dan <i>miring</i>")
        )
    }

    @Test
    fun inlineRunsTreatDisplayMathAsOneRun() {
        val runs = splitInlineRuns("coba \$\$x^2\$\$ ya")

        assertEquals(
            listOf(
                InlineRun.Text("coba "),
                InlineRun.Math("x^2"),
                InlineRun.Text(" ya")
            ),
            runs
        )
    }

    @Test
    fun imageModelMapsAbsolutePathsToFiles() {
        assertTrue(imageModelFor("/storage/emulated/0/Pictures/a.png") is java.io.File)
        assertEquals("https://example.com/a.png", imageModelFor("https://example.com/a.png"))
        assertEquals("//cdn.example.com/a.png", imageModelFor("//cdn.example.com/a.png"))
        assertEquals("content://media/1", imageModelFor("content://media/1"))
        assertEquals("file:///sdcard/a.png", imageModelFor("file:///sdcard/a.png"))
    }
}
