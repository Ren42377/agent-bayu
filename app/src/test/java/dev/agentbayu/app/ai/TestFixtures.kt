package dev.agentbayu.app.ai

import dev.agentbayu.app.ai.oauth.OAuthConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeClock(private var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }

    fun set(millis: Long) {
        now = millis
    }
}

class FakeConnectionSource(
    connections: List<Connection> = emptyList(),
    activeConnectionId: String? = null
) : ConnectionSource {

    private val state = MutableStateFlow(connections)
    private val active = MutableStateFlow(activeConnectionId)

    val healthCalls = ArrayList<Pair<String, ConnectionHealth>>()

    override val connections: StateFlow<List<Connection>> = state
    override val activeConnectionId: StateFlow<String?> = active

    override fun markHealth(connectionId: String, health: ConnectionHealth, detail: String?) {
        healthCalls += connectionId to health
    }

    fun setConnections(value: List<Connection>) {
        state.value = value
    }

    fun setActive(connectionId: String?) {
        active.value = connectionId
    }
}

class FakeKeys(private val keys: Map<String, String> = emptyMap()) : KeySource {
    override fun key(connectionId: String): String? = keys[connectionId]?.takeIf { it.isNotBlank() }

    override fun hasKey(connectionId: String): Boolean = key(connectionId) != null
}

fun testProvider(
    id: String = "groq",
    label: String = id,
    wireFormat: WireFormat = WireFormat.OPENAI,
    baseUrl: String = "https://api.example.test/v1",
    controlBaseUrl: String? = null,
    tier: ProviderTier = ProviderTier.API_KEY,
    authKind: AuthKind = AuthKind.API_KEY,
    optionalKey: Boolean = false,
    anonymousKey: String? = null,
    minOutputTokens: Int? = null,
    risk: RiskLevel = RiskLevel.NONE,
    authHeader: AuthHeader = AuthHeader.BEARER,
    authPrefix: String? = null,
    models: List<ModelEntry> = listOf(ModelEntry(id = "model-a")),
    supportsStreamUsage: Boolean = false,
    modelsPath: String? = null,
    modelIdFilter: String? = null,
    timeoutMillis: Long = ProviderEntry.DEFAULT_TIMEOUT_MILLIS,
    unsupportedParams: List<String> = emptyList(),
    effortMode: EffortMode = EffortMode.NONE,
    extraHeaders: Map<String, String> = emptyMap(),
    vision: Boolean = false,
    tools: Boolean = false,
    oauth: OAuthConfig? = null
): ProviderEntry = ProviderEntry(
    id = id,
    label = label,
    wireFormat = wireFormat,
    baseUrl = baseUrl,
    controlBaseUrl = controlBaseUrl,
    tier = tier,
    authKind = authKind,
    optionalKey = optionalKey,
    anonymousKey = anonymousKey,
    minOutputTokens = minOutputTokens,
    risk = risk,
    authHeader = authHeader,
    authPrefix = authPrefix,
    supportsStreamUsage = supportsStreamUsage,
    modelsPath = modelsPath,
    modelIdFilter = modelIdFilter,
    timeoutMillis = timeoutMillis,
    unsupportedParams = unsupportedParams,
    effortMode = effortMode,
    extraHeaders = extraHeaders,
    vision = vision,
    tools = tools,
    oauth = oauth,
    models = models
)

fun testConnection(
    id: String = "conn-1",
    providerId: String = "groq",
    label: String = id,
    model: String = "model-a",
    baseUrlOverride: String? = null,
    discoveredModels: List<String> = emptyList(),
    projectId: String? = null,
    effort: ReasoningEffort? = null,
    health: ConnectionHealth = ConnectionHealth.READY,
    createdAtMillis: Long = 0L
): Connection = Connection(
    id = id,
    providerId = providerId,
    label = label,
    model = model,
    baseUrlOverride = baseUrlOverride,
    discoveredModels = discoveredModels,
    projectId = projectId,
    effort = effort,
    health = health,
    createdAtMillis = createdAtMillis
)

fun testCandidate(
    connectionId: String = "conn-1",
    providerId: String = "groq",
    modelId: String = "model-a",
    upstreamModelId: String? = null,
    modelWireFormat: WireFormat? = null,
    tier: ProviderTier = ProviderTier.API_KEY,
    contextLength: Int = ModelEntry.DEFAULT_CONTEXT_LENGTH,
    maxOutputTokens: Int = ModelEntry.DEFAULT_MAX_OUTPUT_TOKENS,
    inputPrice: Double? = null,
    outputPrice: Double? = null,
    free: Boolean = false,
    baseUrl: String = "https://api.example.test/v1",
    controlBaseUrl: String? = null,
    baseUrlOverride: String? = null,
    projectId: String? = null,
    authKind: AuthKind = AuthKind.API_KEY,
    optionalKey: Boolean = false,
    anonymousKey: String? = null,
    minOutputTokens: Int? = null,
    authHeader: AuthHeader = AuthHeader.BEARER,
    authPrefix: String? = null,
    wireFormat: WireFormat = WireFormat.OPENAI,
    supportsStreamUsage: Boolean = false,
    timeoutMillis: Long = ProviderEntry.DEFAULT_TIMEOUT_MILLIS,
    providerUnsupportedParams: List<String> = emptyList(),
    modelUnsupportedParams: List<String> = emptyList(),
    extraHeaders: Map<String, String> = emptyMap(),
    vision: Boolean = false,
    providerVision: Boolean = false,
    tools: Boolean = false,
    providerTools: Boolean = false,
    oauth: OAuthConfig? = null
): Candidate {
    val model = ModelEntry(
        id = modelId,
        upstreamId = upstreamModelId,
        wireFormat = modelWireFormat,
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens,
        inputPricePerMillion = inputPrice,
        outputPricePerMillion = outputPrice,
        unsupportedParams = modelUnsupportedParams,
        free = free,
        vision = vision,
        tools = tools
    )
    return Candidate(
        connection = testConnection(
            id = connectionId,
            providerId = providerId,
            label = connectionId,
            model = modelId,
            baseUrlOverride = baseUrlOverride,
            projectId = projectId
        ),
        provider = testProvider(
            id = providerId,
            wireFormat = wireFormat,
            baseUrl = baseUrl,
            controlBaseUrl = controlBaseUrl,
            tier = tier,
            authKind = authKind,
            optionalKey = optionalKey,
            anonymousKey = anonymousKey,
            minOutputTokens = minOutputTokens,
            authHeader = authHeader,
            authPrefix = authPrefix,
            models = listOf(model),
            supportsStreamUsage = supportsStreamUsage,
            timeoutMillis = timeoutMillis,
            unsupportedParams = providerUnsupportedParams,
            extraHeaders = extraHeaders,
            vision = providerVision,
            tools = providerTools,
            oauth = oauth
        ),
        model = model
    )
}

fun usageStats(
    requests: Int = 0,
    successes: Int = 0,
    failures: Int = 0,
    inFlight: Int = 0,
    firstTokenEwmaMillis: Double = 0.0,
    lastSuccessAtMillis: Long = 0L
): UsageStats = UsageStats(
    requests = requests,
    successes = successes,
    failures = failures,
    inFlight = inFlight,
    firstTokenEwmaMillis = firstTokenEwmaMillis,
    lastSuccessAtMillis = lastSuccessAtMillis
)
