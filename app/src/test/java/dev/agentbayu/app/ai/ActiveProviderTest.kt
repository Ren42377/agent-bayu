package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveProviderTest {

    private fun catalog(vararg providers: ProviderEntry) = ProviderCatalog(providers.toList())

    @Test
    fun `reports no connection when the list is empty`() {
        val provider = ActiveProvider(FakeConnectionSource(emptyList()), catalog(testProvider()), FakeKeys())

        val resolution = provider.resolve()

        assertEquals(
            ActiveResolution.Unavailable(ActiveProviderProblem.NO_CONNECTION),
            resolution
        )
    }

    @Test
    fun `reports no connection when every connection is disabled`() {
        val source = FakeConnectionSource(listOf(testConnection(enabled = false)), "conn-1")
        val provider = ActiveProvider(source, catalog(testProvider()), FakeKeys(mapOf("conn-1" to "key")))

        assertEquals(
            ActiveResolution.Unavailable(ActiveProviderProblem.NO_CONNECTION),
            provider.resolve()
        )
    }

    @Test
    fun `reports unknown provider when the catalog has no entry`() {
        val source = FakeConnectionSource(listOf(testConnection(providerId = "ghost")), "conn-1")
        val provider = ActiveProvider(source, catalog(testProvider()), FakeKeys())

        assertEquals(
            ActiveResolution.Unavailable(ActiveProviderProblem.UNKNOWN_PROVIDER),
            provider.resolve()
        )
    }

    @Test
    fun `reports missing credential when a key provider has no key`() {
        val source = FakeConnectionSource(listOf(testConnection()), "conn-1")
        val provider = ActiveProvider(source, catalog(testProvider()), FakeKeys())

        assertEquals(
            ActiveResolution.Unavailable(ActiveProviderProblem.MISSING_CREDENTIAL),
            provider.resolve()
        )
    }

    @Test
    fun `resolves a keyless provider without any credential`() {
        val entry = testProvider(id = "kilocode", authKind = AuthKind.NONE, optionalKey = true)
        val source = FakeConnectionSource(listOf(testConnection(providerId = "kilocode")), "conn-1")

        val resolution = ActiveProvider(source, catalog(entry), FakeKeys()).resolve()

        assertTrue(resolution is ActiveResolution.Ready)
        val candidate = (resolution as ActiveResolution.Ready).candidate
        assertEquals("kilocode", candidate.provider.id)
        assertEquals("model-a", candidate.model.id)
    }

    @Test
    fun `resolves a key provider once the key is present`() {
        val source = FakeConnectionSource(listOf(testConnection()), "conn-1")
        val keys = FakeKeys(mapOf("conn-1" to "key-1234"))

        val resolution = ActiveProvider(source, catalog(testProvider()), keys).resolve()

        assertTrue(resolution is ActiveResolution.Ready)
        assertEquals("conn-1", (resolution as ActiveResolution.Ready).candidate.connection.id)
    }

    @Test
    fun `falls back to a model entry that is not in the catalog`() {
        val source = FakeConnectionSource(listOf(testConnection(model = "custom-model")), "conn-1")
        val keys = FakeKeys(mapOf("conn-1" to "key-1234"))

        val resolution = ActiveProvider(source, catalog(testProvider()), keys).resolve()

        val candidate = (resolution as ActiveResolution.Ready).candidate
        assertEquals("custom-model", candidate.model.id)
        assertEquals(ModelEntry.DEFAULT_CONTEXT_LENGTH, candidate.model.contextLength)
    }

    @Test
    fun `active id wins over priority`() {
        val connections = listOf(
            testConnection(id = "conn-low", priority = 10),
            testConnection(id = "conn-high", priority = 900)
        )

        assertEquals("conn-high", resolveActiveConnection(connections, "conn-high")?.id)
    }

    @Test
    fun `falls back to the lowest priority when the active id is unknown`() {
        val connections = listOf(
            testConnection(id = "conn-b", priority = 900),
            testConnection(id = "conn-a", priority = 10)
        )

        assertEquals("conn-a", resolveActiveConnection(connections, "missing")?.id)
    }

    @Test
    fun `falls back past a disabled active connection`() {
        val connections = listOf(
            testConnection(id = "conn-off", enabled = false),
            testConnection(id = "conn-on")
        )

        assertEquals("conn-on", resolveActiveConnection(connections, "conn-off")?.id)
    }

    @Test
    fun `breaks priority ties by creation time then id`() {
        val connections = listOf(
            testConnection(id = "conn-z", createdAtMillis = 5L),
            testConnection(id = "conn-a", createdAtMillis = 9L),
            testConnection(id = "conn-b", createdAtMillis = 5L)
        )

        assertEquals("conn-b", resolveActiveConnection(connections, null)?.id)
    }

    @Test
    fun `resolves to null when there is nothing enabled`() {
        assertNull(resolveActiveConnection(emptyList(), "conn-1"))
    }
}
