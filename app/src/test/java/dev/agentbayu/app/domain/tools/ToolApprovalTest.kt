package dev.agentbayu.app.domain.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolApprovalTest {

    @Test
    fun sessionGrantCoversOnlyTheSamePathAgain() = runTest {
        val gate = UiToolApprovalGate()
        val first = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        gate.resolve(ToolApprovalDecision.ALLOW_SESSION)
        assertEquals(ToolApprovalDecision.ALLOW_SESSION, first.await())

        val repeat = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        assertEquals(ToolApprovalDecision.ALLOW_SESSION, repeat.await())
        assertNull(gate.pending.value)

        val other = async { gate.confirm(requestFor("edit_file", "notes/two.txt")) }
        assertEquals("notes/two.txt", gate.pending.filterNotNull().first().path)
        gate.resolve(ToolApprovalDecision.DENY)
        assertEquals(ToolApprovalDecision.DENY, other.await())
    }

    @Test
    fun sessionGrantIsScopedToOneTool() = runTest {
        val gate = UiToolApprovalGate()
        val first = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        gate.resolve(ToolApprovalDecision.ALLOW_SESSION)
        first.await()

        val other = async { gate.confirm(requestFor("delete_file", "notes/one.txt")) }
        assertEquals("delete_file", gate.pending.filterNotNull().first().toolName)
        gate.resolve(ToolApprovalDecision.DENY)
        assertEquals(ToolApprovalDecision.DENY, other.await())
    }

    @Test
    fun clearSessionForgetsGrants() = runTest {
        val gate = UiToolApprovalGate()
        val first = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        gate.resolve(ToolApprovalDecision.ALLOW_SESSION)
        first.await()
        gate.clearSession()

        val again = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        gate.resolve(ToolApprovalDecision.ALLOW_ONCE)
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, again.await())
    }

    @Test
    fun autoModeRunsWithoutAskingWhenTheJudgeApproves() = runTest {
        val notes = ToolApprovalNotes()
        val gate = JudgingToolApprovalGate(
            judge = FixedJudge(JudgeOutcome.ACCEPTED),
            fallback = DenyingGate(),
            notes = notes
        )
        assertEquals(
            ToolApprovalDecision.ALLOW_ONCE,
            gate.confirm(requestFor("edit_file", "notes/one.txt", "menyimpan catatan"))
        )
        assertEquals("menyimpan catatan", notes.take())
        assertEquals("", notes.take())
    }

    @Test
    fun autoModeDeniesWhenTheJudgeRejects() = runTest {
        val fallback = DenyingGate()
        val gate = JudgingToolApprovalGate(
            judge = FixedJudge(JudgeOutcome.REJECTED),
            fallback = fallback
        )
        assertEquals(
            ToolApprovalDecision.DENY,
            gate.confirm(requestFor("delete_file", "notes/one.txt"))
        )
        assertEquals(0, fallback.calls)
    }

    @Test
    fun autoModeFallsBackWhenTheJudgeIsUnusable() = runTest {
        val fallback = DenyingGate()
        val unsure = JudgingToolApprovalGate(
            judge = FixedJudge(JudgeOutcome.UNUSABLE),
            fallback = fallback
        )
        assertEquals(
            ToolApprovalDecision.DENY,
            unsure.confirm(requestFor("edit_file", "notes/one.txt"))
        )
        assertEquals(1, fallback.calls)
    }

    @Test
    fun autoModeWithoutAReasonStillNamesTheAction() = runTest {
        val notes = ToolApprovalNotes()
        val gate = JudgingToolApprovalGate(
            judge = FixedJudge(JudgeOutcome.ACCEPTED),
            fallback = DenyingGate(),
            notes = notes
        )
        gate.confirm(requestFor("write_file", "notes/one.txt"))
        assertEquals("write_file notes/one.txt", notes.take())
    }

    @Test
    fun autoModeFallsBackWhenTheJudgeNeverAnswers() = runTest {
        val fallback = DenyingGate()
        val gate = JudgingToolApprovalGate(
            judge = object : ToolApprovalJudge {
                override suspend fun review(
                    request: ToolApprovalRequest,
                    userIntent: String
                ): JudgeOutcome {
                    awaitCancellation()
                }
            },
            fallback = fallback,
            timeoutMillis = 5_000L
        )

        assertEquals(
            ToolApprovalDecision.DENY,
            gate.confirm(requestFor("edit_file", "notes/one.txt"))
        )
        assertEquals(1, fallback.calls)
    }

    @Test
    fun routerPicksTheGateForTheStoredMode() = runTest {
        var mode = ToolApprovalMode.BYPASS
        val ask = DenyingGate()
        val auto = JudgingToolApprovalGate(
            judge = FixedJudge(JudgeOutcome.ACCEPTED),
            fallback = ask
        )
        val router = ToolApprovalRouter(mode = { mode }, ask = ask, auto = auto)
        val request = requestFor("edit_file", "notes/one.txt")

        assertEquals(ToolApprovalDecision.ALLOW_ONCE, router.confirm(request))
        assertEquals(0, ask.calls)

        mode = ToolApprovalMode.AUTO
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, router.confirm(request))
        assertEquals(0, ask.calls)

        mode = ToolApprovalMode.ASK
        assertEquals(ToolApprovalDecision.DENY, router.confirm(request))
        assertEquals(1, ask.calls)
    }

    @Test
    fun theJudgeSeesTheOwnerIntent() = runTest {
        val judge = FixedJudge(JudgeOutcome.ACCEPTED)
        val gate = JudgingToolApprovalGate(
            judge = judge,
            fallback = DenyingGate(),
            userIntent = { "rename my notes" }
        )
        gate.confirm(requestFor("move_file", "notes/one.txt"))
        assertEquals("rename my notes", judge.seenIntent)
    }

    @Test
    fun aPendingSheetCanBeReleasedFromOutside() = runTest {
        val gate = UiToolApprovalGate()
        val waiting = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        gate.releaseIfOpen(ToolApprovalDecision.ALLOW_ONCE)
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, waiting.await())
        assertNull(gate.pending.value)
    }

    private fun requestFor(
        toolName: String,
        path: String,
        reason: String = ""
    ) = ToolApprovalRequest(
        id = 1L,
        toolName = toolName,
        kind = ToolApprovalKind.EDIT,
        path = path,
        reason = reason
    )

    private class FixedJudge(private val outcome: JudgeOutcome) : ToolApprovalJudge {
        var seenIntent: String? = null

        override suspend fun review(
            request: ToolApprovalRequest,
            userIntent: String
        ): JudgeOutcome {
            seenIntent = userIntent
            return outcome
        }
    }

    private class DenyingGate : ToolApprovalGate {
        var calls = 0

        override suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision {
            calls += 1
            return ToolApprovalDecision.DENY
        }
    }
}
