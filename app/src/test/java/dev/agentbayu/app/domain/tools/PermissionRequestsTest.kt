package dev.agentbayu.app.domain.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequestsTest {

    @Test
    fun anAlreadyGrantedPermissionNeverAsks() = runTest {
        val requests = PermissionRequests { true }

        assertTrue(requests.request(PermissionKind.STORAGE))
        assertNull(requests.pending.value)
    }

    @Test
    fun aMissingPermissionWaitsForTheOwner() = runTest {
        val requests = PermissionRequests { false }

        val answer = async { requests.request(PermissionKind.NOTIFICATIONS) }
        val ask = requests.pending.filterNotNull().first()
        assertEquals(PermissionKind.NOTIFICATIONS, ask.kind)

        requests.resolve(true)

        assertTrue(answer.await())
        assertNull(requests.pending.value)
    }

    @Test
    fun aRefusalComesBackAsFalse() = runTest {
        val requests = PermissionRequests { false }

        val answer = async { requests.request(PermissionKind.EXACT_ALARMS) }
        requests.pending.filterNotNull().first()
        requests.resolve(false)

        assertFalse(answer.await())
    }

    @Test
    fun theWireNamesMapBothWays() {
        assertEquals(PermissionKind.STORAGE, PermissionKind.of("storage"))
        assertEquals(PermissionKind.EXACT_ALARMS, PermissionKind.of("Exact Alarms"))
        assertEquals(PermissionKind.EXACT_ALARMS, PermissionKind.of("exact-alarms"))
        assertNull(PermissionKind.of("camera"))
    }
}
