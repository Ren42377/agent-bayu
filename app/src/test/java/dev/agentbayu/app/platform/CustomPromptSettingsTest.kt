package dev.agentbayu.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomPromptSettingsTest {

    @Test
    fun promptIsNormalizedAndRestored() {
        val storage = InMemoryStorage()
        val settings = CustomPromptSettings(storage)

        settings.setCustomPrompt("  First\r\nSecond\rThird  ")

        assertEquals("First\nSecond\nThird", settings.customPrompt.value)
        assertEquals(
            "First\nSecond\nThird",
            CustomPromptSettings(storage).customPrompt.value
        )
    }

    @Test
    fun blankPromptClearsStoredValue() {
        val storage = InMemoryStorage()
        val settings = CustomPromptSettings(storage)
        settings.setCustomPrompt("Keep this")

        settings.setCustomPrompt(" \r\n ")

        assertEquals("", settings.customPrompt.value)
        assertNull(storage.read(CustomPromptSettings.FILE_NAME))
    }

    @Test
    fun promptLengthIsBounded() {
        val storage = InMemoryStorage()
        val settings = CustomPromptSettings(storage)

        settings.setCustomPrompt("x".repeat(CustomPromptSettings.MAX_PROMPT_CHARS + 1))

        assertEquals(CustomPromptSettings.MAX_PROMPT_CHARS, settings.customPrompt.value.length)
    }

    @Test
    fun failedWriteDoesNotPublishAnUnsavedPrompt() {
        val storage = FailingStorage()
        val settings = CustomPromptSettings(storage)
        storage.fail = true

        runCatching { settings.setCustomPrompt("Keep this") }

        assertEquals("", settings.customPrompt.value)
        assertNull(storage.read(CustomPromptSettings.FILE_NAME))
    }

    @Test
    fun failedDeleteKeepsThePublishedPrompt() {
        val storage = FailingStorage()
        val settings = CustomPromptSettings(storage)
        settings.setCustomPrompt("Keep this")
        storage.fail = true

        runCatching { settings.setCustomPrompt("") }

        assertEquals("Keep this", settings.customPrompt.value)
        storage.fail = false
        assertEquals("Keep this", storage.read(CustomPromptSettings.FILE_NAME))
    }

    private class FailingStorage : EncryptedStorage {
        private val values = HashMap<String, String>()
        var fail = false

        override fun read(name: String): String? = values[name]

        override fun write(name: String, content: String) {
            if (fail) throw java.io.IOException("Write failed")
            values[name] = content
        }

        override fun delete(name: String) {
            if (fail) throw java.io.IOException("Delete failed")
            values.remove(name)
        }
    }
}
