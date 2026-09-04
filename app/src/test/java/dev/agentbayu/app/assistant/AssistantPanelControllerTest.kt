package dev.agentbayu.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPanelControllerTest {

    @Test
    fun startsHidden() {
        val controller = AssistantPanelController()
        assertFalse(controller.visible.value)
        assertEquals("", controller.input.value)
    }

    @Test
    fun showRevealsPanelWithCleanInput() {
        val controller = AssistantPanelController()
        controller.updateInput("stale")
        controller.show()
        assertTrue(controller.visible.value)
        assertEquals("", controller.input.value)
    }

    @Test
    fun requestHideKeepsDraftInput() {
        val controller = AssistantPanelController()
        controller.show()
        controller.updateInput("draft")
        controller.requestHide()
        assertFalse(controller.visible.value)
        assertEquals("draft", controller.input.value)
    }

    @Test
    fun resetClearsState() {
        val controller = AssistantPanelController()
        controller.show()
        controller.updateInput("draft")
        controller.reset()
        assertFalse(controller.visible.value)
        assertEquals("", controller.input.value)
    }

    @Test
    fun takeInputReturnsTrimmedTextAndClearsField() {
        val controller = AssistantPanelController()
        controller.updateInput("  write a note  ")
        assertEquals("write a note", controller.takeInput())
        assertEquals("", controller.input.value)
    }

    @Test
    fun takeInputWithBlankValueReturnsEmptyAndKeepsField() {
        val controller = AssistantPanelController()
        controller.updateInput("   ")
        assertEquals("", controller.takeInput())
        assertEquals("   ", controller.input.value)
    }
}
