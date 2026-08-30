package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.OpenAiCompatibleAdapter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionTesterTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(modelIdFilter: String? = "-free"): ProviderEntry = testProvider(
        id = "opencode",
        authKind = AuthKind.NONE,
        optionalKey = true,
        tier = ProviderTier.FREE,
        modelsPath = "/models",
        modelIdFilter = modelIdFilter,
        models = listOf(ModelEntry(id = "a-free"))
    )

    private fun connection(): Connection = testConnection(
        providerId = "opencode",
        model = "a-free",
        baseUrlOverride = server.url("/v1").toString()
    )

    private fun tester(provider: ProviderEntry): ConnectionTester = ConnectionTester(
        client = client,
        catalog = ProviderCatalog(listOf(provider)),
        credentials = KeySourceCredentials(FakeKeys()),
        adapters = mapOf<WireFormat, ChatAdapter>(
            WireFormat.OPENAI to OpenAiCompatibleAdapter(client)
        )
    )

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun sseResponse(vararg chunks: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(chunks.joinToString("") { chunk -> "data: " + chunk + "\n\n" })

    @Test
    fun `fetchModels drops ids that miss the filter token`() {
        server.enqueue(
            jsonResponse(
                "{\"data\":[{\"id\":\"z-free\"},{\"id\":\"premium-large\"},{\"id\":\"a-free\"}]}"
            )
        )

        val result = runBlocking { tester(provider()).fetchModels(connection()) }

        assertTrue(result is ModelFetchResult.Success)
        assertEquals(listOf("a-free", "z-free"), (result as ModelFetchResult.Success).models)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun `fetchModels keeps every id when the provider has no filter`() {
        server.enqueue(jsonResponse("{\"data\":[{\"id\":\"b-free\"},{\"id\":\"premium-large\"}]}"))

        val result = runBlocking { tester(provider(modelIdFilter = null)).fetchModels(connection()) }

        assertEquals(
            listOf("b-free", "premium-large"),
            (result as ModelFetchResult.Success).models
        )
    }

    @Test
    fun `fetchModels reads a models array and strips the model prefix`() {
        server.enqueue(
            jsonResponse(
                "{\"models\":[{\"name\":\"models/one-free\"},{\"name\":\"models/two-paid\"}]}"
            )
        )

        val result = runBlocking { tester(provider()).fetchModels(connection()) }

        assertEquals(listOf("one-free"), (result as ModelFetchResult.Success).models)
    }

    @Test
    fun `fetchModels sends an http error through the classifier`() {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "3").setBody("slow down")
        )

        val result = runBlocking { tester(provider()).fetchModels(connection()) }

        val failure = (result as ModelFetchResult.Failure).failure
        assertEquals(FailureKind.COOLDOWN, failure.kind)
        assertEquals(429, failure.statusCode)
        assertEquals(3_000L, failure.retryAfterMillis)
    }

    @Test
    fun `fetchModels reports an unsupported provider when there is no models path`() {
        val result = runBlocking {
            tester(provider().copy(modelsPath = null)).fetchModels(connection())
        }

        assertEquals(FailureKind.TERMINAL, (result as ModelFetchResult.Failure).failure.kind)
    }

    @Test
    fun `probeModels returns one result per model including the dead ones`() {
        server.enqueue(sseResponse("{\"choices\":[{\"delta\":{\"content\":\"pong\"}}]}", "[DONE]"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("model unavailable"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("overloaded"))

        val results = runBlocking {
            tester(provider()).probeModels(
                connection = connection(),
                modelIds = listOf("a-free", "b-free", "c-free")
            )
        }

        assertEquals(listOf("a-free", "b-free", "c-free"), results.keys.toList())
        assertTrue(results["a-free"] is ConnectionTestResult.Success)
        assertEquals(
            "a-free",
            (results.getValue("a-free") as ConnectionTestResult.Success).model
        )
        assertEquals(
            FailureKind.MODEL_LOCK,
            (results.getValue("b-free") as ConnectionTestResult.Failure).failure.kind
        )
        assertEquals(
            FailureKind.RETRYABLE,
            (results.getValue("c-free") as ConnectionTestResult.Failure).failure.kind
        )
    }

    @Test
    fun `probe requests carry the model under test`() {
        server.enqueue(sseResponse("{\"choices\":[{\"delta\":{\"content\":\"pong\"}}]}", "[DONE]"))

        runBlocking {
            tester(provider()).probeModels(
                connection = connection(),
                modelIds = listOf("only-free")
            )
        }

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"only-free\""))
    }
}
