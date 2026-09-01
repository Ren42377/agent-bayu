package dev.agentbayu.app.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ReasoningEffort(val wireValue: String, val label: String) {
    @SerialName("low")
    LOW("low", "Low"),

    @SerialName("medium")
    MEDIUM("medium", "Medium"),

    @SerialName("high")
    HIGH("high", "High"),

    @SerialName("xhigh")
    XHIGH("xhigh", "XHigh"),

    @SerialName("max")
    MAX("max", "Max");

    companion object {
        fun fromWire(value: String?): ReasoningEffort? {
            val trimmed = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.wireValue == trimmed }
        }
    }
}

@Serializable
enum class EffortMode {
    @SerialName("none")
    NONE,

    @SerialName("model_suffix")
    MODEL_SUFFIX,

    @SerialName("request_field")
    REQUEST_FIELD;

    val isActive: Boolean
        get() = this != NONE
}
