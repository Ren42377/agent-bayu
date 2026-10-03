package dev.agentbayu.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeScrubTest {

    @Test
    fun stopsFollowTheThemeModeOrder() {
        assertEquals(listOf(0f, 0f, 1f), themeDarknessStops(systemDarkness = 0f))
        assertEquals(listOf(1f, 0f, 1f), themeDarknessStops(systemDarkness = 1f))
    }

    @Test
    fun wholePositionsReturnTheirStop() {
        val stops = listOf(1f, 0f, 1f)

        assertEquals(1f, interpolateDarkness(stops, 0f), 0f)
        assertEquals(0f, interpolateDarkness(stops, 1f), 0f)
        assertEquals(1f, interpolateDarkness(stops, 2f), 0f)
    }

    @Test
    fun fractionalPositionsBlendNeighbouringStops() {
        val stops = listOf(1f, 0f, 1f)

        assertEquals(0.5f, interpolateDarkness(stops, 0.5f), 0.0001f)
        assertEquals(0.25f, interpolateDarkness(stops, 1.25f), 0.0001f)
        assertEquals(0.75f, interpolateDarkness(stops, 1.75f), 0.0001f)
    }

    @Test
    fun positionsOutsideTheRangeAreClamped() {
        val stops = listOf(0f, 0f, 1f)

        assertEquals(0f, interpolateDarkness(stops, -1f), 0f)
        assertEquals(1f, interpolateDarkness(stops, 5f), 0f)
    }

    @Test
    fun noStopsMeansLight() {
        assertEquals(0f, interpolateDarkness(emptyList(), 1f), 0f)
    }

    @Test
    fun scrubIsEmptyUntilBound() {
        val scrub = ThemeScrub()

        assertNull(scrub.read())
    }

    @Test
    fun boundScrubReadsTheLivePosition() {
        val scrub = ThemeScrub()
        var position = 0f
        scrub.bind { position }

        assertEquals(0f, scrub.read()!!, 0f)
        position = 1.5f
        assertEquals(1.5f, scrub.read()!!, 0f)
    }

    @Test
    fun unbindingClearsTheScrub() {
        val scrub = ThemeScrub()
        scrub.bind { 2f }

        scrub.unbind()

        assertNull(scrub.read())
    }
}
