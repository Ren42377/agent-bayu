package dev.agentbayu.app.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.agentbayu.app.R

enum class AgentBayuDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int
) {
    CHAT(R.string.tab_chat, R.drawable.ic_chat),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings)
}
