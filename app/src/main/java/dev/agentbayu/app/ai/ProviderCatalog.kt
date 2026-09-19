package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProviderCatalogFile(
    val version: Int = 1,
    val updateUrl: String? = null,
    val providers: List<ProviderEntry> = emptyList()
)

open class ProviderCatalog(
    open val providers: List<ProviderEntry>,
    open val updateUrl: String? = null
) {

    private val byId: Map<String, ProviderEntry> by lazy {
        providers.associateBy { it.id }
    }

    open fun find(providerId: String): ProviderEntry? = byId[providerId]

    open fun model(providerId: String, modelId: String): ModelEntry? =
        find(providerId)?.model(modelId)

    open fun sortedByTier(): List<ProviderEntry> = providers.sortedWith(
        compareBy({ it.tier.order }, { it.label })
    )

    companion object {
        const val DEFAULT_PROVIDER_ID = "opencode"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(raw: String): ProviderCatalog {
            val file = json.decodeFromString(ProviderCatalogFile.serializer(), raw)
            val unique = LinkedHashMap<String, ProviderEntry>()
            file.providers.forEach { unique[it.id] = it }
            return ProviderCatalog(unique.values.toList(), file.updateUrl)
        }

        fun empty(): ProviderCatalog = ProviderCatalog(emptyList())
    }
}
