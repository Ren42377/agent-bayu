package dev.agentbayu.app.platform

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val screenContextState =
        MutableStateFlow(preferences.getBoolean(KEY_SCREEN_CONTEXT, false))

    val useScreenContext: StateFlow<Boolean> = screenContextState.asStateFlow()

    fun setUseScreenContext(enabled: Boolean) {
        screenContextState.value = enabled
        preferences.edit().putBoolean(KEY_SCREEN_CONTEXT, enabled).apply()
    }

    fun defaultConnectionSeeded(): Boolean = preferences.getBoolean(KEY_DEFAULT_SEEDED, false)

    fun markDefaultConnectionSeeded() {
        preferences.edit().putBoolean(KEY_DEFAULT_SEEDED, true).apply()
    }

    private companion object {
        const val FILE_NAME = "agent_bayu_settings"
        const val KEY_SCREEN_CONTEXT = "use_screen_context"
        const val KEY_DEFAULT_SEEDED = "default_connection_seeded"
    }
}
