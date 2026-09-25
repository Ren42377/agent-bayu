package dev.agentbayu.app.ai

import kotlin.math.abs

const val MIN_EFFORT_FAMILY_SIZE = 2

private val EFFORT_SUFFIXES: List<Pair<String, ReasoningEffort>> =
    ReasoningEffort.entries.map { "-" + it.wireValue to it }

fun splitEffortSuffix(modelId: String): Pair<String, ReasoningEffort>? {
    val trimmed = modelId.trim()
    if (trimmed.isEmpty()) return null
    val match = EFFORT_SUFFIXES.firstOrNull { trimmed.endsWith(it.first, ignoreCase = true) }
        ?: return null
    val base = trimmed.dropLast(match.first.length)
    if (base.isEmpty()) return null
    return base to match.second
}

fun effortBaseOf(modelId: String): String = splitEffortSuffix(modelId)?.first ?: modelId.trim()

fun effortFamilies(modelIds: List<String>): Map<String, List<ReasoningEffort>> {
    val grouped = LinkedHashMap<String, MutableSet<ReasoningEffort>>()
    modelIds.forEach { id ->
        val split = splitEffortSuffix(id) ?: return@forEach
        grouped.getOrPut(split.first) { linkedSetOf() } += split.second
    }
    return grouped
        .filterValues { it.size >= MIN_EFFORT_FAMILY_SIZE }
        .mapValues { entry -> entry.value.sortedBy { it.ordinal } }
}

fun effortsFor(modelId: String, availableIds: List<String>): List<ReasoningEffort> {
    val base = effortBaseOf(modelId)
    if (base.isEmpty()) return emptyList()
    return effortFamilies(availableIds)[base].orEmpty()
}

fun availableEfforts(
    provider: ProviderEntry,
    modelId: String,
    discoveredModels: List<String> = emptyList()
): List<ReasoningEffort> = when (provider.effortMode) {
    EffortMode.NONE -> emptyList()
    EffortMode.MODEL_SUFFIX ->
        effortsFor(modelId, provider.pickerModelIds(discoveredModels, modelId))
    EffortMode.REQUEST_FIELD -> mergeEfforts(
        provider.model(modelId)?.efforts.orEmpty(),
        effortsFor(modelId, discoveredModels)
    )
    else -> emptyList()
}

private fun mergeEfforts(
    catalog: List<ReasoningEffort>,
    discovered: List<ReasoningEffort>
): List<ReasoningEffort> = when {
    discovered.isEmpty() -> catalog
    catalog.isEmpty() -> discovered
    else -> (catalog + discovered).distinct().sortedBy { it.ordinal }
}

fun nearestEffort(target: ReasoningEffort, supported: List<ReasoningEffort>): ReasoningEffort? =
    supported.minByOrNull { abs(it.ordinal - target.ordinal) }

fun resolveEffort(
    options: List<ReasoningEffort>,
    stored: ReasoningEffort?,
    modelId: String? = null
): ReasoningEffort? {
    if (options.isEmpty()) return null
    if (stored != null) {
        return if (options.contains(stored)) stored else nearestEffort(stored, options)
    }
    val implied = modelId?.let { splitEffortSuffix(it)?.second }
    if (implied != null && options.contains(implied)) return implied
    return options.firstOrNull { it == ReasoningEffort.MEDIUM } ?: options[options.size / 2]
}

fun Candidate.withEffortModel(): Candidate {
    val level = effort ?: return this
    return when (provider.effortMode) {
        EffortMode.MODEL_SUFFIX -> withSuffixModel(level)
        EffortMode.REQUEST_FIELD -> withDiscoveredVariantModel(level)
        EffortMode.NONE -> this
    }
}

private fun Candidate.withSuffixModel(level: ReasoningEffort): Candidate {
    val base = effortBaseOf(model.id)
    if (base.isEmpty()) return this
    val target = base + "-" + level.wireValue
    if (target == model.id) return this
    return copy(model = provider.modelOrFallback(target))
}

private fun Candidate.withDiscoveredVariantModel(level: ReasoningEffort): Candidate {
    if (!model.upstreamByEffort[level.wireValue].isNullOrBlank()) return this
    val variant = model.id + "-" + level.wireValue
    if (variant == model.id) return this
    val raw = connection.discoveredModels
        .firstOrNull { it.trim().substringBefore('(').trim() == variant }
        ?.trim()
        ?: return this
    return copy(model = model.copy(id = raw))
}
