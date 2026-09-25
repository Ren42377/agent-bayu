package dev.agentbayu.app.ai

import dev.agentbayu.app.platform.InMemoryStorage
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogUpdaterTest {

    private lateinit var server: MockWebServer
    private val storage = InMemoryStorage()
    private val clock = FakeClock(1_000L)
    private val updater = CatalogUpdater(OkHttpClient(), storage, clock)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `an older remote catalog is rejected and the cache is dropped`() = runBlocking {
        val strict = CatalogUpdater(
            client = OkHttpClient(),
            storage = storage,
            clock = clock,
            minimumVersion = 2
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(catalogBody())
        )

        val result = strict.fetch(server.url("/p.json").toString())

        assertEquals(
            "catalog version is older than bundled",
            (result as CatalogUpdateResult.Failure).message
        )

        storage.write(CatalogUpdater.CATALOG_FILE, catalogBody())
        storage.write(CatalogUpdater.CATALOG_TIME_FILE, "1000")

        assertNull(strict.restore())
        assertNull(storage.read(CatalogUpdater.CATALOG_FILE))
        assertNull(storage.read(CatalogUpdater.CATALOG_TIME_FILE))
    }

    @Test
    fun `the bundled version is parsed from the catalog file`() {
        val catalog = ProviderCatalog.parse("{\"version\": 2, \"providers\": []}")

        assertEquals(2, catalog.version)
        assertEquals(1, ProviderCatalog.parse(catalogBody()).version)
    }

    private fun catalogBody(updateUrl: String? = null): String {
        val urlField = updateUrl?.let { "\"updateUrl\": \"$it\"," }.orEmpty()
        return """
        {
          "version": 1,
          ${urlField}
          "providers": [
            {"id": "remote", "label": "Remote", "wireFormat": "openai",
             "baseUrl": "https://remote.test/v1", "tier": "free",
             "models": [{"id": "remote-mini", "contextLength": 8192, "maxOutputTokens": 2048}]}
          ]
        }
        """.trimIndent()
    }

    @Test
    fun `fetch replaces the catalog with the remote providers`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(catalogBody())
        )

        val result = updater.fetch(server.url("/providers.json").toString())

        val catalog = (result as CatalogUpdateResult.Success).catalog
        assertEquals("remote", catalog.providers.single().id)
        assertEquals(1_000L, result.fetchedAtMillis)
        assertEquals("/providers.json", server.takeRequest().path)
    }

    @Test
    fun `a fetched catalog survives a restart through the cache`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(catalogBody())
        )
        updater.fetch(server.url("/providers.json").toString())

        val restored = updater.restore()

        val catalog = (restored as CatalogUpdateResult.Success).catalog
        assertEquals("remote", catalog.providers.single().id)
        assertEquals(1_000L, restored.fetchedAtMillis)
    }

    @Test
    fun `a broken payload keeps the current catalog in place`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertEquals(
            "http 500",
            (updater.fetch(server.url("/p.json").toString()) as CatalogUpdateResult.Failure).message
        )

        server.enqueue(MockResponse().setBody("not json at all"))
        assertEquals(
            "malformed catalog",
            (updater.fetch(server.url("/p.json").toString()) as CatalogUpdateResult.Failure).message
        )

        assertNull(updater.restore())
    }

    @Test
    fun `a remote catalog without providers is rejected`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"version\": 1, \"providers\": []}")
        )

        val result = updater.fetch(server.url("/p.json").toString())

        assertEquals("catalog has no providers", (result as CatalogUpdateResult.Failure).message)
    }

    @Test
    fun `the repository publishes and exposes the freshest catalog`() {
        val repository = CatalogRepository(ProviderCatalog.empty())
        val fresh = ProviderCatalog.parse(catalogBody())

        repository.publish(fresh, 5_000L)

        assertEquals("remote", repository.find("remote")?.id)
        assertEquals("remote", repository.catalog.value.find("remote")?.id)
        assertEquals(5_000L, repository.lastUpdatedMillis.value)
        assertTrue(repository.providers.isNotEmpty())
        assertNull(ProviderCatalog.empty().updateUrl)
    }
}
