package dev.agentbayu.app.ai

class FakeClock(private var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        now += millis
    }

    fun set(millis: Long) {
        now = millis
    }
}

fun testProvider(
    id: String = "groq",
    label: String = id,
    wireFormat: WireFormat = WireFormat.OPENAI,
    baseUrl: String = "https://api.example.test/v1",
    tier: ProviderTier = ProviderTier.API_KEY,
    authType: AuthType = AuthType.API_KEY,
    authHeader: AuthHeader = AuthHeader.BEARER,
    authPrefix: String? = null,
    models: List<ModelEntry> = listOf(ModelEntry(id = "model-a")),
    supportsStreamUsage: Boolean = false,
    modelsPath: String? = null,
    timeoutMillis: Long = ProviderEntry.DEFAULT_TIMEOUT_MILLIS,
    unsupportedParams: List<String> = emptyList(),
    extraHeaders: Map<String, String> = emptyMap()
): ProviderEntry = ProviderEntry(
    id = id,
    label = label,
    wireFormat = wireFormat,
    baseUrl = baseUrl,
    tier = tier,
    authType = authType,
    authHeader = authHeader,
    authPrefix = authPrefix,
    supportsStreamUsage = supportsStreamUsage,
    modelsPath = modelsPath,
    timeoutMillis = timeoutMillis,
    unsupportedParams = unsupportedParams,
    extraHeaders = extraHeaders,
    models = models
)

fun testConnection(
    id: String = "conn-1",
    providerId: String = "groq",
    label: String = id,
    model: String = "model-a",
    priority: Int = Connection.DEFAULT_PRIORITY,
    weight: Int = Connection.DEFAULT_WEIGHT,
    enabled: Boolean = true,
    baseUrlOverride: String? = null
): Connection = Connection(
    id = id,
    providerId = providerId,
    label = label,
    model = model,
    enabled = enabled,
    priority = priority,
    weight = weight,
    baseUrlOverride = baseUrlOverride
)

fun testCandidate(
    connectionId: String = "conn-1",
    providerId: String = "groq",
    modelId: String = "model-a",
    priority: Int = Connection.DEFAULT_PRIORITY,
    weight: Int = Connection.DEFAULT_WEIGHT,
    tier: ProviderTier = ProviderTier.API_KEY,
    contextLength: Int = ModelEntry.DEFAULT_CONTEXT_LENGTH,
    maxOutputTokens: Int = ModelEntry.DEFAULT_MAX_OUTPUT_TOKENS,
    inputPrice: Double? = null,
    outputPrice: Double? = null,
    free: Boolean = false,
    baseUrl: String = "https://api.example.test/v1",
    baseUrlOverride: String? = null,
    authType: AuthType = AuthType.API_KEY,
    authHeader: AuthHeader = AuthHeader.BEARER,
    authPrefix: String? = null,
    wireFormat: WireFormat = WireFormat.OPENAI,
    supportsStreamUsage: Boolean = false,
    timeoutMillis: Long = ProviderEntry.DEFAULT_TIMEOUT_MILLIS,
    providerUnsupportedParams: List<String> = emptyList(),
    modelUnsupportedParams: List<String> = emptyList(),
    extraHeaders: Map<String, String> = emptyMap()
): Candidate {
    val model = ModelEntry(
        id = modelId,
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens,
        inputPricePerMillion = inputPrice,
        outputPricePerMillion = outputPrice,
        unsupportedParams = modelUnsupportedParams,
        free = free
    )
    return Candidate(
        connection = testConnection(
            id = connectionId,
            providerId = providerId,
            label = connectionId,
            model = modelId,
            priority = priority,
            weight = weight,
            baseUrlOverride = baseUrlOverride
        ),
        provider = testProvider(
            id = providerId,
            wireFormat = wireFormat,
            baseUrl = baseUrl,
            tier = tier,
            authType = authType,
            authHeader = authHeader,
            authPrefix = authPrefix,
            models = listOf(model),
            supportsStreamUsage = supportsStreamUsage,
            timeoutMillis = timeoutMillis,
            unsupportedParams = providerUnsupportedParams,
            extraHeaders = extraHeaders
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
