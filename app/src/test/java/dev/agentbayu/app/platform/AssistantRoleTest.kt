package dev.agentbayu.app.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoleTest {

    private val packageName = "dev.agentbayu.app"

    @Test
    fun nullComponentIsNotOwned() {
        assertFalse(AssistantRole.isOwnedByPackage(null, packageName))
    }

    @Test
    fun emptyComponentIsNotOwned() {
        assertFalse(AssistantRole.isOwnedByPackage("", packageName))
        assertFalse(AssistantRole.isOwnedByPackage("   ", packageName))
        assertFalse(AssistantRole.isOwnedByPackage("dev.agentbayu.app/BayuService", ""))
    }

    @Test
    fun flattenedComponentWithMatchingPackageIsOwned() {
        val value = "dev.agentbayu.app/dev.agentbayu.app.assistant.BayuVoiceInteractionService"
        assertTrue(AssistantRole.isOwnedByPackage(value, packageName))
    }

    @Test
    fun barePackageNameIsOwned() {
        assertTrue(AssistantRole.isOwnedByPackage(packageName, packageName))
    }

    @Test
    fun whitespaceAroundValueIsIgnored() {
        val value = " dev.agentbayu.app/dev.agentbayu.app.assistant.BayuVoiceInteractionService "
        assertTrue(AssistantRole.isOwnedByPackage(value, packageName))
    }

    @Test
    fun otherPackageIsNotOwned() {
        val value = "com.other.app/com.other.app.Service"
        assertFalse(AssistantRole.isOwnedByPackage(value, packageName))
    }

    @Test
    fun prefixPackageIsNotOwned() {
        val value = "dev.agentbayu.app.another/SomeService"
        assertFalse(AssistantRole.isOwnedByPackage(value, packageName))
    }
}
