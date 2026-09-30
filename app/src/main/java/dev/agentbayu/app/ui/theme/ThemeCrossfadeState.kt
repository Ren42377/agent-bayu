package dev.agentbayu.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class ThemeCrossfadeState {

    var active: Boolean by mutableStateOf(false)
        private set

    private var lastDark: Boolean? = null

    fun onTheme(darkTheme: Boolean): Boolean {
        val previous = lastDark
        lastDark = darkTheme
        if (previous == null || previous == darkTheme) {
            return false
        }
        active = true
        return true
    }

    fun onFinished() {
        active = false
    }
}
