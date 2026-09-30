package dev.agentbayu.app.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCrossfadeStateTest {

    @Test
    fun theFirstThemeIsTheBaselineAndDoesNotCrossfade() {
        val state = ThemeCrossfadeState()

        assertFalse(state.onTheme(darkTheme = true))

        assertFalse(state.active)
    }

    @Test
    fun repeatingTheSameThemeDoesNotCrossfade() {
        val state = ThemeCrossfadeState()
        state.onTheme(darkTheme = false)

        assertFalse(state.onTheme(darkTheme = false))

        assertFalse(state.active)
    }

    @Test
    fun aFlipStartsACrossfade() {
        val state = ThemeCrossfadeState()
        state.onTheme(darkTheme = false)

        assertTrue(state.onTheme(darkTheme = true))

        assertTrue(state.active)
    }

    @Test
    fun finishingClearsTheActiveFlag() {
        val state = ThemeCrossfadeState()
        state.onTheme(darkTheme = false)
        state.onTheme(darkTheme = true)

        state.onFinished()

        assertFalse(state.active)
    }

    @Test
    fun everyFlipCrossfades() {
        val state = ThemeCrossfadeState()
        state.onTheme(darkTheme = false)

        assertTrue(state.onTheme(darkTheme = true))
        state.onFinished()
        assertTrue(state.onTheme(darkTheme = false))
        state.onFinished()
        assertTrue(state.onTheme(darkTheme = true))
    }

    @Test
    fun aFlipWhileStillActiveStillCrossfades() {
        val state = ThemeCrossfadeState()
        state.onTheme(darkTheme = false)
        state.onTheme(darkTheme = true)

        assertTrue(state.onTheme(darkTheme = false))
        assertTrue(state.active)
    }
}
