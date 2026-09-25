package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.InMemoryStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMigrationTest {

    private val provider = testProvider(
        id = "agy",
        modelsPath = "/models",
        retiredModels = listOf("gemini-2.5-pro"),
        models = listOf(
            ModelEntry(id = "gemini-3.8-flash"),
            ModelEntry(id = "gemini-3.8-flash-high", deprecated = true),
            ModelEntry(id = "gemini-3.7-flash")
        )
    )

    private fun store(connection: Connection): ConnectionStore {
        val store = ConnectionStore(InMemoryStorage(), FakeClock())
        store.upsert(connection)
        return store
    }

    private fun catalog(): ProviderCatalog = ProviderCatalog(listOf(provider))

    @Test
    fun `a saved effort variant migrates to its family and keeps the level`() {
        val id = "conn-1"
        val store = store(
            testConnection(
                id = id,
                providerId = provider.id,
                model = "gemini-3.8-flash-high"
            )
        )

        store.migrateModels(catalog())

        assertEquals("gemini-3.8-flash", store.find(id)?.model)
        assertEquals(ReasoningEffort.HIGH, store.find(id)?.effort)
    }

    @Test
    fun `a stored level is never overwritten by the old model name`() {
        val id = "conn-1"
        val store = store(
            testConnection(
                id = id,
                providerId = provider.id,
                model = "gemini-3.8-flash-high",
                effort = ReasoningEffort.LOW
            )
        )

        store.migrateModels(catalog())

        assertEquals("gemini-3.8-flash", store.find(id)?.model)
        assertEquals(ReasoningEffort.LOW, store.find(id)?.effort)
    }

    @Test
    fun `a retired model falls back to the first selectable model`() {
        val id = "conn-1"
        val store = store(
            testConnection(
                id = id,
                providerId = provider.id,
                model = "gemini-2.5-pro"
            )
        )

        store.migrateModels(catalog())

        assertEquals("gemini-3.8-flash", store.find(id)?.model)
    }

    @Test
    fun `a clean model is left untouched`() {
        val id = "conn-1"
        val store = store(
            testConnection(
                id = id,
                providerId = provider.id,
                model = "gemini-3.7-flash"
            )
        )

        store.migrateModels(catalog())

        assertEquals("gemini-3.7-flash", store.find(id)?.model)
        assertEquals(null, store.find(id)?.effort)
    }

    @Test
    fun `migrating twice changes nothing the second time`() {
        val id = "conn-1"
        val store = store(
            testConnection(
                id = id,
                providerId = provider.id,
                model = "gemini-3.8-flash-high"
            )
        )

        store.migrateModels(catalog())
        val afterFirst = store.find(id)
        store.migrateModels(catalog())

        assertEquals(afterFirst, store.find(id))
    }
}
