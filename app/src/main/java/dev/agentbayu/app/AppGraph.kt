package dev.agentbayu.app

import android.content.Context
import dev.agentbayu.app.ai.ActiveProvider
import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.Connection
import dev.agentbayu.app.ai.ConnectionHealth
import dev.agentbayu.app.ai.ConnectionStore
import dev.agentbayu.app.ai.ConnectionTester
import dev.agentbayu.app.ai.CredentialStore
import dev.agentbayu.app.ai.LogStore
import dev.agentbayu.app.ai.ProviderCatalog
import dev.agentbayu.app.ai.RealClock
import dev.agentbayu.app.ai.StoredCredentials
import dev.agentbayu.app.ai.UsageTracker
import dev.agentbayu.app.ai.WireFormat
import dev.agentbayu.app.ai.adapter.AnthropicAdapter
import dev.agentbayu.app.ai.adapter.AntigravityAdapter
import dev.agentbayu.app.ai.adapter.ChatAdapter
import dev.agentbayu.app.ai.adapter.GeminiAdapter
import dev.agentbayu.app.ai.adapter.OpenAiCompatibleAdapter
import dev.agentbayu.app.ai.adapter.OpenAiResponsesAdapter
import dev.agentbayu.app.ai.oauth.AntigravityProjectBootstrap
import dev.agentbayu.app.ai.oauth.CodexDeviceFlow
import dev.agentbayu.app.ai.oauth.GoogleCodeFlow
import dev.agentbayu.app.ai.oauth.TokenRefresher
import dev.agentbayu.app.domain.ChatController
import dev.agentbayu.app.domain.ContextBuilder
import dev.agentbayu.app.domain.ConversationRepository
import dev.agentbayu.app.domain.ConversationSessionManager
import dev.agentbayu.app.domain.ConversationStore
import dev.agentbayu.app.domain.ProviderAgentEngine
import dev.agentbayu.app.domain.ProviderCopy
import dev.agentbayu.app.platform.AppSettings
import dev.agentbayu.app.platform.SecureStore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object AppGraph {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val warmUpDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val conversation = ConversationRepository()
    private val clock: Clock = RealClock
    private val readinessState = MutableStateFlow(false)

    val readiness: StateFlow<Boolean> = readinessState.asStateFlow()

    @Volatile
    private var container: Container? = null

    fun warmUp(context: Context) {
        if (container != null) {
            readinessState.value = true
            return
        }
        scope.launch(warmUpDispatcher) {
            synchronized(this@AppGraph) {
                if (container == null) {
                    settings(context.applicationContext)
                    container = build(context.applicationContext)
                }
            }
            container?.credentialStore?.preload()
            readinessState.value = true
        }
    }

    private class Container(
        val chatController: ChatController,
        val sessionManager: ConversationSessionManager,
        val connectionStore: ConnectionStore,
        val credentialStore: CredentialStore,
        val catalog: ProviderCatalog,
        val activeProvider: ActiveProvider,
        val tester: ConnectionTester,
        val deviceFlow: CodexDeviceFlow,
        val codeFlow: GoogleCodeFlow,
        val projectBootstrap: AntigravityProjectBootstrap,
        val usageTracker: UsageTracker,
        val logStore: LogStore
    )

    @Volatile
    private var appSettings: AppSettings? = null

    fun chat(context: Context): ChatController = container(context).chatController

    fun sessions(context: Context): ConversationSessionManager =
        container(context).sessionManager

    fun connections(context: Context): ConnectionStore = container(context).connectionStore

    fun credentials(context: Context): CredentialStore = container(context).credentialStore

    fun catalog(context: Context): ProviderCatalog = container(context).catalog

    fun activeProvider(context: Context): ActiveProvider = container(context).activeProvider

    fun connectionTester(context: Context): ConnectionTester = container(context).tester

    fun deviceFlow(context: Context): CodexDeviceFlow = container(context).deviceFlow

    fun codeFlow(context: Context): GoogleCodeFlow = container(context).codeFlow

    fun projectBootstrap(context: Context): AntigravityProjectBootstrap =
        container(context).projectBootstrap

    fun usage(context: Context): UsageTracker = container(context).usageTracker

    fun logs(context: Context): LogStore = container(context).logStore

    fun settings(context: Context): AppSettings {
        appSettings?.let { return it }
        return synchronized(this) {
            appSettings ?: AppSettings(context).also { appSettings = it }
        }
    }

    private fun container(context: Context): Container {
        container?.let { return it }
        return synchronized(this) {
            container ?: build(context.applicationContext).also {
                container = it
                readinessState.value = true
            }
        }
    }

    private fun build(context: Context): Container {
        val secureStore = SecureStore(context)
        val catalog = loadCatalog(context)
        val credentialStore = CredentialStore(secureStore)
        val connectionStore = ConnectionStore(secureStore, clock)
        val usageTracker = UsageTracker(clock)
        val logStore = LogStore(clock)
        seedDefaultConnection(context, catalog, connectionStore)
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val adapters: Map<WireFormat, ChatAdapter> = mapOf(
            WireFormat.OPENAI to OpenAiCompatibleAdapter(client),
            WireFormat.OPENAI_RESPONSES to OpenAiResponsesAdapter(client),
            WireFormat.ANTHROPIC to AnthropicAdapter(client),
            WireFormat.GEMINI to GeminiAdapter(client),
            WireFormat.ANTIGRAVITY to AntigravityAdapter(client)
        )
        val activeProvider = ActiveProvider(
            connections = connectionStore,
            catalog = catalog,
            keys = credentialStore
        )
        val credentials = StoredCredentials(
            store = credentialStore,
            refresher = TokenRefresher(client, clock),
            connections = connectionStore,
            clock = clock
        )
        val aiClient = AiClient(
            activeProvider = activeProvider,
            connections = connectionStore,
            credentials = credentials,
            adapters = adapters,
            usageTracker = usageTracker,
            logStore = logStore,
            clock = clock
        )
        val engine = ProviderAgentEngine(
            client = aiClient,
            contextBuilder = ContextBuilder(
                systemPrompt = context.getString(R.string.agent_system_prompt),
                screenContextTemplate = context.getString(R.string.agent_screen_context_prompt)
            ),
            copy = providerCopy(context)
        )
        val conversationStore = ConversationStore(secureStore)
        val sessionManager = ConversationSessionManager(
            store = conversationStore,
            repository = conversation,
            clock = clock
        )
        sessionManager.attach(scope)
        val chatController = ChatController(
            repository = conversation,
            engine = engine,
            errorReply = context.getString(R.string.agent_error_reply),
            logStore = logStore,
            scope = scope
        )
        sessionManager.bindCancel(chatController::cancel)
        return Container(
            chatController = chatController,
            sessionManager = sessionManager,
            connectionStore = connectionStore,
            credentialStore = credentialStore,
            catalog = catalog,
            activeProvider = activeProvider,
            tester = ConnectionTester(client, catalog, credentials, adapters, clock),
            deviceFlow = CodexDeviceFlow(client, clock),
            codeFlow = GoogleCodeFlow(client, clock),
            projectBootstrap = AntigravityProjectBootstrap(client),
            usageTracker = usageTracker,
            logStore = logStore
        )
    }

    private fun providerCopy(context: Context): ProviderCopy = ProviderCopy(
        noConnection = context.getString(R.string.agent_no_connection),
        unknownProvider = context.getString(R.string.agent_unknown_provider),
        missingCredential = context.getString(R.string.agent_missing_credential),
        unauthorized = context.getString(R.string.agent_unauthorized),
        outOfCredit = context.getString(R.string.agent_out_of_credit),
        quotaExhausted = context.getString(R.string.agent_quota_exhausted),
        rateLimited = context.getString(R.string.agent_rate_limited),
        rateLimitedWait = context.getString(R.string.agent_rate_limited_wait),
        modelUnavailable = context.getString(R.string.agent_model_unavailable),
        serverError = context.getString(R.string.agent_server_error),
        networkError = context.getString(R.string.agent_network_error),
        genericError = context.getString(R.string.agent_generic_error)
    )

    private fun seedDefaultConnection(
        context: Context,
        catalog: ProviderCatalog,
        store: ConnectionStore
    ) {
        val settings = settings(context)
        if (settings.defaultConnectionSeeded()) return
        settings.markDefaultConnectionSeeded()
        if (store.connections.value.isNotEmpty()) return
        val provider = catalog.find(ProviderCatalog.DEFAULT_PROVIDER_ID) ?: return
        val model = provider.models.firstOrNull()?.id ?: return
        val id = store.newId()
        store.upsert(
            Connection(
                id = id,
                providerId = provider.id,
                label = provider.label,
                model = model,
                health = ConnectionHealth.READY
            )
        )
        store.setActive(id)
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
