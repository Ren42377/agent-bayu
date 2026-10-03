package dev.agentbayu.app.ui.components

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageReferencesTest {

    @Test
    fun absolutePathsBecomeFiles() {
        assertEquals(
            File("/storage/emulated/0/Pictures/a.jpg"),
            imageModelFor("/storage/emulated/0/Pictures/a.jpg")
        )
    }

    @Test
    fun escapedSpacesInPathsAreRestored() {
        assertEquals(
            File("/storage/My Pics/a.jpg"),
            imageModelFor("/storage/My%20Pics/a.jpg")
        )
    }

    @Test
    fun fileUrisAreDecodedIntoFiles() {
        assertEquals(
            File("/sdcard/My Pics/a.png"),
            imageModelFor("file:///sdcard/My%20Pics/a.png")
        )
    }

    @Test
    fun pathsWithoutLeadingSlashAreAnchoredToTheRoot() {
        assertEquals(File("/sdcard/Download/a.png"), imageModelFor("sdcard/Download/a.png"))
        assertEquals(
            File("/storage/emulated/0/a.png"),
            imageModelFor("storage/emulated/0/a.png")
        )
    }

    @Test
    fun remoteAndContentDestinationsStayStrings() {
        assertEquals("https://example.com/a.png", imageModelFor("https://example.com/a.png"))
        assertEquals("content://media/external/images/media/7", imageModelFor("content://media/external/images/media/7"))
    }

    @Test
    fun protocolRelativeUrlsUseHttps() {
        assertEquals("https://cdn.example.com/a.png", imageModelFor("//cdn.example.com/a.png"))
    }

    @Test
    fun bracketsAndTitlesAreDroppedFromDestinations() {
        assertEquals("https://example.com/a.png", imageModelFor("<https://example.com/a.png>"))
        assertEquals(
            "https://example.com/a.png",
            imageModelFor("https://example.com/a.png \"A title\"")
        )
    }

    @Test
    fun imageReferencesAreRecognised() {
        assertTrue(isImageReference("https://example.com/a.png"))
        assertTrue(isImageReference("https://example.com/a.JPG?size=large"))
        assertTrue(isImageReference("/storage/emulated/0/My Pics/a.webp"))
        assertTrue(isImageReference("file:///sdcard/a.png"))
        assertTrue(isImageReference("content://media/external/images/media/12"))
    }

    @Test
    fun otherTextIsNotAnImageReference() {
        assertFalse(isImageReference(""))
        assertFalse(isImageReference("https://example.com/page"))
        assertFalse(isImageReference("https://example.com/a.png and more"))
        assertFalse(isImageReference("/storage/emulated/0/notes.txt"))
        assertFalse(isImageReference("hello.png"))
        assertFalse(isImageReference("//cdn.example.com/a.png"))
    }

    @Test
    fun markdownImagesEscapeWhitespace() {
        assertEquals(
            "![image](/storage/My%20Pics/a.jpg)",
            markdownImageFor("image", "/storage/My Pics/a.jpg")
        )
        assertEquals("![ab](https://e.com/a.png)", markdownImageFor("[a]b", " https://e.com/a.png "))
    }

    @Test
    fun spacedDestinationsAreEscaped() {
        assertEquals(
            "![a](/storage/My%20Pics/a.png)",
            encodeImageDestinations("![a](/storage/My Pics/a.png)")
        )
    }

    @Test
    fun escapingKeepsTitlesAndBalancedParentheses() {
        assertEquals(
            "![a](/p/a%20b.png \"t\")",
            encodeImageDestinations("![a](/p/a b.png \"t\")")
        )
        assertEquals(
            "![a](/p/Shot%20(1).png)",
            encodeImageDestinations("![a](/p/Shot (1).png)")
        )
    }

    @Test
    fun cleanDestinationsAreLeftAlone() {
        val source = "![a](https://x.com/a.png \"A title\") and ![b](/p/b.png)"

        assertEquals(source, encodeImageDestinations(source))
    }

    @Test
    fun escapingSkipsFencedCode() {
        val source = "```\n![a](/p/a b.png)\n```"

        assertEquals(source, encodeImageDestinations(source))
    }

    @Test
    fun sanitiseEscapesSpacedImagePaths() {
        assertEquals(
            "![a](/storage/My%20Pics/a.png)",
            sanitiseMarkdown("![a](/storage/My Pics/a.png)")
        )
    }

    @Test
    fun standaloneReferencesBecomeImages() {
        assertEquals(
            "Intro\n\n![](https://example.com/a.png)\n\nOutro",
            embedImageReferences("Intro\nhttps://example.com/a.png\nOutro")
        )
    }

    @Test
    fun standalonePathsWithSpacesAreEscaped() {
        assertEquals(
            "![](/storage/emulated/0/My%20Pics/a.png)\n",
            embedImageReferences("/storage/emulated/0/My Pics/a.png")
        )
    }

    @Test
    fun bangParenthesisLinesBecomeImages() {
        assertEquals(
            "![](/storage/emulated/0/Notes-gambar/size.png)\n",
            embedImageReferences("!(/storage/emulated/0/Notes-gambar/size.png)")
        )
        assertEquals(
            "/storage/emulated/0/Notes-gambar/size.png",
            firstImageReference("Catatan\n!(/storage/emulated/0/Notes-gambar/size.png)")
        )
        assertEquals(
            "Catatan",
            stripImageReferences("Catatan\n!(/storage/emulated/0/Notes-gambar/size.png)")
        )
    }

    @Test
    fun embeddingLeavesOtherLinesAlone() {
        val source = "See https://example.com/a.png now\n- /storage/a.png\n    /storage/b.png\nhttps://example.com/page"

        assertEquals(source, embedImageReferences(source))
    }

    @Test
    fun embeddingSkipsFencedCode() {
        val source = "```\nhttps://example.com/a.png\n```"

        assertEquals(source, embedImageReferences(source))
    }

    @Test
    fun firstReferenceIsTheEarliestOne() {
        assertEquals(
            "https://a.com/1.png",
            firstImageReference("text\n![x](https://a.com/1.png)\n![y](https://a.com/2.png)")
        )
        assertEquals(
            "/storage/emulated/0/a.jpg",
            firstImageReference("/storage/emulated/0/a.jpg\n![x](https://a.com/1.png)")
        )
        assertEquals(
            "https://a.com/1.png",
            firstImageReference("![x](https://a.com/1.png)\n/storage/a.jpg")
        )
    }

    @Test
    fun noReferenceMeansNull() {
        assertNull(firstImageReference("no images here"))
        assertNull(firstImageReference("```\n![x](https://a.com/1.png)\n```"))
    }

    @Test
    fun strippingRemovesImageSyntaxAndReferenceLines() {
        assertEquals(
            "Intro\n\nOutro",
            stripImageReferences("Intro\n![x](https://a.com/1.png)\nOutro")
        )
        assertEquals("Intro", stripImageReferences("Intro\n/storage/emulated/0/a.jpg"))
        assertEquals("", stripImageReferences("![x](https://a.com/1.png)"))
    }

    @Test
    fun insertionIntoEmptyTextEndsWithABlankLine() {
        val result = insertImageBlock("", 0, 0, "![a](b)")

        assertEquals("![a](b)\n\n", result.text)
        assertEquals(9, result.cursor)
    }

    @Test
    fun insertionAfterAParagraphAddsASeparator() {
        val result = insertImageBlock("Hello", 5, 5, "![a](b)")

        assertEquals("Hello\n\n![a](b)\n\n", result.text)
        assertEquals(16, result.cursor)
    }

    @Test
    fun insertionDoesNotStackBlankLines() {
        val result = insertImageBlock("Hello\n\nWorld", 7, 7, "![a](b)")

        assertEquals("Hello\n\n![a](b)\n\nWorld", result.text)
    }

    @Test
    fun insertionReplacesTheSelection() {
        val result = insertImageBlock("abcdef", 2, 4, "![a](b)")

        assertEquals("ab\n\n![a](b)\n\nef", result.text)
    }

    @Test
    fun insertionClampsOutOfRangeSelections() {
        val result = insertImageBlock("abc", 10, 20, "![a](b)")

        assertEquals("abc\n\n![a](b)\n\n", result.text)
    }
}
