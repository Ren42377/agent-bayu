package dev.agentbayu.app.ai

fun planTypeOf(credential: Credential?, provider: ProviderEntry?): String? {
    val tokens = credential as? Credential.OAuthTokens ?: return null
    val field = provider?.oauth?.planField?.takeIf { it.isNotBlank() } ?: return null
    return tokens.extras[field]
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
}

fun isModelAccessible(model: ModelEntry, planType: String?): Boolean {
    if (model.plans.isEmpty()) return true
    val plan = planType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
    return plan in model.plans
}

fun pickerModelIds(
    provider: ProviderEntry?,
    connection: Connection,
    planType: String? = null
): List<String> {
    if (provider == null) return emptyList()
    val discoveryFirst = provider.modelsPath != null && connection.discoveredModels.isNotEmpty()
    val base = if (discoveryFirst) {
        connection.discoveredModels +
            connection.customModels +
            listOfNotNull(connection.model.takeIf { it.isNotBlank() })
    } else {
        provider.pickerModelIds(
            connection.discoveredModels + connection.customModels,
            connection.model
        )
    }
    return base
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .filter { id -> isModelAccessible(provider.model(id) ?: ModelEntry(id = id), planType) }
        .toList()
}
