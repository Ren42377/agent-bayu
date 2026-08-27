package dev.agentbayu.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.agentbayu.app.R

enum class AgentBayuDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    CHAT("chat", R.string.tab_chat, R.drawable.ic_chat),
    SETUP("setup", R.string.tab_setup, R.drawable.ic_setup),
    SETTINGS("settings", R.string.tab_settings, R.drawable.ic_settings)
}
