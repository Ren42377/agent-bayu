package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.InMemoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionStoreTest {

    private fun store(): ConnectionStore = ConnectionStore(InMemoryStorage(), FakeClock(7L))

    @Test
    fun `discovered models replace cleanly and skip duplicates`() {
        val store = store()
        store.upsert(Connection(id = "conn-1", providerId = "agy", label = "A", model = "m"))

        store.setDiscoveredModels("conn-1", listOf(" a ", "b", "", "a"))
        assertEquals(listOf("a", "b"), store.find("conn-1")?.discoveredModels)

        store.setDiscoveredModels("conn-1", listOf("a", "b"))
        assertEquals(listOf("a", "b"), store.find("conn-1")?.discoveredModels)

        store.setDiscoveredModels("missing", listOf("a"))
        assertNull(store.find("missing"))
    }

    @Test
    fun `custom models append without duplicates and can be removed`() {
        val store = store()
        store.upsert(Connection(id = "conn-1", providerId = "codex", label = "C", model = "m"))

        store.addCustomModel("conn-1", " my-model ")
        store.addCustomModel("conn-1", "my-model")
        store.addCustomModel("conn-1", "   ")
        assertEquals(listOf("my-model"), store.find("conn-1")?.customModels)

        store.addCustomModel("conn-1", "other")
        store.removeCustomModel("conn-1", "my-model")
        assertEquals(listOf("other"), store.find("conn-1")?.customModels)

        store.removeCustomModel("conn-1", "absent")
        assertEquals(listOf("other"), store.find("conn-1")?.customModels)
    }

    @Test
    fun `context override keeps positive values and drops the rest`() {
        val store = store()
        store.upsert(Connection(id = "conn-1", providerId = "codex", label = "C", model = "m"))

        store.setContextLength("conn-1", 200_000)
        assertEquals(200_000, store.find("conn-1")?.contextLengthOverride)

        store.setContextLength("conn-1", 0)
        assertNull(store.find("conn-1")?.contextLengthOverride)

        store.setContextLength("conn-1", -5)
        assertNull(store.find("conn-1")?.contextLengthOverride)
    }
}
