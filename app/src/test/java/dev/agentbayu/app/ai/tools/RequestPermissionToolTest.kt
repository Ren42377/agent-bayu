package dev.agentbayu.app.ai.tools

import dev.agentbayu.app.domain.tools.PermissionKind
import dev.agentbayu.app.domain.tools.PermissionRequests
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestPermissionToolTest {

    private fun call(arguments: String) =
        ToolCall(id = "call-1", name = "request_permission", arguments = arguments)

    @Test
    fun anAlreadyGrantedPermissionAnswersWithoutADialog() = runTest {
        val requests = PermissionRequests { true }
        val tool = RequestPermissionTool { requests }

        val result = tool.run(call("{\"kind\":\"storage\"}"))

        assertFalse(result.isError)
        assertTrue(result.content.contains("Already granted"))
    }

    @Test
    fun anUnknownKindIsRefused() = runTest {
        val tool = RequestPermissionTool { PermissionRequests { false } }

        val result = tool.run(call("{\"kind\":\"camera\"}"))

        assertTrue(result.isError)
        assertTrue(result.content.contains("camera"))
    }

    @Test
    fun aMissingKindIsRefused() = runTest {
        val tool = RequestPermissionTool { PermissionRequests { false } }

        assertTrue(tool.run(call("{}")).isError)
    }

    @Test
    fun aRefusalIsReportedAsAnError() = runTest {
        val requests = PermissionRequests { false }
        val tool = RequestPermissionTool { requests }

        val result = async { tool.run(call("{\"kind\":\"notifications\"}")) }
        requests.pending.filterNotNull().first()
        requests.resolve(false)

        assertTrue(result.await().isError)
    }

    @Test
    fun anOpenedSettingsPageTellsTheModelToTryAgain() = runTest {
        var allowed = false
        val requests = PermissionRequests { allowed }
        val tool = RequestPermissionTool { requests }

        val result = async { tool.run(call("{\"kind\":\"storage\"}")) }
        requests.pending.filterNotNull().first()
        requests.resolve(true)

        val answer = result.await()
        assertFalse(answer.isError)
        assertTrue(answer.content.contains("try again"))
        assertEquals(PermissionKind.STORAGE, PermissionKind.of("storage"))
        assertFalse(allowed)
    }

    @Test
    fun aGrantedPermissionAfterTheDialogIsReportedAsGranted() = runTest {
        var allowed = false
        val requests = PermissionRequests { allowed }
        val tool = RequestPermissionTool { requests }

        val result = async { tool.run(call("{\"kind\":\"notifications\"}")) }
        requests.pending.filterNotNull().first()
        allowed = true
        requests.resolve(true)

        val answer = result.await()
        assertFalse(answer.isError)
        assertTrue(answer.content.contains("Granted"))
    }
}
