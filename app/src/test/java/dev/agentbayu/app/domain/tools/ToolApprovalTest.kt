package dev.agentbayu.app.domain.tools

import kotlinx.coroutines.async
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
    fun routerPicksTheGateForTheStoredMode() = runTest {
        var mode = ToolApprovalMode.BYPASS
        val ask = DenyingGate()
        val router = ToolApprovalRouter(mode = { mode }, ask = ask)
        val request = requestFor("edit_file", "notes/one.txt")

        assertEquals(ToolApprovalDecision.ALLOW_ONCE, router.confirm(request))
        assertEquals(0, ask.calls)

        mode = ToolApprovalMode.ASK
        assertEquals(ToolApprovalDecision.DENY, router.confirm(request))
        assertEquals(1, ask.calls)
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

    @Test
    fun switchingToBypassReleasesPendingAndSkipsLaterPrompts() = runTest {
        var bypassed = false
        val gate = UiToolApprovalGate { bypassed }
        val blocker = async { gate.confirm(requestFor("edit_file", "notes/one.txt")) }
        gate.pending.filterNotNull().first()
        bypassed = true
        gate.bypassPending(ToolApprovalDecision.ALLOW_ONCE)
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, blocker.await())

        val next = async { gate.confirm(requestFor("edit_file", "notes/two.txt")) }
        assertEquals(ToolApprovalDecision.ALLOW_ONCE, next.await())
        assertNull(gate.pending.value)
    }

    private fun requestFor(toolName: String, path: String) = ToolApprovalRequest(
        id = 1L,
        toolName = toolName,
        kind = ToolApprovalKind.EDIT,
        path = path
    )

    private class DenyingGate : ToolApprovalGate {
        var calls = 0

        override suspend fun confirm(request: ToolApprovalRequest): ToolApprovalDecision {
            calls += 1
            return ToolApprovalDecision.DENY
        }
    }
}
