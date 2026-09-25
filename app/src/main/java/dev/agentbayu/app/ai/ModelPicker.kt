package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.JwtClaims

fun planTypeOf(credential: Credential?, provider: ProviderEntry?): String? {
    val tokens = credential as? Credential.OAuthTokens ?: return null
    val config = provider?.oauth ?: return null
    val field = config.planField?.takeIf { it.isNotBlank() } ?: return null
    tokens.extras[field]
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    val claim = config.accountClaim?.takeIf { it.isNotBlank() } ?: return null
    val token = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
    return JwtClaims.claim(token, claim, field)
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
}

fun isModelAccessible(model: ModelEntry, planType: String?): Boolean {
    if (model.plans.isEmpty()) return true
    val plan = planType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return true
    return plan in model.plans
}

fun isSelectableModel(provider: ProviderEntry, modelId: String): Boolean {
    val entry = provider.model(modelId) ?: return false
    return !entry.deprecated
}

fun normalizeDiscoveredModelId(provider: ProviderEntry, rawId: String): String {
    var candidate = rawId.trim().substringBefore('(').trim()
    if (candidate.isEmpty()) return rawId.trim()
    if (isSelectableModel(provider, candidate)) return candidate
    while (true) {
        val token = FAMILY_TOKENS.firstOrNull { candidate.endsWith(it) } ?: return candidate
        val stripped = candidate.removeSuffix(token)
        if (stripped.isEmpty()) return candidate
        if (isSelectableModel(provider, stripped)) return stripped
        candidate = stripped
    }
}

fun pickerModelIds(
    provider: ProviderEntry?,
    connection: Connection,
    planType: String? = null
): List<String> {
    if (provider == null) return emptyList()
    val current = connection.model.trim()
    val base = if (provider.modelsPath != null && connection.discoveredModels.isNotEmpty()) {
        connection.discoveredModels
            .map { normalizeDiscoveredModelId(provider, it) }
            .filter { it.isNotEmpty() }
            .filter { id -> id == current || !isDeprecatedModel(provider, id) }
            .plus(connection.customModels)
            .plus(listOfNotNull(current.takeIf { it.isNotBlank() }))
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

fun accountEmailOf(credential: Credential?): String? {
    val tokens = credential as? Credential.OAuthTokens ?: return null
    return tokens.extras[Credential.EMAIL_EXTRA]
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun isDeprecatedModel(provider: ProviderEntry, modelId: String): Boolean =
    provider.model(modelId)?.deprecated == true

private val FAMILY_TOKENS = listOf(
    "-extra-low",
    "-tiered",
    "-low",
    "-medium",
    "-high",
    "-minimal"
)
