package dev.agentbayu.app.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProviderCatalogFile(
    val version: Int = 1,
    val providers: List<ProviderEntry> = emptyList()
)

class ProviderCatalog(val providers: List<ProviderEntry>) {

    private val byId: Map<String, ProviderEntry> = providers.associateBy { it.id }

    fun find(providerId: String): ProviderEntry? = byId[providerId]

    fun model(providerId: String, modelId: String): ModelEntry? = find(providerId)?.model(modelId)

    fun sortedByTier(): List<ProviderEntry> = providers.sortedWith(
        compareBy({ it.tier.order }, { it.label })
    )

    companion object {
        const val DEFAULT_PROVIDER_ID = "kilocode"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(raw: String): ProviderCatalog {
            val file = json.decodeFromString(ProviderCatalogFile.serializer(), raw)
            val unique = LinkedHashMap<String, ProviderEntry>()
            file.providers.forEach { unique[it.id] = it }
            return ProviderCatalog(unique.values.toList())
        }

        fun empty(): ProviderCatalog = ProviderCatalog(emptyList())
    }
}
