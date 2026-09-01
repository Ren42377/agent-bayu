package dev.agentbayu.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CandidateEffortTest {

    private val suffixProvider = testProvider(
        id = "agy",
        effortMode = EffortMode.MODEL_SUFFIX,
        models = listOf(
            ModelEntry(id = "flash-low"),
            ModelEntry(id = "flash-medium"),
            ModelEntry(id = "flash-high")
        )
    )

    private fun suffixCandidate(
        modelId: String,
        effort: ReasoningEffort? = null,
        discoveredModels: List<String> = emptyList()
    ) = Candidate(
        connection = testConnection(
            providerId = suffixProvider.id,
            model = modelId,
            discoveredModels = discoveredModels,
            effort = effort
        ),
        provider = suffixProvider,
        model = suffixProvider.modelOrFallback(modelId)
    )

    @Test
    fun `suffix mode exposes the family and rewrites the model id`() {
        val candidate = suffixCandidate(modelId = "flash-high", effort = ReasoningEffort.LOW)

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH),
            candidate.efforts
        )
        assertEquals(ReasoningEffort.LOW, candidate.effort)
        assertEquals("flash-low", candidate.withEffortModel().model.id)
    }

    @Test
    fun `a stored level outside the family clamps to the nearest sibling`() {
        val candidate = suffixCandidate(modelId = "flash-low", effort = ReasoningEffort.MAX)

        assertEquals(ReasoningEffort.HIGH, candidate.effort)
        assertEquals("flash-high", candidate.withEffortModel().model.id)
    }

    @Test
    fun `discovered models widen the family`() {
        val candidate = suffixCandidate(
            modelId = "flash-low",
            effort = ReasoningEffort.XHIGH,
            discoveredModels = listOf("flash-xhigh")
        )

        assertEquals(
            listOf(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH, ReasoningEffort.XHIGH),
            candidate.efforts
        )
        assertEquals(ReasoningEffort.XHIGH, candidate.effort)
        assertEquals("flash-xhigh", candidate.withEffortModel().model.id)
    }

    @Test
    fun `rewriting the model id is idempotent`() {
        val candidate = suffixCandidate(modelId = "flash-medium", effort = ReasoningEffort.MEDIUM)

        val once = candidate.withEffortModel()
        assertSame(candidate, once)
        assertEquals("flash-medium", once.withEffortModel().model.id)
    }

    @Test
    fun `a model without a family keeps its id and reports no level`() {
        val candidate = suffixCandidate(modelId = "flash-tiered", effort = ReasoningEffort.HIGH)

        assertEquals(emptyList<ReasoningEffort>(), candidate.efforts)
        assertNull(candidate.effort)
        assertSame(candidate, candidate.withEffortModel())
    }

    @Test
    fun `request field mode keeps the model id untouched`() {
        val provider = testProvider(
            id = "codex",
            effortMode = EffortMode.REQUEST_FIELD,
            models = listOf(
                ModelEntry(
                    id = "gpt-5.6-sol",
                    efforts = listOf(
                        ReasoningEffort.LOW,
                        ReasoningEffort.MEDIUM,
                        ReasoningEffort.HIGH,
                        ReasoningEffort.XHIGH
                    )
                )
            )
        )
        val candidate = Candidate(
            connection = testConnection(
                providerId = provider.id,
                model = "gpt-5.6-sol",
                effort = ReasoningEffort.XHIGH
            ),
            provider = provider,
            model = provider.modelOrFallback("gpt-5.6-sol")
        )

        assertEquals(ReasoningEffort.XHIGH, candidate.effort)
        assertSame(candidate, candidate.withEffortModel())
    }

    @Test
    fun `providers without an effort mode carry no level`() {
        val candidate = testCandidate()

        assertEquals(emptyList<ReasoningEffort>(), candidate.efforts)
        assertNull(candidate.effort)
    }
}
