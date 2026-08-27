package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable

@Serializable
data class ComboStep(
    val strategy: String = RoutingStrategies.PRIORITY,
    val connectionIds: List<String> = emptyList(),
    val tier: ProviderTier? = null
)

@Serializable
data class Combo(
    val id: String,
    val label: String,
    val steps: List<ComboStep> = emptyList(),
    val builtIn: Boolean = false
)

object BuiltInCombos {
    const val TIER_CASCADE = "tier-cascade"

    val tierCascade = Combo(
        id = TIER_CASCADE,
        label = "Tier cascade",
        builtIn = true,
        steps = listOf(
            ComboStep(strategy = RoutingStrategies.LEAST_USED, tier = ProviderTier.SUBSCRIPTION),
            ComboStep(strategy = RoutingStrategies.LEAST_USED, tier = ProviderTier.API_KEY),
            ComboStep(strategy = RoutingStrategies.LEAST_USED, tier = ProviderTier.CHEAP),
            ComboStep(strategy = RoutingStrategies.LEAST_USED, tier = ProviderTier.FREE)
        )
    )

    val all: List<Combo> = listOf(tierCascade)

    fun find(id: String): Combo? = all.firstOrNull { it.id == id }
}
