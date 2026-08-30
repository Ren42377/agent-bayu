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
        val entry = testProvider(id = "opencode", authKind = AuthKind.NONE, optionalKey = true)
        val source = FakeConnectionSource(listOf(testConnection(providerId = "opencode")), "conn-1")

        val resolution = ActiveProvider(source, catalog(entry), FakeKeys()).resolve()

        assertTrue(resolution is ActiveResolution.Ready)
        val candidate = (resolution as ActiveResolution.Ready).candidate
        assertEquals("opencode", candidate.provider.id)
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
    fun `an oauth provider needs a stored credential before it is ready`() {
        val entry = testProvider(id = "codex", authKind = AuthKind.OAUTH_DEVICE)
        val source = FakeConnectionSource(listOf(testConnection(providerId = "codex")), "conn-1")

        assertEquals(
            ActiveResolution.Unavailable(ActiveProviderProblem.MISSING_CREDENTIAL),
            ActiveProvider(source, catalog(entry), FakeKeys()).resolve()
        )

        val loggedIn = FakeKeys(mapOf("conn-1" to "access-1"))
        assertTrue(ActiveProvider(source, catalog(entry), loggedIn).resolve() is ActiveResolution.Ready)
    }

    @Test
    fun `active id wins over creation order`() {
        val connections = listOf(
            testConnection(id = "conn-old", createdAtMillis = 1L),
            testConnection(id = "conn-new", createdAtMillis = 9L)
        )

        assertEquals("conn-new", resolveActiveConnection(connections, "conn-new")?.id)
    }

    @Test
    fun `falls back to the oldest connection when the active id is unknown`() {
        val connections = listOf(
            testConnection(id = "conn-b", createdAtMillis = 900L),
            testConnection(id = "conn-a", createdAtMillis = 10L)
        )

        assertEquals("conn-a", resolveActiveConnection(connections, "missing")?.id)
    }

    @Test
    fun `breaks creation time ties by id`() {
        val connections = listOf(
            testConnection(id = "conn-z", createdAtMillis = 5L),
            testConnection(id = "conn-a", createdAtMillis = 9L),
            testConnection(id = "conn-b", createdAtMillis = 5L)
        )

        assertEquals("conn-b", resolveActiveConnection(connections, null)?.id)
    }

    @Test
    fun `resolves to null when there is no connection at all`() {
        assertNull(resolveActiveConnection(emptyList(), "conn-1"))
    }
}
