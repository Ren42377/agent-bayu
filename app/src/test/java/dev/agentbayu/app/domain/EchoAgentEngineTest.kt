package dev.agentbayu.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoAgentEngineTest {

    @Test
    fun replyUsesTemplateWithoutContext() = runTest {
        val engine = EchoAgentEngine(
            replyTemplate = "Recorded: %1\$s.",
            contextReplyTemplate = "Context %2\$s from %1\$s.",
            thinkingDelayMillis = 0L
        )
        assertEquals("Recorded: hello there.", engine.reply("  hello there  ", null))
    }

    @Test
    fun replyWithContextUsesBothTemplates() = runTest {
        val engine = EchoAgentEngine(
            replyTemplate = "Recorded: %1\$s.",
            contextReplyTemplate = "From prompt %1\$s read %2\$s.",
            thinkingDelayMillis = 0L
        )
        val reply = engine.reply("summarize", "on screen text")
        assertEquals("From prompt summarize read on screen text.", reply)
    }

    @Test
    fun emptyContextFallsBackToSimpleTemplate() = runTest {
        val engine = EchoAgentEngine(
            replyTemplate = "Recorded: %1\$s.",
            contextReplyTemplate = "Should not be used.",
            thinkingDelayMillis = 0L
        )
        assertEquals("Recorded: hi.", engine.reply("hi", "   "))
    }

    @Test
    fun contextIsTruncated() = runTest {
        val engine = EchoAgentEngine(
            replyTemplate = "Recorded: %1\$s.",
            contextReplyTemplate = "%2\$s",
            thinkingDelayMillis = 0L
        )
        val longContext = "a".repeat(300)
        assertEquals(160, engine.reply("p", longContext).length)
    }

    @Test
    fun thinkingDelayDoesNotChangeReply() = runTest {
        val engine = EchoAgentEngine(
            replyTemplate = "Recorded: %1\$s.",
            contextReplyTemplate = "unused",
            thinkingDelayMillis = 1_000_000L
        )
        assertEquals("Recorded: p.", engine.reply("p", null))
    }
}
