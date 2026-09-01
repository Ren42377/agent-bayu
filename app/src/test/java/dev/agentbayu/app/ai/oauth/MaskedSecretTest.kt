package dev.agentbayu.app.ai.oauth

import dev.agentbayu.app.ai.ProviderCatalog
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskedSecretTest {

    @Test
    fun `unmask restores a masked value`() {
        assertEquals("sample-secret-0123456789", unmaskSecret(MASKED_SAMPLE))
    }

    @Test
    fun `unmask rejects input that is not masked`() {
        assertNull(unmaskSecret(""))
        assertNull(unmaskSecret("not base64 at all"))
    }

    @Test
    fun `the bundled agy secret unmasks to an installed app shape`() {
        val config = ProviderCatalog.parse(readAsset()).find("agy")!!.browserLogin!!
        val masked = config.clientSecretMasked!!
        val secret = config.clientSecret

        assertEquals(unmaskSecret(masked), secret)
        assertNotEquals(masked, secret)
        assertTrue("client secret shape", SECRET_SHAPE.matches(secret.orEmpty()))
    }

    @Test
    fun `a provider without a masked secret has none at runtime`() {
        val config = ProviderCatalog.parse(readAsset()).find("codex")!!.deviceLogin!!

        assertNull(config.clientSecretMasked)
        assertNull(config.clientSecret)
    }

    private fun readAsset(): String {
        val candidates = listOf(
            File("src/main/assets/providers.json"),
            File("app/src/main/assets/providers.json")
        )
        val file = candidates.firstOrNull { it.exists() }
        checkNotNull(file) { "providers.json not found" }
        return file.readText()
    }

    private companion object {
        const val MASKED_SAMPLE = "HAwDGR4KWAcATgIQFkFZUh9FBVpbWVFL"
        val SECRET_SHAPE = Regex("[A-Z]{6}-[A-Za-z0-9_-]{28}")
    }
}
