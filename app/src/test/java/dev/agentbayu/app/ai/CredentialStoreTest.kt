package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.InMemoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialStoreTest {

    @Test
    fun `stores and reads an api key`() {
        val storage = InMemoryStorage()
        val store = CredentialStore(storage)

        store.putApiKey("conn-1", "  secret-key-1234  ")

        assertEquals("secret-key-1234", store.key("conn-1"))
        assertTrue(store.hasKey("conn-1"))
        assertEquals("****1234", store.hint("conn-1"))
        assertEquals(Credential.ApiKey("secret-key-1234"), store.credential("conn-1"))
    }

    @Test
    fun `blank api key removes the entry`() {
        val storage = InMemoryStorage()
        val store = CredentialStore(storage)
        store.putApiKey("conn-1", "secret-key-1234")

        store.putApiKey("conn-1", "   ")

        assertNull(store.key("conn-1"))
        assertFalse(store.hasKey("conn-1"))
        assertNull(store.hint("conn-1"))
        assertNull(storage.read(CredentialStore.FILE_NAME))
    }

    @Test
    fun `stores oauth tokens and exposes the access token as the secret`() {
        val storage = InMemoryStorage()
        val tokens = Credential.OAuthTokens(
            accessToken = "access-abcd",
            refreshToken = "refresh-efgh",
            expiresAtMillis = 5_000L,
            extras = mapOf("copilot" to "copilot-token")
        )

        CredentialStore(storage).put("conn-1", tokens)
        val restored = CredentialStore(storage).credential("conn-1")

        assertEquals(tokens, restored)
        assertEquals("access-abcd", CredentialStore(storage).key("conn-1"))
    }

    @Test
    fun `survives a round trip through storage`() {
        val storage = InMemoryStorage()
        val first = CredentialStore(storage)
        first.putApiKey("conn-1", "key-one-1111")
        first.put("conn-2", Credential.OAuthTokens(accessToken = "token-two-2222"))

        val second = CredentialStore(storage)

        assertEquals("key-one-1111", second.key("conn-1"))
        assertEquals("token-two-2222", second.key("conn-2"))
    }

    @Test
    fun `removing one entry keeps the others`() {
        val storage = InMemoryStorage()
        val store = CredentialStore(storage)
        store.putApiKey("conn-1", "key-one-1111")
        store.putApiKey("conn-2", "key-two-2222")

        store.remove("conn-1")

        assertNull(store.key("conn-1"))
        assertEquals("key-two-2222", store.key("conn-2"))
        assertEquals("key-two-2222", CredentialStore(storage).key("conn-2"))
    }

    @Test
    fun `migrates a legacy flat map into api key credentials`() {
        val storage = InMemoryStorage()
        storage.write(
            CredentialStore.FILE_NAME,
            "{\"conn-1\":\"legacy-key-9999\",\"conn-2\":\"\"}"
        )

        val store = CredentialStore(storage)

        assertEquals("legacy-key-9999", store.key("conn-1"))
        assertEquals(Credential.ApiKey("legacy-key-9999"), store.credential("conn-1"))
        assertNull(store.key("conn-2"))
        assertTrue(storage.read(CredentialStore.FILE_NAME)!!.contains("entries"))
        assertEquals("legacy-key-9999", CredentialStore(storage).key("conn-1"))
    }

    @Test
    fun `ignores unreadable content`() {
        val storage = InMemoryStorage()
        storage.write(CredentialStore.FILE_NAME, "not json at all")

        assertNull(CredentialStore(storage).key("conn-1"))
    }

    @Test
    fun `secret falls back to the provider anonymous key`() {
        val store = CredentialStore(InMemoryStorage())
        val provider = testProvider(
            id = "aihorde",
            authKind = AuthKind.NONE,
            optionalKey = true,
            anonymousKey = "0000000000"
        )
        val connection = testConnection(providerId = "aihorde")

        assertEquals("0000000000", store.secretFor(connection, provider))

        store.putApiKey(connection.id, "real-key-4321")

        assertEquals("real-key-4321", store.secretFor(connection, provider))
    }

    @Test
    fun `secret stays null when nothing is stored and there is no anonymous key`() {
        val store = CredentialStore(InMemoryStorage())

        assertNull(store.secretFor(testConnection(), testProvider()))
    }

    @Test
    fun `hint masks short keys entirely`() {
        val store = CredentialStore(InMemoryStorage())
        store.putApiKey("conn-1", "abcd")

        assertEquals("****", store.hint("conn-1"))
    }
}
