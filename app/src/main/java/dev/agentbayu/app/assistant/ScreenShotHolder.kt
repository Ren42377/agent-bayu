package dev.agentbayu.app.assistant

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScreenShotHolder {

    private val state = MutableStateFlow<Bitmap?>(null)

    val screenshot: StateFlow<Bitmap?> = state.asStateFlow()

    fun update(bitmap: Bitmap) {
        state.value = bitmap
    }

    fun clear() {
        state.value = null
    }
}
