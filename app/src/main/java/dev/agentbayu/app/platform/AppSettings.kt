package dev.agentbayu.app.platform

import android.content.Context
import dev.agentbayu.app.domain.tools.ToolApprovalMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val screenContextState =
        MutableStateFlow(preferences.getBoolean(KEY_SCREEN_CONTEXT, false))

    val useScreenContext: StateFlow<Boolean> = screenContextState.asStateFlow()

    private val onboardingState =
        MutableStateFlow(!preferences.getBoolean(KEY_ONBOARDING_DONE, false))

    val onboardingVisible: StateFlow<Boolean> = onboardingState.asStateFlow()

    private val themeModeState = MutableStateFlow(readThemeMode())

    val themeMode: StateFlow<ThemeMode> = themeModeState.asStateFlow()

    private val toolApprovalModeState = MutableStateFlow(readToolApprovalMode())

    val toolApprovalMode: StateFlow<ToolApprovalMode> = toolApprovalModeState.asStateFlow()

    fun setUseScreenContext(enabled: Boolean) {
        screenContextState.value = enabled
        preferences.edit().putBoolean(KEY_SCREEN_CONTEXT, enabled).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        themeModeState.value = mode
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setToolApprovalMode(mode: ToolApprovalMode) {
        toolApprovalModeState.value = mode
        preferences.edit().putString(KEY_TOOL_APPROVAL_MODE, mode.name).apply()
    }

    private fun readThemeMode(): ThemeMode {
        val stored = preferences.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private fun readToolApprovalMode(): ToolApprovalMode {
        val stored = preferences.getString(KEY_TOOL_APPROVAL_MODE, null)
            ?: return ToolApprovalMode.ASK
        return ToolApprovalMode.entries.firstOrNull { it.name == stored } ?: ToolApprovalMode.ASK
    }

    fun completeOnboarding() {
        onboardingState.value = false
        preferences.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun defaultConnectionSeeded(): Boolean = preferences.getBoolean(KEY_DEFAULT_SEEDED, false)

    fun markDefaultConnectionSeeded() {
        preferences.edit().putBoolean(KEY_DEFAULT_SEEDED, true).apply()
    }

    private companion object {
        const val FILE_NAME = "agent_bayu_settings"
        const val KEY_SCREEN_CONTEXT = "use_screen_context"
        const val KEY_DEFAULT_SEEDED = "default_connection_seeded"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_TOOL_APPROVAL_MODE = "tool_approval_mode"
    }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
