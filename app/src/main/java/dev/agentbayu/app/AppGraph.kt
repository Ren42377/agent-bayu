package dev.agentbayu.app

import android.content.Context
import dev.agentbayu.app.ai.AiRouter
import dev.agentbayu.app.ai.CircuitBreaker
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.ConnectionStore
import dev.agentbayu.app.ai.ConnectionTester
import dev.agentbayu.app.ai.CooldownRegistry
import dev.agentbayu.app.ai.CredentialVault
import dev.agentbayu.app.ai.ModelLockout
import dev.agentbayu.app.ai.ProviderCatalog
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.ResilienceGate
import dev.agentbayu.app.ai.RoutingConfigStore
import dev.agentbayu.app.ai.UsageTracker
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.adapter.AnthropicAdapter
import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.GeminiAdapter
import dev.agentbayu.app.ai.adapter.OpenAiCompatibleAdapter
import dev.agentbayu.app.domain.ChatController
import dev.agentbayu.app.domain.ContextBuilder
import dev.agentbayu.app.domain.ConversationRepository
import dev.agentbayu.app.domain.ConversationStore
import dev.agentbayu.app.domain.RouterAgentEngine
import dev.agentbayu.app.domain.RouterCopy
import dev.agentbayu.app.platform.AppSettings
import dev.agentbayu.app.platform.SecureStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

object AppGraph {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val conversation = ConversationRepository()
    private val clock: Clock = RealClock

    @Volatile
    private var container: Container? = null

    private class Container(
        val chatController: ChatController,
        val conversationStore: ConversationStore,
        val connectionStore: ConnectionStore,
        val routingConfigStore: RoutingConfigStore,
        val credentialVault: CredentialVault,
        val catalog: ProviderCatalog,
        val router: AiRouter,
        val tester: ConnectionTester,
        val usageTracker: UsageTracker
    )

    @Volatile
    private var appSettings: AppSettings? = null

    fun chat(context: Context): ChatController = container(context).chatController

    fun connections(context: Context): ConnectionStore = container(context).connectionStore

    fun routingConfig(context: Context): RoutingConfigStore = container(context).routingConfigStore

    fun credentials(context: Context): CredentialVault = container(context).credentialVault

    fun catalog(context: Context): ProviderCatalog = container(context).catalog

    fun router(context: Context): AiRouter = container(context).router

    fun connectionTester(context: Context): ConnectionTester = container(context).tester

    fun usage(context: Context): UsageTracker = container(context).usageTracker

    fun conversationStore(context: Context): ConversationStore = container(context).conversationStore

    fun settings(context: Context): AppSettings {
        appSettings?.let { return it }
        return synchronized(this) {
            appSettings ?: AppSettings(context).also { appSettings = it }
        }
    }

    private fun container(context: Context): Container {
        container?.let { return it }
        return synchronized(this) {
            container ?: build(context.applicationContext).also { container = it }
        }
    }

    private fun build(context: Context): Container {
        val secureStore = SecureStore(context)
        val catalog = loadCatalog(context)
        val credentialVault = CredentialVault(secureStore)
        val connectionStore = ConnectionStore(secureStore, clock)
        val routingConfigStore = RoutingConfigStore(secureStore)
        val usageTracker = UsageTracker(clock)
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val adapters: Map<WireFormat, ChatAdapter> = mapOf(
            WireFormat.OPENAI to OpenAiCompatibleAdapter(client),
            WireFormat.ANTHROPIC to AnthropicAdapter(client),
            WireFormat.GEMINI to GeminiAdapter(client)
        )
        val gate = ResilienceGate(
            breaker = CircuitBreaker(clock),
            cooldown = CooldownRegistry(clock),
            lockout = ModelLockout(clock)
        )
        val router = AiRouter(
            catalog = catalog,
            connectionSource = connectionStore,
            keySource = credentialVault,
            configSource = routingConfigStore,
            gate = gate,
            usageTracker = usageTracker,
            adapters = adapters,
            clock = clock
        )
        val engine = RouterAgentEngine(
            router = router,
            contextBuilder = ContextBuilder(
                systemPrompt = context.getString(R.string.agent_system_prompt),
                screenContextTemplate = context.getString(R.string.agent_screen_context_prompt)
            ),
            copy = RouterCopy(
                noConnection = context.getString(R.string.agent_no_connection),
                noCandidateAvailable = context.getString(R.string.agent_no_candidate),
                allCandidatesFailed = context.getString(R.string.agent_all_failed)
            )
        )
        val conversationStore = ConversationStore(secureStore)
        conversationStore.attach(scope, conversation)
        val chatController = ChatController(
            repository = conversation,
            engine = engine,
            errorReply = context.getString(R.string.agent_error_reply),
            scope = scope
        )
        return Container(
            chatController = chatController,
            conversationStore = conversationStore,
            connectionStore = connectionStore,
            routingConfigStore = routingConfigStore,
            credentialVault = credentialVault,
            catalog = catalog,
            router = router,
            tester = ConnectionTester(client, catalog, credentialVault, adapters, clock),
            usageTracker = usageTracker
        )
    }

    private fun loadCatalog(context: Context): ProviderCatalog = try {
        context.assets.open(CATALOG_ASSET).use { stream ->
            ProviderCatalog.parse(stream.readBytes().toString(Charsets.UTF_8))
        }
    } catch (error: Exception) {
        ProviderCatalog.empty()
    }

    private const val CATALOG_ASSET = "providers.json"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
}
