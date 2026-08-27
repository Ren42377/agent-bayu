package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.EncryptedStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class RoutingConfigStore(private val storage: EncryptedStorage) : RoutingConfigSource {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    override val config: StateFlow<RoutingConfig> = state.asStateFlow()

    fun setChannel(channel: String) {
        if (state.value.channel == channel) return
        update(state.value.copy(channel = channel))
    }

    fun upsertCombo(combo: Combo) {
        val current = state.value
        val index = current.combos.indexOfFirst { it.id == combo.id }
        val combos = if (index >= 0) {
            current.combos.toMutableList().apply { set(index, combo) }
        } else {
            current.combos + combo
        }
        update(current.copy(combos = combos))
    }

    fun removeCombo(comboId: String) {
        val current = state.value
        val combos = current.combos.filterNot { it.id == comboId }
        if (combos.size == current.combos.size) return
        val channel = if (current.channel == RoutingConfig.comboChannel(comboId)) {
            AutoChannels.AUTO
        } else {
            current.channel
        }
        update(current.copy(channel = channel, combos = combos))
    }

    fun newComboId(): String = COMBO_PREFIX + (state.value.combos.size + 1).toString()

    private fun update(config: RoutingConfig) {
        state.value = config
        storage.write(FILE_NAME, json.encodeToString(RoutingConfig.serializer(), config))
    }

    private fun load(): RoutingConfig {
        val raw = storage.read(FILE_NAME) ?: return RoutingConfig()
        return try {
            json.decodeFromString(RoutingConfig.serializer(), raw)
        } catch (error: IllegalArgumentException) {
            RoutingConfig()
        }
    }

    companion object {
        const val FILE_NAME = "routing.bin"
        private const val COMBO_PREFIX = "combo-"
    }
}
