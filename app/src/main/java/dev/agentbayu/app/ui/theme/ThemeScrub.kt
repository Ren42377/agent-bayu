package dev.agentbayu.app.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.agentbayu.app.platform.ThemeMode

@Stable
internal class ThemeScrub {

    private var source: (() -> Float)? by mutableStateOf(null)

    fun bind(reader: () -> Float) {
        source = reader
    }

    fun unbind() {
        source = null
    }

    fun read(): Float? = source?.invoke()
}

internal val LocalThemeScrub = staticCompositionLocalOf { ThemeScrub() }

internal fun themeDarknessStops(systemDarkness: Float): List<Float> =
    ThemeMode.entries.map { mode ->
        when (mode) {
            ThemeMode.SYSTEM -> systemDarkness
            ThemeMode.LIGHT -> 0f
            ThemeMode.DARK -> 1f
        }
    }

internal fun interpolateDarkness(stops: List<Float>, position: Float): Float {
    if (stops.isEmpty()) {
        return 0f
    }
    val clamped = position.coerceIn(0f, stops.lastIndex.toFloat())
    val low = clamped.toInt()
    val high = (low + 1).coerceAtMost(stops.lastIndex)
    val blend = clamped - low
    return stops[low] + (stops[high] - stops[low]) * blend
}
