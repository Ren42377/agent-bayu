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
        val blocks = splitMarkup("Rumusnya:\n\n$$x^2 + y^2 = z^2$$\n\nSelesai.")

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
}
