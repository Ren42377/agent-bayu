package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class RoutingConfig(
    val version: Int = 1,
    val channel: String = AutoChannels.AUTO,
    val combos: List<Combo> = emptyList()
) {
    fun combo(id: String): Combo? =
        combos.firstOrNull { it.id == id } ?: BuiltInCombos.find(id)

    fun availableCombos(): List<Combo> = BuiltInCombos.all + combos

    companion object {
        const val COMBO_PREFIX = "combo:"
        const val CONNECTION_PREFIX = "connection:"

        fun comboChannel(comboId: String): String = COMBO_PREFIX + comboId

        fun connectionChannel(connectionId: String): String = CONNECTION_PREFIX + connectionId

        fun comboIdOf(channel: String): String? =
            if (channel.startsWith(COMBO_PREFIX)) channel.removePrefix(COMBO_PREFIX) else null

        fun connectionIdOf(channel: String): String? =
            if (channel.startsWith(CONNECTION_PREFIX)) channel.removePrefix(CONNECTION_PREFIX) else null
    }
}
