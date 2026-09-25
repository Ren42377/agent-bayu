package dev.agentbayu.app.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CatalogRepository(initial: ProviderCatalog) : ProviderCatalog(emptyList()) {

    @Volatile
    private var delegate: ProviderCatalog = initial

    private val catalogState = MutableStateFlow(delegate)
    private val updatedState = MutableStateFlow<Long?>(null)

    val catalog: StateFlow<ProviderCatalog> = catalogState.asStateFlow()
    val lastUpdatedMillis: StateFlow<Long?> = updatedState.asStateFlow()

    override val providers: List<ProviderEntry>
        get() = delegate.providers

    override val updateUrl: String?
        get() = delegate.updateUrl

    override val version: Int
        get() = delegate.version

    override fun find(providerId: String): ProviderEntry? = delegate.find(providerId)

    override fun model(providerId: String, modelId: String): ModelEntry? =
        delegate.model(providerId, modelId)

    override fun sortedByTier(): List<ProviderEntry> = delegate.sortedByTier()

    fun publish(catalog: ProviderCatalog, updatedAtMillis: Long) {
        delegate = catalog
        catalogState.value = catalog
        updatedState.value = updatedAtMillis
    }
}
