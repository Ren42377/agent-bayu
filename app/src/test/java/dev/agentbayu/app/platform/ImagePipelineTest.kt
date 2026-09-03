package dev.agentbayu.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePipelineTest {

    private val maxEdge = ImagePipeline.MAX_EDGE

    @Test
    fun smallImagesAreDecodedWhole() {
        assertEquals(1, ImagePipeline.sampleSizeFor(800, 600, maxEdge))
        assertEquals(1, ImagePipeline.sampleSizeFor(maxEdge, maxEdge, maxEdge))
        assertEquals(1, ImagePipeline.sampleSizeFor(0, 0, maxEdge))
    }

    @Test
    fun sampleSizeDoublesPerHalvingStep() {
        assertEquals(2, ImagePipeline.sampleSizeFor(4_000, 3_000, maxEdge))
        assertEquals(4, ImagePipeline.sampleSizeFor(8_000, 6_000, maxEdge))
        assertEquals(8, ImagePipeline.sampleSizeFor(16_000, 12_000, maxEdge))
    }

    @Test
    fun theLongestEdgeDecidesTheStep() {
        assertEquals(
            ImagePipeline.sampleSizeFor(6_000, 400, maxEdge),
            ImagePipeline.sampleSizeFor(400, 6_000, maxEdge)
        )
    }

    @Test
    fun samplingNeverFallsBelowTheTargetEdge() {
        listOf(1_600, 2_000, 3_137, 4_000, 12_000, 40_000).forEach { edge ->
            val sample = ImagePipeline.sampleSizeFor(edge, edge / 2, maxEdge)
            assertTrue(edge.toString(), edge / sample >= maxEdge)
        }
    }

    @Test
    fun thumbnailsSampleHarderThanFullImages() {
        val full = ImagePipeline.sampleSizeFor(4_000, 3_000, maxEdge)
        val thumbnail = ImagePipeline.sampleSizeFor(4_000, 3_000, ImagePipeline.THUMBNAIL_EDGE)

        assertTrue(thumbnail > full)
    }
}
