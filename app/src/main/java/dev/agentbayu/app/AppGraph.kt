package dev.agentbayu.app

import android.content.Context
import android.util.Log
import dev.agentbayu.app.ai.ActiveProvider
import dev.agentbayu.app.ai.AiClient
import dev.agentbayu.app.ai.AntigravityProjectResolver
import dev.agentbayu.app.ai.Clock
import dev.agentbayu.app.ai.CrashLog
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
import dev.agentbayu.app.ai.tools.CompleteTaskTool
import dev.agentbayu.app.ai.tools.CreateAlarmTool
import dev.agentbayu.app.ai.tools.CreateTaskTool
import dev.agentbayu.app.ai.tools.DeleteAlarmTool
import dev.agentbayu.app.ai.tools.DeleteTaskTool
import dev.agentbayu.app.ai.tools.DeleteFileTool
import dev.agentbayu.app.ai.tools.EditFileTool
import dev.agentbayu.app.ai.tools.ListFilesTool
import dev.agentbayu.app.ai.tools.ListTasksTool
import dev.agentbayu.app.ai.tools.MoveFileTool
import dev.agentbayu.app.ai.tools.ReadFileTool
import dev.agentbayu.app.ai.tools.RequestPermissionTool
import dev.agentbayu.app.ai.tools.SearchFilesTool
import dev.agentbayu.app.ai.tools.ToolRegistry
import dev.agentbayu.app.ai.tools.ViewImageTool
import dev.agentbayu.app.ai.tools.WebSearchTool
import dev.agentbayu.app.ai.tools.WriteFileTool
import dev.agentbayu.app.domain.Attachments
import dev.agentbayu.app.domain.ChatController
import dev.agentbayu.app.domain.ContextBuilder
import dev.agentbayu.app.domain.ConversationRepository
import dev.agentbayu.app.domain.ConversationSessionManager
import dev.agentbayu.app.domain.ConversationStore
import dev.agentbayu.app.domain.ProviderAgentEngine
import dev.agentbayu.app.domain.ProviderCopy
import dev.agentbayu.app.domain.tasks.TaskStore
import dev.agentbayu.app.domain.tools.PermissionKind
import dev.agentbayu.app.domain.tools.PermissionRequests
import dev.agentbayu.app.domain.tools.ToolApprovalDecision
import dev.agentbayu.app.domain.tools.ToolApprovalMode
import dev.agentbayu.app.domain.tools.ToolApprovalRouter
import dev.agentbayu.app.domain.tools.UiToolApprovalGate
import dev.agentbayu.app.platform.AppSettings
import dev.agentbayu.app.platform.AppTaskStorage
import dev.agentbayu.app.platform.FileStorage
import dev.agentbayu.app.platform.ImagePipeline
import dev.agentbayu.app.platform.NotificationAccess
import dev.agentbayu.app.platform.SecureStore
import dev.agentbayu.app.platform.files.AllFilesAccess
import dev.agentbayu.app.platform.files.FileAccess
import dev.agentbayu.app.platform.tasks.TaskAlarms
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val assistantReadinessState = MutableStateFlow(false)
    private val readinessState = MutableStateFlow(false)
    private val settingsLock = Any()
    private val containerLock = Any()
    private val taskHubLock = Any()
    private val warmUpLock = Any()

    val assistantReadiness: StateFlow<Boolean> = assistantReadinessState.asStateFlow()
    val readiness: StateFlow<Boolean> = readinessState.asStateFlow()

    @Volatile
    private var container: Container? = null

    @Volatile
    private var warmUpJob: Job? = null

    private var fullWarmUpRequested = false
    private var warmUpFailures = 0

    fun warmUp(context: Context) {
        requestWarmUp(context.applicationContext, full = false)
    }

    fun warmUpApp(context: Context) {
        requestWarmUp(context.applicationContext, full = true)
    }

    private fun requestWarmUp(context: Context, full: Boolean, retrying: Boolean = false) {
        if (readinessState.value || (!full && assistantReadinessState.value)) return
        synchronized(warmUpLock) {
            if (full) fullWarmUpRequested = true
            if (readinessState.value || (!full && assistantReadinessState.value)) return
            if (warmUpJob != null) return
            if (!retrying) warmUpFailures = 0
            warmUpJob = scope.launch(warmUpDispatcher) {
                var restartAllowed = false
                try {
                    if (!assistantReadinessState.value) {
                        settings(context)
                        val graph = container(context)
                        graph.credentialStore.preload()
                        graph.sessionManager.awaitReady()
                        assistantReadinessState.value = true
                    }
                    val finishFullWarmUp = synchronized(warmUpLock) {
                        fullWarmUpRequested && !readinessState.value
                    }
                    if (finishFullWarmUp) {
                        taskHub(context)
                        synchronized(warmUpLock) {
                            readinessState.value = true
                            fullWarmUpRequested = false
                        }
                    }
                    synchronized(warmUpLock) { warmUpFailures = 0 }
                    restartAllowed = true
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val retry = synchronized(warmUpLock) {
                        warmUpFailures += 1
                        warmUpFailures <= MAX_WARM_UP_RETRIES
                    }
                    readinessState.value = false
                    Log.e(TAG, "App startup failed", error)
                    restartAllowed = retry
                    if (retry) delay(WARM_UP_RETRY_DELAY_MILLIS)
                } finally {
                    val restartFull = synchronized(warmUpLock) {
                        warmUpJob = null
                        val needsWarmUp =
                            !assistantReadinessState.value ||
                                (fullWarmUpRequested && !readinessState.value)
                        if (
                            restartAllowed &&
                            needsWarmUp &&
                            warmUpFailures <= MAX_WARM_UP_RETRIES
                        ) {
                            fullWarmUpRequested
                        } else {
                            null
                        }
                    }
                    if (restartFull != null) {
                        requestWarmUp(context, full = restartFull, retrying = true)
                    }
                }
            }
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
        val logStore: LogStore,
        val attachments: Attachments,
        val approvals: UiToolApprovalGate,
        val permissions: PermissionRequests
    )

    @Volatile
    private var appSettings: AppSettings? = null

    @Volatile
    private var taskHub: TaskHub? = null

    private class TaskHub(val store: TaskStore, val alarms: TaskAlarms)

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

    fun attachments(context: Context): Attachments = container(context).attachments

    fun approvals(context: Context): UiToolApprovalGate = container(context).approvals

    fun permissions(context: Context): PermissionRequests = container(context).permissions

    fun settings(context: Context): AppSettings {
        appSettings?.let { return it }
        return synchronized(settingsLock) {
            appSettings ?: AppSettings(context.applicationContext).also { appSettings = it }
        }
    }

    fun tasks(context: Context): TaskStore = taskHub(context).store

    fun taskAlarms(context: Context): TaskAlarms = taskHub(context).alarms

    private fun taskHub(context: Context): TaskHub {
        taskHub?.let { return it }
        return synchronized(taskHubLock) {
            taskHub ?: buildTasks(context.applicationContext).also { taskHub = it }
        }
    }

    private fun buildTasks(context: Context): TaskHub {
        val store = TaskStore(
            storage = AppTaskStorage(context),
            legacyStorage = SecureStore(context),
            clock = clock
        )
        val alarms = TaskAlarms(context, clock)
        scope.launch { store.tasks.collect { alarms.sync(it) } }
        return TaskHub(store, alarms)
    }

    private fun container(context: Context): Container {
        container?.let { return it }
        return synchronized(containerLock) {
            container ?: build(context.applicationContext).also { container = it }
        }
    }

    private fun build(context: Context): Container {
        val secureStore = SecureStore(context)
        val catalog = loadCatalog(context)
        val credentialStore = CredentialStore(secureStore)
        val connectionStore = ConnectionStore(secureStore, clock)
        val usageTracker = UsageTracker(clock)
        val logStore = LogStore(clock)
        CrashLog.take(context)?.let { crash ->
            logStore.error("Crash", crash.type, crash.detail)
        }
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
        val projectBootstrap = AntigravityProjectBootstrap(client)
        val projects = AntigravityProjectResolver(
            bootstrap = projectBootstrap,
            sink = connectionStore,
            clock = clock
        )
        val aiClient = AiClient(
            activeProvider = activeProvider,
            connections = connectionStore,
            credentials = credentials,
            adapters = adapters,
            usageTracker = usageTracker,
            logStore = logStore,
            clock = clock,
            projects = projects
        )
        val pipeline = ImagePipeline(context)
        val attachments = Attachments(
            pipeline = pipeline,
            storage = FileStorage(context, ATTACHMENT_DIRECTORY),
            clock = clock
        )
        val approvals = UiToolApprovalGate {
            settings(context).toolApprovalMode.value == ToolApprovalMode.BYPASS
        }
        val gate = ToolApprovalRouter(
            mode = { settings(context).toolApprovalMode.value },
            ask = approvals
        )
        scope.launch {
            settings(context).toolApprovalMode.collect { mode ->
                approvals.clearSession()
                if (mode == ToolApprovalMode.BYPASS) {
                    approvals.bypassPending(ToolApprovalDecision.ALLOW_ONCE)
                }
            }
        }
        val files = lazy { FileAccess.of(context) }
        val permissions = PermissionRequests { kind ->
            when (kind) {
                PermissionKind.STORAGE -> AllFilesAccess.granted(context)
                PermissionKind.NOTIFICATIONS -> NotificationAccess.isAllowed(context)
                PermissionKind.EXACT_ALARMS -> taskAlarms(context).canScheduleExact()
            }
        }
        val engine = ProviderAgentEngine(
            client = aiClient,
            contextBuilder = ContextBuilder(
                systemPrompt = context.getString(R.string.agent_system_prompt),
                screenContextTemplate = context.getString(R.string.agent_screen_context_prompt),
                momentTemplate = context.getString(R.string.agent_moment_prompt),
                customPrompt = { settings(context).customPrompt.value },
                images = { list -> list.mapNotNull(attachments::image) },
                clock = clock
            ),
            copy = providerCopy(context),
            tools = ToolRegistry(
                listOf(
                    CreateTaskTool(
                        store = { tasks(context) },
                        defaultListTitle = context.getString(R.string.tasks_list_default)
                    ),
                    ListTasksTool { tasks(context) },
                    CompleteTaskTool { tasks(context) },
                    DeleteTaskTool { tasks(context) },
                    CreateAlarmTool(context),
                    DeleteAlarmTool(context),
                    WebSearchTool(client),
                    ListFilesTool { files.value },
                    ReadFileTool { files.value },
                    SearchFilesTool { files.value },
                    ViewImageTool(files = { files.value }, pipeline = { pipeline }),
                    WriteFileTool({ files.value }, gate),
                    EditFileTool({ files.value }, gate),
                    DeleteFileTool({ files.value }, gate),
                    MoveFileTool({ files.value }, gate),
                    RequestPermissionTool { permissions }
                )
            )
        )
        val conversationStore = ConversationStore(secureStore)
        val sessionManager = ConversationSessionManager(
            store = conversationStore,
            repository = conversation,
            attachments = attachments,
            clock = clock
        )
        sessionManager.attach(scope)
        scope.launch {
            sessionManager.activeSessionId.collect {
                approvals.clearSession()
                approvals.releaseIfOpen(ToolApprovalDecision.DENY)
            }
        }
        scope.launch {
            sessionManager.incognitoToken.collect {
                approvals.clearSession()
                approvals.releaseIfOpen(ToolApprovalDecision.DENY)
            }
        }
        val chatController = ChatController(
            repository = conversation,
            engine = engine,
            errorReply = context.getString(R.string.agent_error_reply),
            logStore = logStore,
            scope = scope,
            discardAttachments = { ids -> ids.forEach(attachments::discard) }
        )
        sessionManager.bindCancel(chatController::cancel)
        sessionManager.bindSwitching(chatController::setSessionSwitching)
        return Container(
            chatController = chatController,
            sessionManager = sessionManager,
            connectionStore = connectionStore,
            credentialStore = credentialStore,
            catalog = catalog,
            activeProvider = activeProvider,
            tester = ConnectionTester(client, catalog, credentials, adapters, clock, projects),
            deviceFlow = CodexDeviceFlow(client, clock),
            codeFlow = GoogleCodeFlow(client, clock),
            projectBootstrap = projectBootstrap,
            usageTracker = usageTracker,
            logStore = logStore,
            attachments = attachments,
            approvals = approvals,
            permissions = permissions
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
        setupIncomplete = context.getString(R.string.agent_setup_incomplete),
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
        val model = provider.selectableModels.firstOrNull()?.id ?: return
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
    private const val ATTACHMENT_DIRECTORY = "attachments"
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val MAX_WARM_UP_RETRIES = 1
    private const val WARM_UP_RETRY_DELAY_MILLIS = 250L
    private const val TAG = "AgentBayu"
}
